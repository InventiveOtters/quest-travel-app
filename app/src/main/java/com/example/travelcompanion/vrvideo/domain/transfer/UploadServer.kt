package com.example.travelcompanion.vrvideo.domain.transfer

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Embedded HTTP server for WiFi video file uploads.
 * Uses NanoHTTPD to serve a web interface and handle multipart file uploads.
 *
 * @param context Android application context (for assets and storage)
 * @param port Port to listen on (default 8080)
 * @param uploadDir Directory to save uploaded files
 * @param onFileUploaded Callback invoked when a file is successfully uploaded
 */
class UploadServer(
    private val context: Context,
    private val port: Int = DEFAULT_PORT,
    private val uploadDir: File,
    private val onFileUploaded: (File) -> Unit = {}
) : NanoHTTPD(port) {

    companion object {
        const val DEFAULT_PORT = 8080
        private const val MIME_JSON = "application/json"
        private const val MIME_HTML = "text/html"
        private const val MIME_CSS = "text/css"
        private const val MIME_JS = "application/javascript"
    }

    /** Represents an uploaded file with metadata */
    data class UploadedFile(
        val name: String,
        val size: Long,
        val uploadedAt: Long = System.currentTimeMillis()
    )

    private val _uploadedFiles = MutableStateFlow<List<UploadedFile>>(emptyList())
    val uploadedFiles: StateFlow<List<UploadedFile>> = _uploadedFiles.asStateFlow()

    private val _lastActivityTime = MutableStateFlow(System.currentTimeMillis())
    val lastActivityTime: StateFlow<Long> = _lastActivityTime.asStateFlow()

    private var uploadCount = 0

    init {
        // Ensure upload directory exists
        if (!uploadDir.exists()) {
            uploadDir.mkdirs()
        }
    }

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
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString())
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
        val params = HashMap<String, String>()

        try {
            session.parseBody(files)
        } catch (e: Exception) {
            return errorResponse("Failed to parse upload: ${e.message}")
        }

        // Get the uploaded file info
        val tempFilePath = files["file"] ?: return errorResponse("No file provided")
        val filename = session.parameters["file"]?.firstOrNull()
            ?: return errorResponse("No filename provided")

        // Validate file type
        val contentType = session.headers["content-type"]
        if (!FileValidator.isValidVideoFile(filename, contentType)) {
            // Clean up temp file
            File(tempFilePath).delete()
            return errorResponse("Invalid file type. Only ${FileValidator.getSupportedExtensionsDisplay()} files are supported.")
        }

        val tempFile = File(tempFilePath)
        val fileSize = tempFile.length()

        // Check storage
        if (!FileValidator.hasEnoughStorage(context, fileSize)) {
            tempFile.delete()
            return errorResponse("Not enough storage space. Need ${FileValidator.formatBytes(fileSize + 500_000_000)}")
        }

        // Move file to upload directory
        val destFile = getUniqueFile(uploadDir, filename)
        return try {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()

            uploadCount++
            val uploadedFile = UploadedFile(destFile.name, fileSize)
            _uploadedFiles.value = listOf(uploadedFile) + _uploadedFiles.value

            onFileUploaded(destFile)

            val json = JSONObject().apply {
                put("success", true)
                put("filename", destFile.name)
                put("size", fileSize)
                put("sizeFormatted", FileValidator.formatBytes(fileSize))
            }
            newFixedLengthResponse(Response.Status.OK, MIME_JSON, json.toString())
        } catch (e: Exception) {
            tempFile.delete()
            destFile.delete()
            errorResponse("Failed to save file: ${e.message}")
        }
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

    /** Creates an error JSON response */
    private fun errorResponse(message: String): Response {
        val json = JSONObject().apply {
            put("success", false)
            put("error", message)
        }
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_JSON, json.toString())
    }

    /**
     * Gets a unique filename in the target directory.
     * If a file with the same name exists, appends a number.
     */
    private fun getUniqueFile(directory: File, filename: String): File {
        var destFile = File(directory, filename)
        if (!destFile.exists()) return destFile

        val nameWithoutExt = filename.substringBeforeLast('.')
        val extension = filename.substringAfterLast('.', "")
        var counter = 1

        while (destFile.exists()) {
            val newName = if (extension.isNotEmpty()) {
                "${nameWithoutExt}_$counter.$extension"
            } else {
                "${nameWithoutExt}_$counter"
            }
            destFile = File(directory, newName)
            counter++
        }

        return destFile
    }

    /** Clears the upload history (not the files themselves) */
    fun clearUploadHistory() {
        _uploadedFiles.value = emptyList()
        uploadCount = 0
    }

    /** Gets the upload directory */
    fun getUploadDirectory(): File = uploadDir

    /** Gets the total number of uploads this session */
    fun getUploadCount(): Int = uploadCount
}

