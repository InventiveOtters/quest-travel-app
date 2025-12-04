package com.inotter.travelcompanion.data.managers.TransferManager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.inotter.travelcompanion.data.managers.TransferManager.models.ResumableUpload
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Embedded HTTP server for WiFi video file uploads.
 * Uses NanoHTTPD to serve a web interface and handle multipart file uploads.
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
    private val isPinEnabled: () -> Boolean = { false },
    private val getIncompleteUploads: (suspend () -> List<ResumableUpload>)? = null,
    private val onUploadSessionCreated: (suspend (filename: String, expectedSize: Long, mediaStoreUri: String, mimeType: String) -> Long)? = null,
    private val onUploadSessionProgress: (suspend (sessionId: Long, bytesReceived: Long) -> Unit)? = null,
    private val onUploadSessionCompleted: (suspend (sessionId: Long, success: Boolean) -> Unit)? = null
) : NanoHTTPD(port) {

    private var currentTempFileManager: MediaStoreTempFileManager? = null

    init {
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
        val FALLBACK_PORTS = listOf(8081, 8082, 8083, 8084, 8085, 8088, 8089, 8090)
        private const val MIME_JSON = "application/json"
        private const val MIME_HTML = "text/html"
        private const val MIME_CSS = "text/css"
        private const val MIME_JS = "application/javascript"

        fun createWithFallbackPorts(
            context: Context,
            contentResolver: ContentResolver,
            onFileUploaded: (Uri) -> Unit = {},
            onUploadProgress: (String, Int) -> Unit = { _, _ -> },
            onUploadStarted: (String, Long) -> Unit = { _, _ -> },
            onUploadCompleted: (String, Boolean) -> Unit = { _, _ -> },
            pinVerifier: (String) -> Boolean = { true },
            isPinEnabled: () -> Boolean = { false },
            getIncompleteUploads: (suspend () -> List<ResumableUpload>)? = null,
            onUploadSessionCreated: (suspend (filename: String, expectedSize: Long, mediaStoreUri: String, mimeType: String) -> Long)? = null,
            onUploadSessionProgress: (suspend (sessionId: Long, bytesReceived: Long) -> Unit)? = null,
            onUploadSessionCompleted: (suspend (sessionId: Long, success: Boolean) -> Unit)? = null
        ): Pair<UploadServer, Int> {
            val portsToTry = listOf(DEFAULT_PORT) + FALLBACK_PORTS
            for (port in portsToTry) {
                try {
                    val server = UploadServer(context, contentResolver, port, onFileUploaded, onUploadProgress,
                        onUploadStarted, onUploadCompleted, pinVerifier, isPinEnabled, getIncompleteUploads,
                        onUploadSessionCreated, onUploadSessionProgress, onUploadSessionCompleted)
                    server.start()
                    android.util.Log.i("UploadServer", "Server started on port $port")
                    return Pair(server, port)
                } catch (e: java.net.BindException) {
                    android.util.Log.w("UploadServer", "Port $port in use, trying next...")
                    continue
                } catch (e: Exception) {
                    if (e.cause is java.net.BindException) continue
                    throw e
                }
            }
            throw java.net.BindException("All ports are in use: $portsToTry")
        }
    }

    data class UploadedFile(val name: String, val size: Long, val uploadedAt: Long = System.currentTimeMillis())
    data class UploadProgress(val filename: String, val totalSize: Long, val progress: Int)

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
                uri == "/api/status" && method == Method.GET -> handleStatus()
                uri == "/api/upload" && method == Method.POST -> handleUpload(session)
                uri == "/api/upload-resume" && method == Method.POST -> handleResumeUpload(session)
                uri == "/api/files" && method == Method.GET -> handleFilesList()
                uri == "/api/incomplete-uploads" && method == Method.GET -> handleIncompleteUploads()
                uri == "/api/verify-pin" && method == Method.POST -> handleVerifyPin(session)
                uri.startsWith("/assets/") -> serveAsset(uri.removePrefix("/assets/"))
                uri == "/" || uri == "/index.html" -> serveAsset("transfer/index.html")
                uri == "/style.css" -> serveAsset("transfer/style.css")
                uri == "/upload.js" -> serveAsset("transfer/upload.js")
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, """{"error": "Not found"}""")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, """{"error": "${e.message?.replace("\"", "'")}"}""")
        }
    }

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

    private fun handleVerifyPin(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        try { session.parseBody(files) } catch (_: Exception) { return errorResponse("Failed to parse request") }
        val pin = session.parameters["pin"]?.firstOrNull() ?: files["postData"] ?: ""
        val isValid = pinVerifier(pin.trim())
        val json = JSONObject().apply {
            put("success", isValid)
            if (!isValid) put("error", "Invalid PIN")
        }
        return if (isValid) newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString())
        else newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_JSON, json.toString())
    }

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
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, JSONObject().apply { put("files", filesArray) }.toString())
    }

    private fun handleIncompleteUploads(): Response {
        val provider = getIncompleteUploads ?: return newFixedLengthResponse(Response.Status.OK, MIME_JSON,
            JSONObject().apply { put("uploads", JSONArray()); put("resumeSupported", false) }.toString())
        return try {
            val uploads = runBlocking { provider() }
            val uploadsArray = JSONArray()
            uploads.forEach { upload ->
                uploadsArray.put(JSONObject().apply {
                    put("sessionId", upload.sessionId)
                    put("filename", upload.filename)
                    put("expectedSize", upload.expectedSize)
                    put("expectedSizeFormatted", FileValidator.formatBytes(upload.expectedSize))
                    put("bytesReceived", upload.bytesReceived)
                    put("bytesReceivedFormatted", FileValidator.formatBytes(upload.bytesReceived))
                    put("progressPercent", upload.progressPercent)
                    put("mediaStoreUri", upload.mediaStoreUri)
                })
            }
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, JSONObject().apply { put("uploads", uploadsArray); put("resumeSupported", true) }.toString())
        } catch (e: Exception) {
            errorResponse("Failed to get incomplete uploads: ${e.message}")
        }
    }

    private fun handleResumeUpload(session: IHTTPSession): Response {
        if (isPinEnabled() && !pinVerifier(session.headers["x-upload-pin"] ?: "")) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_JSON,
                """{"success": false, "error": "PIN required. Please enter the PIN shown on the VR headset."}""")
        }
        val sessionId = session.parameters["sessionId"]?.firstOrNull()?.toLongOrNull() ?: session.headers["x-session-id"]?.toLongOrNull()
            ?: return errorResponse("Missing sessionId parameter")
        val contentRange = session.headers["content-range"] ?: return errorResponse("Missing Content-Range header for resume")
        val rangeMatch = Regex("""bytes (\d+)-(\d+)/(\d+)""").find(contentRange) ?: return errorResponse("Invalid Content-Range format")
        val rangeEnd = rangeMatch.groupValues[2].toLong()
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON,
            JSONObject().apply { put("success", true); put("sessionId", sessionId); put("bytesReceived", rangeEnd + 1); put("message", "Resume upload endpoint ready") }.toString())
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        var sessionId: Long? = null

        if (isPinEnabled() && !pinVerifier(session.headers["x-upload-pin"] ?: "")) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_JSON,
                """{"success": false, "error": "PIN required. Please enter the PIN shown on the VR headset."}""")
        }

        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
        val rawFilename = extractFilenameFromHeaders(session.headers) ?: "unknown"
        onUploadStarted(rawFilename, contentLength)
        _currentUpload.value = UploadProgress(rawFilename, contentLength, 0)

        try { session.parseBody(files) } catch (e: Exception) {
            _currentUpload.value = null
            onUploadCompleted(rawFilename, false)
            return errorResponse("Failed to parse upload: ${e.message}")
        }

        val tempFileUri = files["file"] ?: run {
            _currentUpload.value = null; onUploadCompleted(rawFilename, false)
            return errorResponse("No file provided")
        }
        val filename = session.parameters["file"]?.firstOrNull() ?: run {
            _currentUpload.value = null; onUploadCompleted(rawFilename, false)
            return errorResponse("No filename provided")
        }

        if (!FileValidator.isValidVideoFile(filename, null)) {
            _currentUpload.value = null; onUploadCompleted(filename, false)
            return unsupportedMediaResponse("Invalid file type. Only ${FileValidator.getSupportedExtensionsDisplay()} files are supported.")
        }

        val tempFile = currentTempFileManager?.findByUri(tempFileUri) ?: run {
            _currentUpload.value = null; onUploadCompleted(filename, false)
            return errorResponse("Upload processing error: temp file not found")
        }

        val fileSize = getMediaStoreFileSize(tempFile.uri)

        if (onUploadSessionCreated != null) {
            val mimeType = getVideoMimeType(filename)
            sessionId = runBlocking { onUploadSessionCreated.invoke(filename, contentLength, tempFile.uri.toString(), mimeType) }
            if (sessionId != null && onUploadSessionProgress != null) {
                runBlocking { onUploadSessionProgress.invoke(sessionId, fileSize) }
            }
        }

        if (!FileValidator.hasEnoughStorage(context, fileSize)) {
            tempFile.delete(); _currentUpload.value = null; onUploadCompleted(filename, false)
            if (sessionId != null && onUploadSessionCompleted != null) runBlocking { onUploadSessionCompleted.invoke(sessionId, false) }
            return storageErrorResponse("Not enough storage space. Need ${FileValidator.formatBytes(fileSize + 500_000_000)}")
        }

        _currentUpload.value = UploadProgress(filename, fileSize, 95)
        onUploadProgress(filename, 95)

        return try {
            if (!tempFile.finalize()) throw Exception("Failed to finalize MediaStore entry")
            currentTempFileManager?.markFinalized(tempFile)
            uploadCount++
            _uploadedFiles.value = listOf(UploadedFile(filename, fileSize)) + _uploadedFiles.value
            _currentUpload.value = UploadProgress(filename, fileSize, 100)
            onUploadProgress(filename, 100); onUploadCompleted(filename, true); _currentUpload.value = null
            if (sessionId != null && onUploadSessionCompleted != null) runBlocking { onUploadSessionCompleted.invoke(sessionId, true) }
            onFileUploaded(tempFile.uri)
            newFixedLengthResponse(Response.Status.OK, MIME_JSON,
                JSONObject().apply { put("success", true); put("filename", filename); put("size", fileSize); put("sizeFormatted", FileValidator.formatBytes(fileSize)) }.toString())
        } catch (e: Exception) {
            tempFile.delete(); _currentUpload.value = null; onUploadCompleted(filename, false)
            if (sessionId != null && onUploadSessionCompleted != null) runBlocking { onUploadSessionCompleted.invoke(sessionId, false) }
            errorResponse("Failed to save file: ${e.message}")
        }
    }

    private fun getVideoMimeType(filename: String): String {
        return when (filename.substringAfterLast('.', "").lowercase()) {
            "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"
            "mov" -> "video/quicktime"; "avi" -> "video/x-msvideo"; "m4v" -> "video/x-m4v"; "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }
    }

    private fun getMediaStoreFileSize(uri: Uri): Long {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.MediaStore.Video.Media.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun extractFilenameFromHeaders(headers: Map<String, String>): String? {
        val contentDisposition = headers["content-disposition"] ?: return null
        return """filename="?([^";\r\n]+)"?""".toRegex().find(contentDisposition)?.groupValues?.get(1)
    }

    private fun serveAsset(path: String): Response {
        return try {
            val inputStream = context.assets.open(path)
            val response = newChunkedResponse(Response.Status.OK, getMimeType(path), inputStream)
            response.addHeader("Cache-Control", if (path.endsWith(".html")) "no-cache" else "max-age=3600")
            response
        } catch (_: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, """{"error": "Asset not found: $path"}""")
        }
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") -> MIME_HTML; path.endsWith(".css") -> MIME_CSS
            path.endsWith(".js") -> MIME_JS; path.endsWith(".json") -> MIME_JSON
            path.endsWith(".png") -> "image/png"; path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".ico") -> "image/x-icon"; else -> "application/octet-stream"
        }
    }

    private fun errorResponse(message: String, status: Response.Status = Response.Status.BAD_REQUEST): Response {
        return newFixedLengthResponse(status, MIME_JSON, JSONObject().apply { put("success", false); put("error", message) }.toString())
    }

    private fun storageErrorResponse(message: String) = errorResponse(message, Response.Status.INTERNAL_ERROR)
    private fun unsupportedMediaResponse(message: String) = errorResponse(message, Response.Status.UNSUPPORTED_MEDIA_TYPE)

    fun clearUploadHistory() { _uploadedFiles.value = emptyList(); uploadCount = 0 }
    fun getUploadCount(): Int = uploadCount
}

