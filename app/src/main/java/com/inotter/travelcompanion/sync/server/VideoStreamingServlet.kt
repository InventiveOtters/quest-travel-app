package com.inotter.travelcompanion.sync.server

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * HTTP servlet for streaming video files with range request support.
 * 
 * Supports:
 * - GET /video/{movieId} - Stream video file
 * - HTTP Range requests for seeking (206 Partial Content)
 * - Multiple video formats (MP4, MKV, etc.)
 * 
 * Range request format:
 * - Request: Range: bytes=0-1023
 * - Response: 206 Partial Content, Content-Range: bytes 0-1023/total
 */
class VideoStreamingServlet : HttpServlet() {

    companion object {
        private const val TAG = "VideoStreamingServlet"
        private const val BUFFER_SIZE = 8192 // 8KB chunks for streaming
        
        // MIME types for video formats
        private val VIDEO_MIME_TYPES = mapOf(
            "mp4" to "video/mp4",
            "mkv" to "video/x-matroska",
            "webm" to "video/webm",
            "avi" to "video/x-msvideo",
            "mov" to "video/quicktime",
            "m4v" to "video/x-m4v"
        )
    }

    // Map of movieId -> File path
    private val videoFiles = mutableMapOf<String, File>()

    /**
     * Register a video file for streaming.
     * @param movieId Unique identifier for the movie
     * @param videoFile File to stream
     */
    fun registerVideo(movieId: String, videoFile: File) {
        if (!videoFile.exists() || !videoFile.canRead()) {
            Log.e(TAG, "Cannot register video: file not found or not readable: ${videoFile.absolutePath}")
            return
        }
        videoFiles[movieId] = videoFile
        Log.i(TAG, "Registered video: $movieId -> ${videoFile.name} (${videoFile.length()} bytes)")
    }

    /**
     * Unregister a video file.
     */
    fun unregisterVideo(movieId: String) {
        videoFiles.remove(movieId)
        Log.i(TAG, "Unregistered video: $movieId")
    }

    /**
     * Clear all registered videos.
     */
    fun clearVideos() {
        videoFiles.clear()
        Log.i(TAG, "Cleared all registered videos")
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val pathInfo = req.pathInfo ?: ""
        val movieId = pathInfo.removePrefix("/")

        if (movieId.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Movie ID required")
            return
        }

        val videoFile = videoFiles[movieId]
        if (videoFile == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Video not found: $movieId")
            return
        }

        try {
            streamVideo(req, resp, videoFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming video: $movieId", e)
            if (!resp.isCommitted) {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Streaming error: ${e.message}")
            }
        }
    }

    /**
     * Stream video file with range request support.
     */
    private fun streamVideo(req: HttpServletRequest, resp: HttpServletResponse, videoFile: File) {
        val fileLength = videoFile.length()
        val rangeHeader = req.getHeader("Range")

        // Set MIME type based on file extension
        resp.contentType = getMimeType(videoFile.name)
        resp.setHeader("Accept-Ranges", "bytes")

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            // Handle range request (for seeking)
            handleRangeRequest(resp, videoFile, fileLength, rangeHeader)
        } else {
            // Handle full file request
            handleFullRequest(resp, videoFile, fileLength)
        }
    }

    /**
     * Handle HTTP range request (206 Partial Content).
     */
    private fun handleRangeRequest(
        resp: HttpServletResponse,
        videoFile: File,
        fileLength: Long,
        rangeHeader: String
    ) {
        val range = parseRange(rangeHeader, fileLength)
        if (range == null) {
            resp.setHeader("Content-Range", "bytes */$fileLength")
            sendError(resp, HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE, "Invalid range")
            return
        }

        val (start, end) = range
        val contentLength = end - start + 1

        resp.status = HttpServletResponse.SC_PARTIAL_CONTENT
        resp.setHeader("Content-Range", "bytes $start-$end/$fileLength")
        resp.setContentLengthLong(contentLength)

        Log.d(TAG, "Range request: bytes $start-$end/$fileLength (${contentLength} bytes)")

        streamFileRange(videoFile, start, contentLength, resp)
    }

    /**
     * Handle full file request (200 OK).
     */
    private fun handleFullRequest(resp: HttpServletResponse, videoFile: File, fileLength: Long) {
        resp.status = HttpServletResponse.SC_OK
        resp.setContentLengthLong(fileLength)

        Log.d(TAG, "Full file request: ${videoFile.name} (${fileLength} bytes)")

        streamFileRange(videoFile, 0, fileLength, resp)
    }

    /**
     * Stream a range of bytes from the file.
     */
    private fun streamFileRange(videoFile: File, start: Long, length: Long, resp: HttpServletResponse) {
        RandomAccessFile(videoFile, "r").use { raf ->
            raf.seek(start)

            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = length

            resp.outputStream.use { output ->
                while (remaining > 0) {
                    val toRead = minOf(remaining, BUFFER_SIZE.toLong()).toInt()
                    val bytesRead = raf.read(buffer, 0, toRead)

                    if (bytesRead == -1) break

                    output.write(buffer, 0, bytesRead)
                    remaining -= bytesRead
                }
                output.flush()
            }
        }
    }

    /**
     * Parse HTTP Range header.
     * Format: "bytes=start-end" or "bytes=start-"
     * Returns (start, end) pair or null if invalid.
     */
    private fun parseRange(rangeHeader: String, fileLength: Long): Pair<Long, Long>? {
        try {
            val rangeValue = rangeHeader.removePrefix("bytes=").trim()
            val parts = rangeValue.split("-")

            if (parts.size != 2) return null

            val start = parts[0].toLongOrNull() ?: return null
            val end = if (parts[1].isEmpty()) {
                fileLength - 1
            } else {
                parts[1].toLongOrNull() ?: return null
            }

            // Validate range
            if (start < 0 || end >= fileLength || start > end) {
                return null
            }

            return Pair(start, end)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse range header: $rangeHeader", e)
            return null
        }
    }

    /**
     * Get MIME type based on file extension.
     */
    private fun getMimeType(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return VIDEO_MIME_TYPES[extension] ?: "video/mp4"
    }

    /**
     * Send error response.
     */
    private fun sendError(resp: HttpServletResponse, status: Int, message: String) {
        resp.status = status
        resp.contentType = "application/json"
        resp.writer.write("""{"error": "$message"}""")
        Log.w(TAG, "Error response: $status - $message")
    }
}
