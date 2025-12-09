package com.inotter.onthegovr.data.managers.TransferManager

import me.desair.tus.server.TusFileUploadService
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Servlet wrapper for TUS file upload service.
 *
 * This thin wrapper adds PIN verification before delegating to tus-java-server.
 * All TUS protocol logic is handled by the library; we only:
 * 1. Check PIN for non-OPTIONS requests (if PIN protection is enabled)
 * 2. Delegate to TusFileUploadService for actual TUS handling
 * 3. Check for completed uploads after PATCH requests
 *
 * Handles endpoints:
 * - OPTIONS /tus/ - Capability discovery
 * - POST /tus/ - Create new upload
 * - HEAD /tus/{id} - Get upload offset (resume)
 * - PATCH /tus/{id} - Upload chunk
 * - DELETE /tus/{id} - Cancel upload
 */
class TusUploadServlet(
    private val tusService: TusFileUploadService,
    private val uploadHandler: TusUploadHandler? = null,
    private val pinVerifier: (String) -> Boolean = { true },
    private val isPinEnabled: () -> Boolean = { false },
    private val tusDataDir: java.io.File? = null
) : HttpServlet() {

    companion object {
        private const val TAG = "TusUploadServlet"
        private const val PIN_HEADER = "X-Upload-Pin"
    }

    /**
     * Handle all HTTP methods by delegating to TUS service.
     * PIN check is performed for non-OPTIONS requests when enabled.
     */
    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
        android.util.Log.d(TAG, "TUS request: ${req.method} ${req.requestURI} (servletPath=${req.servletPath}, pathInfo=${req.pathInfo})")

        // OPTIONS requests don't require PIN (capability discovery)
        if (req.method != "OPTIONS" && isPinEnabled()) {
            val pin = req.getHeader(PIN_HEADER) ?: ""
            if (!pinVerifier(pin)) {
                android.util.Log.w(TAG, "PIN verification failed for ${req.method} ${req.requestURI}")
                resp.status = HttpServletResponse.SC_UNAUTHORIZED
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "PIN required. Please enter the PIN shown on the VR headset."}""")
                return
            }
        }

        try {
            // Extract upload ID from path for DELETE handling
            val uploadId = extractUploadId(req.pathInfo)

            // Log directory state BEFORE DELETE to track what's being deleted
            if (req.method == "DELETE") {
                android.util.Log.i(TAG, "DELETE request starting for ${req.requestURI} (uploadId: $uploadId)")
                logTusDirectoryContents("BEFORE DELETE")
            }

            // Delegate to TUS service - it handles all protocol logic
            tusService.process(req, resp)
            android.util.Log.d(TAG, "TUS request completed: ${req.method} ${req.requestURI} -> ${resp.status}")

            // After PATCH request, check if upload is complete
            if (req.method == "PATCH" && resp.status in 200..299) {
                checkUploadCompletion(req.requestURI)
            }

            // Handle DELETE: manually clean up files since tus-java-server doesn't seem to delete them
            // Note: TUS protocol should return 204 No Content for successful DELETE, but we've seen 200 too
            if (req.method == "DELETE" && uploadId != null) {
                android.util.Log.i(TAG, "DELETE request for ${req.requestURI} completed with status ${resp.status}")

                // Try to understand why the library might not be deleting:
                // Check if the upload info exists (library needs this to delete)
                try {
                    val uploadInfo = tusService.getUploadInfo(req.requestURI, null)
                    android.util.Log.d(TAG, "Upload info after library DELETE: ${uploadInfo?.id} (null means library deleted it or it never existed)")
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Could not get upload info after DELETE: ${e.message}")
                }

                // Manually delete the upload files regardless of status code
                val deletedCount = manuallyDeleteUploadFiles(uploadId)
                android.util.Log.i(TAG, "Manually deleted $deletedCount files for upload $uploadId")

                // Log remaining files in TUS directory for debugging
                logTusDirectoryContents("AFTER DELETE + MANUAL CLEANUP")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "TUS processing error: ${e.message}", e)
            if (!resp.isCommitted) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "${e.message?.replace("\"", "'")}"}""")
            }
        }
    }

    private fun checkUploadCompletion(uploadUri: String) {
        try {
            val uploadInfo = tusService.getUploadInfo(uploadUri, null)
            uploadHandler?.checkAndProcessUpload(uploadInfo, uploadUri)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to check upload completion: ${e.message}")
        }
    }

    /**
     * Extracts the upload ID from the path info.
     * e.g., "/af0a0139-6aeb-4669-995f-5268ef53c77d" -> "af0a0139-6aeb-4669-995f-5268ef53c77d"
     */
    private fun extractUploadId(pathInfo: String?): String? {
        if (pathInfo.isNullOrBlank()) return null
        return pathInfo.removePrefix("/").takeIf { it.isNotBlank() }
    }

    /**
     * Manually deletes upload files from the TUS storage directory.
     * The tus-java-server library stores files in uploads/{id} subdirectory structure.
     * This is a workaround for the library's DELETE not actually deleting files.
     *
     * @param uploadId The TUS upload ID to delete
     * @return Number of files/directories deleted
     */
    private fun manuallyDeleteUploadFiles(uploadId: String): Int {
        val tusDir = tusDataDir ?: return 0
        var deletedCount = 0

        try {
            // tus-java-server DiskStorageService stores files in:
            // - uploads/{id} (directory containing the data)
            // - uploads/{id}.info (metadata file, though library may use different format)
            // - locks/{id} (lock files)

            val uploadsDir = java.io.File(tusDir, "uploads")
            val locksDir = java.io.File(tusDir, "locks")

            // Log all files in uploads dir to see what naming convention is used
            android.util.Log.d(TAG, "Looking for upload ID '$uploadId' in uploads/")
            uploadsDir.listFiles()?.forEach { file ->
                val matches = file.name == uploadId || file.name.startsWith("$uploadId.")
                android.util.Log.d(TAG, "  File: ${file.name} (matches: $matches, isDir: ${file.isDirectory})")
            }

            // Delete upload data directory/files
            if (uploadsDir.exists()) {
                uploadsDir.listFiles()?.filter { file ->
                    file.name == uploadId || file.name.startsWith("$uploadId.")
                }?.forEach { file ->
                    val size = if (file.isDirectory) {
                        file.walkTopDown().sumOf { it.length() }
                    } else {
                        file.length()
                    }
                    if (deleteRecursively(file)) {
                        android.util.Log.i(TAG, "Deleted upload file/dir: ${file.name} (${formatSize(size)})")
                        deletedCount++
                    } else {
                        android.util.Log.w(TAG, "Failed to delete upload file/dir: ${file.name}")
                    }
                }
            }

            // Delete lock files
            if (locksDir.exists()) {
                locksDir.listFiles()?.filter { file ->
                    file.name == uploadId || file.name.startsWith("$uploadId.")
                }?.forEach { file ->
                    if (file.delete()) {
                        android.util.Log.d(TAG, "Deleted lock file: ${file.name}")
                        deletedCount++
                    }
                }
            }

            if (deletedCount == 0) {
                android.util.Log.w(TAG, "No files found matching upload ID: $uploadId")
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error during manual upload cleanup: ${e.message}", e)
        }

        return deletedCount
    }

    /**
     * Recursively deletes a file or directory.
     */
    private fun deleteRecursively(file: java.io.File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                if (!deleteRecursively(child)) {
                    return false
                }
            }
        }
        return file.delete()
    }

    private fun logTusDirectoryContents(context: String = "") {
        try {
            val tusDir = tusDataDir ?: return

            if (!tusDir.exists()) {
                android.util.Log.d(TAG, "TUS directory does not exist")
                return
            }

            // Recursively calculate total size and count files
            var totalFiles = 0
            var totalSize = 0L
            val subdirSizes = mutableMapOf<String, Pair<Int, Long>>() // name -> (fileCount, size)

            tusDir.listFiles()?.forEach { entry ->
                if (entry.isDirectory) {
                    val (count, size) = countDirectoryContents(entry)
                    subdirSizes[entry.name] = Pair(count, size)
                    totalFiles += count
                    totalSize += size
                } else {
                    totalFiles++
                    totalSize += entry.length()
                }
            }

            val prefix = if (context.isNotEmpty()) "[$context] " else ""
            android.util.Log.i(TAG, "${prefix}TUS directory: $totalFiles files, total size: ${formatSize(totalSize)}")
            subdirSizes.forEach { (name, data) ->
                val (count, size) = data
                android.util.Log.d(TAG, "${prefix}  - $name/: $count files, ${formatSize(size)}")
            }

            // Log actual upload directories in uploads directory for debugging
            // Each upload is a directory named after the upload ID, containing 'data' and 'info' files
            val uploadsDir = java.io.File(tusDir, "uploads")
            if (uploadsDir.exists()) {
                uploadsDir.listFiles()?.take(5)?.forEach { uploadDir ->
                    if (uploadDir.isDirectory) {
                        val dataFile = java.io.File(uploadDir, "data")
                        val infoFile = java.io.File(uploadDir, "info")
                        val dataSize = if (dataFile.exists()) dataFile.length() else 0L
                        val infoSize = if (infoFile.exists()) infoFile.length() else 0L
                        android.util.Log.d(TAG, "${prefix}    - uploads/${uploadDir.name}/: data=${formatSize(dataSize)}, info=${formatSize(infoSize)}")
                    } else {
                        android.util.Log.d(TAG, "${prefix}    - uploads/${uploadDir.name}: ${formatSize(uploadDir.length())} (unexpected file)")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to log TUS directory: ${e.message}")
        }
    }

    private fun countDirectoryContents(dir: java.io.File): Pair<Int, Long> {
        var count = 0
        var size = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val (subCount, subSize) = countDirectoryContents(file)
                count += subCount
                size += subSize
            } else {
                count++
                size += file.length()
            }
        }
        return Pair(count, size)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

