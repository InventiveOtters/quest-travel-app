package com.example.travelcompanion.vrvideo.domain.transfer

import android.content.ContentResolver
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * NanoHTTPD TempFileManager implementation that uses a hybrid approach:
 * - Regular file-based temp files for NanoHTTPD's internal buffering (getTmpBucket)
 * - MediaStore-backed temp files for actual video file uploads
 *
 * This is necessary because NanoHTTPD's internal getTmpBucket() creates a temp file
 * and opens it with RandomAccessFile using getName(), which requires a real file path.
 * Content URIs from MediaStore cannot be used with RandomAccessFile.
 *
 * Files uploaded this way:
 * - Survive app uninstall
 * - Are visible to other apps (file managers, galleries)
 * - Are discovered by MediaStoreScanWorker automatically
 *
 * @param uploader MediaStoreUploader instance for MediaStore operations
 * @param tempDir Directory for internal buffer temp files (use context.cacheDir)
 */
class MediaStoreTempFileManager(
    private val uploader: MediaStoreUploader,
    private val tempDir: File
) : NanoHTTPD.TempFileManager {

    private val mediaStoreTempFiles = mutableListOf<MediaStoreUploadTempFile>()
    private val regularTempFiles = mutableListOf<RegularTempFile>()

    // Video extensions that should go to MediaStore
    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp")

    /**
     * Creates a new temp file.
     *
     * If the filename hint indicates a video file, creates a MediaStore-backed temp file.
     * Otherwise, creates a regular file-based temp file for internal NanoHTTPD buffering.
     *
     * @param filename_hint Hint for the filename (may be null or contain path info)
     * @return TempFile instance (either MediaStore-backed or regular file-backed)
     */
    override fun createTempFile(filename_hint: String?): NanoHTTPD.TempFile {
        val filename = extractFilename(filename_hint)
        val extension = filename.substringAfterLast('.', "").lowercase()

        // Use MediaStore only for video files with known extensions
        return if (extension in videoExtensions) {
            createMediaStoreTempFile(filename)
        } else {
            // For internal NanoHTTPD buffers and non-video files, use regular temp files
            createRegularTempFile()
        }
    }

    /**
     * Creates a MediaStore-backed temp file for video uploads.
     */
    private fun createMediaStoreTempFile(filename: String): NanoHTTPD.TempFile {
        val mimeType = getMimeType(filename)

        // Create pending MediaStore entry
        val uri = uploader.createPendingVideo(filename, mimeType)
            ?: throw java.io.IOException("Failed to create MediaStore entry for $filename")

        val tempFile = MediaStoreUploadTempFile(uploader, uri, filename)
        mediaStoreTempFiles.add(tempFile)

        android.util.Log.d("MediaStoreTempFileManager", "Created MediaStore temp file for: $filename -> $uri")
        return tempFile
    }

    /**
     * Creates a regular file-based temp file for internal buffering.
     * These are used by NanoHTTPD's getTmpBucket() which requires RandomAccessFile support.
     */
    private fun createRegularTempFile(): NanoHTTPD.TempFile {
        val tempFile = RegularTempFile(tempDir)
        regularTempFiles.add(tempFile)
        android.util.Log.d("MediaStoreTempFileManager", "Created regular temp file: ${tempFile.getName()}")
        return tempFile
    }

    /**
     * Cleans up any incomplete uploads and temporary files.
     * Called by NanoHTTPD after request processing is complete.
     */
    override fun clear() {
        // Clean up MediaStore temp files (cancel pending entries)
        for (tempFile in mediaStoreTempFiles) {
            try {
                tempFile.delete()
            } catch (e: Exception) {
                android.util.Log.w("MediaStoreTempFileManager",
                    "Failed to clear MediaStore temp file: ${e.message}")
            }
        }
        mediaStoreTempFiles.clear()

        // Clean up regular temp files
        for (tempFile in regularTempFiles) {
            try {
                tempFile.delete()
            } catch (e: Exception) {
                android.util.Log.w("MediaStoreTempFileManager",
                    "Failed to clear regular temp file: ${e.message}")
            }
        }
        regularTempFiles.clear()
    }

    /**
     * Finds a temp file by its MediaStore URI string.
     * Used by UploadServer to finalize a specific upload.
     *
     * @param uriString The URI string (from TempFile.getName())
     * @return The MediaStoreUploadTempFile, or null if not found
     */
    fun findByUri(uriString: String): MediaStoreUploadTempFile? {
        return mediaStoreTempFiles.find { it.uri.toString() == uriString }
    }

    /**
     * Removes a temp file from the tracking list after successful finalization.
     * This prevents it from being deleted during clear().
     */
    fun markFinalized(tempFile: MediaStoreUploadTempFile) {
        tempFile.markCancelled() // Prevent delete() from cancelling the MediaStore entry
        mediaStoreTempFiles.remove(tempFile)
    }

    /**
     * Extracts a clean filename from the hint provided by NanoHTTPD.
     */
    private fun extractFilename(hint: String?): String {
        if (hint.isNullOrBlank()) {
            return "upload_${System.currentTimeMillis()}.tmp"
        }

        // Remove any path components, keep just the filename
        val cleanName = hint
            .replace("\\", "/")
            .substringAfterLast("/")
            .trim()

        return if (cleanName.isNotBlank()) cleanName else "upload_${System.currentTimeMillis()}.tmp"
    }

    /**
     * Determines MIME type based on file extension.
     */
    private fun getMimeType(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "m4v" -> "video/x-m4v"
            "3gp" -> "video/3gpp"
            else -> "video/mp4" // Default to mp4 for unknown
        }
    }

    /**
     * Regular file-based TempFile implementation for internal NanoHTTPD buffering.
     * This is similar to NanoHTTPD's DefaultTempFile but with proper cleanup.
     */
    private class RegularTempFile(tempDir: File) : NanoHTTPD.TempFile {
        private val file: File = File.createTempFile("NanoHTTPD-buffer-", ".tmp", tempDir)
        private val outputStream: OutputStream = FileOutputStream(file)

        override fun getName(): String = file.absolutePath

        override fun open(): OutputStream = outputStream

        override fun delete() {
            try {
                outputStream.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
            file.delete()
        }
    }

    /**
     * Factory for creating MediaStoreTempFileManager instances.
     * Used with NanoHTTPD.setTempFileManagerFactory().
     *
     * @param contentResolver ContentResolver for MediaStore operations
     * @param tempDir Directory for internal buffer temp files
     */
    class Factory(
        private val contentResolver: ContentResolver,
        private val tempDir: File
    ) : NanoHTTPD.TempFileManagerFactory {
        override fun create(): NanoHTTPD.TempFileManager {
            val uploader = MediaStoreUploader(contentResolver)
            return MediaStoreTempFileManager(uploader, tempDir)
        }
    }
}

