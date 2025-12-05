package com.inotter.travelcompanion.data.managers.TransferManager

import android.net.Uri
import com.inotter.travelcompanion.data.repositories.UploadSessionRepository.UploadSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.desair.tus.server.TusFileUploadService
import me.desair.tus.server.upload.UploadInfo
import java.io.File
import java.io.FileInputStream

/**
 * Handles TUS upload completion events and moves files to MediaStore.
 *
 * This handler monitors TUS uploads and when complete:
 * 1. Creates a session record in Room
 * 2. Copies the file to MediaStore
 * 3. Cleans up the TUS temp file
 */
class TusUploadHandler(
    private val uploadSessionRepository: UploadSessionRepository,
    private val mediaStoreUploader: MediaStoreUploader,
    private val tusService: TusFileUploadService,
    private val tusDataDir: File,
    private val onFileUploaded: (Uri) -> Unit = {}
) {
    companion object {
        private const val TAG = "TusUploadHandler"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Checks if an upload is complete and processes it.
     * Call this after each PATCH request to check completion.
     * @param uploadUri The URI of the upload (e.g., "/tus/abc123")
     */
    fun checkAndProcessUpload(uploadInfo: UploadInfo?, uploadUri: String? = null) {
        if (uploadInfo == null) return

        // Check if upload is complete
        val offset = uploadInfo.offset ?: 0L
        val length = uploadInfo.length ?: 0L
        if (offset >= length && length > 0) {
            processCompletedUpload(uploadInfo, uploadUri)
        }
    }

    private fun processCompletedUpload(uploadInfo: UploadInfo, uploadUri: String?) {
        scope.launch {
            try {
                val filename = extractFilename(uploadInfo)
                val mimeType = extractMimeType(uploadInfo)
                val tusId = uploadInfo.id?.toString() ?: return@launch
                val resolvedUploadUri = uploadUri ?: "/tus/$tusId"

                android.util.Log.i(TAG, "Processing completed upload: $tusId - $filename")

                // Get the uploaded file from TUS storage
                val uploadedBytes = tusService.getUploadedBytes(resolvedUploadUri, null)
                if (uploadedBytes == null) {
                    android.util.Log.e(TAG, "Failed to get uploaded bytes for $tusId")
                    return@launch
                }

                // Create MediaStore entry
                val mediaStoreUri = mediaStoreUploader.createPendingVideo(filename, mimeType)
                if (mediaStoreUri == null) {
                    android.util.Log.e(TAG, "Failed to create MediaStore entry for $tusId")
                    return@launch
                }

                // Create session record
                uploadSessionRepository.createSession(
                    tusUploadId = tusId,
                    uploadUrl = resolvedUploadUri,
                    filename = filename,
                    expectedSize = uploadInfo.length ?: 0L,
                    mediaStoreUri = mediaStoreUri.toString(),
                    mimeType = mimeType
                )

                // Copy file to MediaStore
                val outputStream = mediaStoreUploader.getAppendOutputStream(mediaStoreUri)
                if (outputStream != null) {
                    try {
                        uploadedBytes.copyTo(outputStream)
                        outputStream.flush()
                    } finally {
                        outputStream.close()
                        uploadedBytes.close()
                    }
                }

                // Finalize MediaStore entry
                val success = mediaStoreUploader.finalizePendingVideo(mediaStoreUri)
                if (success) {
                    uploadSessionRepository.markCompletedByTusId(tusId)
                    onFileUploaded(mediaStoreUri)
                    android.util.Log.i(TAG, "Upload complete and moved to MediaStore: $filename")
                } else {
                    uploadSessionRepository.markFailed(
                        uploadSessionRepository.getByTusId(tusId)?.id ?: 0
                    )
                    android.util.Log.e(TAG, "Failed to finalize MediaStore entry: $filename")
                }

                // Cleanup TUS temp file
                try {
                    tusService.deleteUpload(resolvedUploadUri, null)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to cleanup TUS file: ${e.message}")
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error processing upload: ${e.message}", e)
            }
        }
    }

    private fun extractFilename(uploadInfo: UploadInfo): String {
        val metadata = uploadInfo.metadata ?: return "upload_${System.currentTimeMillis()}.mp4"
        return metadata["filename"] as? String ?: "upload_${System.currentTimeMillis()}.mp4"
    }

    private fun extractMimeType(uploadInfo: UploadInfo): String {
        val metadata = uploadInfo.metadata ?: return "video/mp4"
        return (metadata["filetype"] as? String) ?: "video/mp4"
    }
}

