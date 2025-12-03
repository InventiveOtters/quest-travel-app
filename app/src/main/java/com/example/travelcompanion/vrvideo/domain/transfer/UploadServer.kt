package com.example.travelcompanion.vrvideo.domain.transfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Embedded HTTP server for WiFi video file uploads.
 * Uses NanoHTTPD to serve a web interface and handle multipart file uploads.
 *
 * Files are uploaded directly to MediaStore (Movies/TravelCompanion/) so they:
 * - Survive app uninstall
 * - Are visible to other apps (file managers, galleries)
 * - Are discovered by MediaStoreScanWorker automatically
 *
 * @param context Android application context (for assets and storage)
 * @param contentResolver ContentResolver for MediaStore operations
 * @param port Port to listen on (default 8080)
 * @param onFileUploaded Callback invoked when a file is successfully uploaded (receives content URI)
 * @param onUploadProgress Callback for upload progress updates (filename, progress 0-100)
 * @param onUploadStarted Callback when upload starts (filename, size)
 * @param onUploadCompleted Callback when upload completes (filename, success)
 * @param pinVerifier Function to verify PIN, returns true if PIN is valid or not required
 */
class UploadServer(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val port: Int = DEFAULT_PORT,
    private val onFileUploaded: (Uri) -> Unit = {},
    private val onUploadProgress: (String, Int) -> Unit = { _, _ -> },
    private val onUploadStarted: (String, Long) -> Unit = { _, _ -> },
    private val onUploadCompleted: (String, Boolean) -> Unit = { _, _ -> },
    private val pinVerifier: (String) -> Boolean = { true },
    private val isPinEnabled: () -> Boolean = { false }
) : NanoHTTPD(port) {

    /** Current TempFileManager for accessing uploaded temp files */
    private var currentTempFileManager: MediaStoreTempFileManager? = null

    init {
        // Use MediaStoreTempFileManager to write uploads directly to MediaStore
        // Files go to Movies/TravelCompanion/ and survive app uninstall
        // The cacheDir is used for internal NanoHTTPD buffering (getTmpBucket)
        val tempDir = context.cacheDir
        setTempFileManagerFactory(object : TempFileManagerFactory {
            override fun create(): TempFileManager {
                val uploader = MediaStoreUploader(contentResolver)
                val manager = MediaStoreTempFileManager(uploader, tempDir)
                currentTempFileManager = manager
                return manager
            }
        })
    }

    companion object {
        const val DEFAULT_PORT = 8080
        /** Fallback ports to try if DEFAULT_PORT is in use */
        val FALLBACK_PORTS = listOf(8081, 8082, 8083, 8084, 8085, 8088, 8089, 8090)
        private const val MIME_JSON = "application/json"
        private const val MIME_HTML = "text/html"
        private const val MIME_CSS = "text/css"
        private const val MIME_JS = "application/javascript"

        /**
         * Tries to create and start an UploadServer on the default port,
         * falling back to alternative ports if the default is in use.
         * @return Pair of (server, actualPort) on success, or throws exception on failure
         */
        fun createWithFallbackPorts(
            context: Context,
            contentResolver: ContentResolver,
            onFileUploaded: (Uri) -> Unit = {},
            onUploadProgress: (String, Int) -> Unit = { _, _ -> },
            onUploadStarted: (String, Long) -> Unit = { _, _ -> },
            onUploadCompleted: (String, Boolean) -> Unit = { _, _ -> },
            pinVerifier: (String) -> Boolean = { true },
            isPinEnabled: () -> Boolean = { false }
        ): Pair<UploadServer, Int> {
            val portsToTry = listOf(DEFAULT_PORT) + FALLBACK_PORTS

            for (port in portsToTry) {
                try {
                    val server = UploadServer(
                        context = context,
                        contentResolver = contentResolver,
                        port = port,
                        onFileUploaded = onFileUploaded,
                        onUploadProgress = onUploadProgress,
                        onUploadStarted = onUploadStarted,
                        onUploadCompleted = onUploadCompleted,
                        pinVerifier = pinVerifier,
                        isPinEnabled = isPinEnabled
                    )
                    server.start()
                    android.util.Log.i("UploadServer", "Server started on port $port")
                    return Pair(server, port)
                } catch (e: java.net.BindException) {
                    android.util.Log.w("UploadServer", "Port $port in use, trying next...")
                    continue
                } catch (e: Exception) {
                    if (e.cause is java.net.BindException) {
                        android.util.Log.w("UploadServer", "Port $port in use, trying next...")
                        continue
                    }
                    throw e
                }
            }
            throw java.net.BindException("All ports are in use: $portsToTry")
        }
    }

    /** Represents an uploaded file with metadata */
    data class UploadedFile(
        val name: String,
        val size: Long,
        val uploadedAt: Long = System.currentTimeMillis()
    )

    /** Represents current upload progress */
    data class UploadProgress(
        val filename: String,
        val totalSize: Long,
        val progress: Int // 0-100
    )

    private val _uploadedFiles = MutableStateFlow<List<UploadedFile>>(emptyList())
    val uploadedFiles: StateFlow<List<UploadedFile>> = _uploadedFiles.asStateFlow()

    private val _lastActivityTime = MutableStateFlow(System.currentTimeMillis())
    val lastActivityTime: StateFlow<Long> = _lastActivityTime.asStateFlow()

    private val _currentUpload = MutableStateFlow<UploadProgress?>(null)
    val currentUpload: StateFlow<UploadProgress?> = _currentUpload.asStateFlow()

    private var uploadCount = 0

    override fun serve(session: IHTTPSession): Response {
        _lastActivityTime.value = System.currentTimeMillis()

        val uri = session.uri
        val method = session.method

        return try {
            when {
                // API endpoints
                uri == "/api/status" && method == Method.GET -> handleStatus()
                uri == "/api/upload" && method == Method.POST -> handleUpload(session)
                uri == "/api/files" && method == Method.GET -> handleFilesList()
                uri == "/api/verify-pin" && method == Method.POST -> handleVerifyPin(session)

                // Static assets
                uri.startsWith("/assets/") -> serveAsset(uri.removePrefix("/assets/"))
                uri == "/" || uri == "/index.html" -> serveAsset("transfer/index.html")
                uri == "/style.css" -> serveAsset("transfer/style.css")
                uri == "/upload.js" -> serveAsset("transfer/upload.js")

                // 404
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON,
                    """{"error": "Not found"}""")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON,
                """{"error": "${e.message?.replace("\"", "'")}"}""")
        }
    }

    /** GET /api/status - Returns server status */
    private fun handleStatus(): Response {
        val availableStorage = FileValidator.getAvailableStorage(context)
        val json = JSONObject().apply {
            put("running", true)
            put("storageAvailable", availableStorage)
            put("storageAvailableFormatted", FileValidator.formatBytes(availableStorage))
            put("uploadCount", uploadCount)
            put("uptime", System.currentTimeMillis() - _lastActivityTime.value)
            put("pinRequired", isPinEnabled())
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString())
    }

    /** POST /api/verify-pin - Verifies the provided PIN */
    private fun handleVerifyPin(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (e: Exception) {
            return errorResponse("Failed to parse request")
        }

        val pin = session.parameters["pin"]?.firstOrNull() ?: files["postData"] ?: ""
        val isValid = pinVerifier(pin.trim())

        val json = JSONObject().apply {
            put("success", isValid)
            if (!isValid) {
                put("error", "Invalid PIN")
            }
        }

        return if (isValid) {
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString())
        } else {
            newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_JSON, json.toString())
        }
    }

    /** GET /api/files - Returns list of uploaded files */
    private fun handleFilesList(): Response {
        val filesArray = JSONArray()
        _uploadedFiles.value.forEach { file ->
            filesArray.put(JSONObject().apply {
                put("name", file.name)
                put("size", file.size)
                put("sizeFormatted", FileValidator.formatBytes(file.size))
                put("uploadedAt", file.uploadedAt)
            })
        }
        val json = JSONObject().apply {
            put("files", filesArray)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString())
    }

    /** POST /api/upload - Handles multipart file upload */
    private fun handleUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()

        // Check PIN if required (PIN should be sent as X-Upload-Pin header)
        if (isPinEnabled()) {
            val providedPin = session.headers["x-upload-pin"] ?: ""
            if (!pinVerifier(providedPin)) {
                return newFixedLengthResponse(
                    Response.Status.UNAUTHORIZED,
                    MIME_JSON,
                    """{"success": false, "error": "PIN required. Please enter the PIN shown on the VR headset."}"""
                )
            }
        }

        // Get expected file size from Content-Length header for progress tracking
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L

        // Try to get filename from Content-Disposition early for progress tracking
        val rawFilename = extractFilenameFromHeaders(session.headers) ?: "unknown"

        // Notify upload started
        onUploadStarted(rawFilename, contentLength)
        _currentUpload.value = UploadProgress(rawFilename, contentLength, 0)

        try {
            session.parseBody(files)
        } catch (e: Exception) {
            _currentUpload.value = null
            onUploadCompleted(rawFilename, false)
            return errorResponse("Failed to parse upload: ${e.message}")
        }

        // Get the uploaded file info - now this is a MediaStore content URI string
        val tempFileUri = files["file"] ?: run {
            _currentUpload.value = null
            onUploadCompleted(rawFilename, false)
            return errorResponse("No file provided")
        }
        val filename = session.parameters["file"]?.firstOrNull() ?: run {
            _currentUpload.value = null
            onUploadCompleted(rawFilename, false)
            return errorResponse("No filename provided")
        }

        // Validate file type by extension only
        if (!FileValidator.isValidVideoFile(filename, null)) {
            _currentUpload.value = null
            onUploadCompleted(filename, false)
            return unsupportedMediaResponse("Invalid file type. Only ${FileValidator.getSupportedExtensionsDisplay()} files are supported.")
        }

        // Find the MediaStore temp file to finalize it
        val tempFile = currentTempFileManager?.findByUri(tempFileUri)
        if (tempFile == null) {
            _currentUpload.value = null
            onUploadCompleted(filename, false)
            return errorResponse("Upload processing error: temp file not found")
        }

        // Query file size from MediaStore
        val fileSize = getMediaStoreFileSize(tempFile.uri)

        // Check storage (for safety - MediaStore should handle this)
        if (!FileValidator.hasEnoughStorage(context, fileSize)) {
            tempFile.delete() // This cancels the MediaStore entry
            _currentUpload.value = null
            onUploadCompleted(filename, false)
            return storageErrorResponse("Not enough storage space. Need ${FileValidator.formatBytes(fileSize + 500_000_000)}")
        }

        // Update progress to indicate upload received, now finalizing
        _currentUpload.value = UploadProgress(filename, fileSize, 95)
        onUploadProgress(filename, 95)

        // Finalize the MediaStore entry (set IS_PENDING=0 to make it visible)
        return try {
            val finalized = tempFile.finalize()
            if (!finalized) {
                throw Exception("Failed to finalize MediaStore entry")
            }

            // Remove from temp file tracking so clear() doesn't delete it
            currentTempFileManager?.markFinalized(tempFile)

            uploadCount++
            val uploadedFile = UploadedFile(filename, fileSize)
            _uploadedFiles.value = listOf(uploadedFile) + _uploadedFiles.value

            // Mark upload complete
            _currentUpload.value = UploadProgress(filename, fileSize, 100)
            onUploadProgress(filename, 100)
            onUploadCompleted(filename, true)
            _currentUpload.value = null

            onFileUploaded(tempFile.uri)

            val json = JSONObject().apply {
                put("success", true)
                put("filename", filename)
                put("size", fileSize)
                put("sizeFormatted", FileValidator.formatBytes(fileSize))
            }
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString())
        } catch (e: Exception) {
            tempFile.delete() // Cancel the MediaStore entry
            _currentUpload.value = null
            onUploadCompleted(filename, false)
            errorResponse("Failed to save file: ${e.message}")
        }
    }

    /**
     * Queries the file size from MediaStore for a given content URI.
     */
    private fun getMediaStoreFileSize(uri: Uri): Long {
        return try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.Video.Media.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            android.util.Log.w("UploadServer", "Failed to get file size: ${e.message}")
            0L
        }
    }

    /** Try to extract filename from Content-Disposition header */
    private fun extractFilenameFromHeaders(headers: Map<String, String>): String? {
        val contentDisposition = headers["content-disposition"] ?: return null
        val regex = """filename="?([^";\r\n]+)"?""".toRegex()
        return regex.find(contentDisposition)?.groupValues?.get(1)
    }

    /** Serves static assets from the Android assets folder */
    private fun serveAsset(path: String): Response {
        return try {
            val inputStream = context.assets.open(path)
            val mimeType = getMimeType(path)
            val response = newChunkedResponse(Response.Status.OK, mimeType, inputStream)
            // Add cache headers for static assets (1 hour for CSS/JS, no-cache for HTML)
            if (path.endsWith(".html")) {
                response.addHeader("Cache-Control", "no-cache")
            } else {
                response.addHeader("Cache-Control", "max-age=3600")
            }
            response
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON,
                """{"error": "Asset not found: $path"}""")
        }
    }

    /** Returns appropriate MIME type for a file extension */
    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") -> MIME_HTML
            path.endsWith(".css") -> MIME_CSS
            path.endsWith(".js") -> MIME_JS
            path.endsWith(".json") -> MIME_JSON
            path.endsWith(".png") -> "image/png"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".ico") -> "image/x-icon"
            else -> "application/octet-stream"
        }
    }

    /** Creates an error JSON response with appropriate HTTP status */
    private fun errorResponse(message: String, status: Response.Status = Response.Status.BAD_REQUEST): Response {
        val json = JSONObject().apply {
            put("success", false)
            put("error", message)
        }
        return newFixedLengthResponse(status, MIME_JSON, json.toString())
    }

    /** Creates an error response for storage issues (507 Insufficient Storage) */
    private fun storageErrorResponse(message: String): Response {
        return errorResponse(message, Response.Status.INTERNAL_ERROR) // 507 not available, use 500
    }

    /** Creates an error response for unsupported media type (415) */
    private fun unsupportedMediaResponse(message: String): Response {
        return errorResponse(message, Response.Status.UNSUPPORTED_MEDIA_TYPE)
    }

    /** Clears the upload history (not the files themselves) */
    fun clearUploadHistory() {
        _uploadedFiles.value = emptyList()
        uploadCount = 0
    }

    /** Gets the total number of uploads this session */
    fun getUploadCount(): Int = uploadCount
}

