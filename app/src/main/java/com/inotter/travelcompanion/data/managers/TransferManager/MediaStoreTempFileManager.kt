package com.inotter.travelcompanion.data.managers.TransferManager

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
 * @param uploader MediaStoreUploader instance for MediaStore operations
 * @param tempDir Directory for internal buffer temp files (use context.cacheDir)
 */
class MediaStoreTempFileManager(
    private val uploader: MediaStoreUploader,
    private val tempDir: File
) : NanoHTTPD.TempFileManager {

    private val mediaStoreTempFiles = mutableListOf<MediaStoreUploadTempFile>()
    private val regularTempFiles = mutableListOf<RegularTempFile>()

    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp")

    override fun createTempFile(filename_hint: String?): NanoHTTPD.TempFile {
        val filename = extractFilename(filename_hint)
        val extension = filename.substringAfterLast('.', "").lowercase()

        return if (extension in videoExtensions) {
            createMediaStoreTempFile(filename)
        } else {
            createRegularTempFile()
        }
    }

    private fun createMediaStoreTempFile(filename: String): NanoHTTPD.TempFile {
        val mimeType = getMimeType(filename)
        val uri = uploader.createPendingVideo(filename, mimeType)
            ?: throw java.io.IOException("Failed to create MediaStore entry for $filename")
        val tempFile = MediaStoreUploadTempFile(uploader, uri, filename)
        mediaStoreTempFiles.add(tempFile)
        android.util.Log.d("MediaStoreTempFileManager", "Created MediaStore temp file for: $filename -> $uri")
        return tempFile
    }

    private fun createRegularTempFile(): NanoHTTPD.TempFile {
        val tempFile = RegularTempFile(tempDir)
        regularTempFiles.add(tempFile)
        android.util.Log.d("MediaStoreTempFileManager", "Created regular temp file: ${tempFile.getName()}")
        return tempFile
    }

    override fun clear() {
        for (tempFile in mediaStoreTempFiles) {
            try { tempFile.delete() } catch (_: Exception) { }
        }
        mediaStoreTempFiles.clear()
        for (tempFile in regularTempFiles) {
            try { tempFile.delete() } catch (_: Exception) { }
        }
        regularTempFiles.clear()
    }

    fun findByUri(uriString: String): MediaStoreUploadTempFile? {
        return mediaStoreTempFiles.find { it.uri.toString() == uriString }
    }

    fun markFinalized(tempFile: MediaStoreUploadTempFile) {
        tempFile.markCancelled()
        mediaStoreTempFiles.remove(tempFile)
    }

    private fun extractFilename(hint: String?): String {
        if (hint.isNullOrBlank()) return "upload_${System.currentTimeMillis()}.tmp"
        val cleanName = hint.replace("\\", "/").substringAfterLast("/").trim()
        return if (cleanName.isNotBlank()) cleanName else "upload_${System.currentTimeMillis()}.tmp"
    }

    private fun getMimeType(filename: String): String {
        return when (filename.substringAfterLast('.', "").lowercase()) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "m4v" -> "video/x-m4v"
            "3gp" -> "video/3gpp"
            else -> "video/mp4"
        }
    }

    private class RegularTempFile(tempDir: File) : NanoHTTPD.TempFile {
        private val file: File = File.createTempFile("NanoHTTPD-buffer-", ".tmp", tempDir)
        private val outputStream: OutputStream = FileOutputStream(file)

        override fun getName(): String = file.absolutePath
        override fun open(): OutputStream = outputStream
        override fun delete() {
            try { outputStream.close() } catch (_: Exception) { }
            file.delete()
        }
    }

    class Factory(
        private val contentResolver: ContentResolver,
        private val tempDir: File
    ) : NanoHTTPD.TempFileManagerFactory {
        override fun create(): NanoHTTPD.TempFileManager {
            return MediaStoreTempFileManager(MediaStoreUploader(contentResolver), tempDir)
        }
    }
}

