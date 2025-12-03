package com.inotter.travelcompanion.domain.transfer

import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import java.io.OutputStream

/**
 * NanoHTTPD TempFile implementation that writes directly to MediaStore.
 * 
 * Adapts the MediaStore upload flow to work with NanoHTTPD's TempFile interface,
 * which is used for handling multipart file uploads.
 * 
 * The MediaStore entry is created with IS_PENDING=1 (hidden), and must be
 * finalized after successful upload or cancelled on failure.
 *
 * @param uploader MediaStoreUploader instance for MediaStore operations
 * @param uri Content URI of the pending MediaStore entry
 * @param filename Original filename for logging purposes
 */
class MediaStoreUploadTempFile(
    private val uploader: MediaStoreUploader,
    val uri: Uri,
    private val filename: String
) : NanoHTTPD.TempFile {

    private var outputStream: OutputStream? = null
    private var isCancelled = false

    /**
     * Returns the MediaStore content URI as the "name" of this temp file.
     * This will be used by UploadServer to identify the uploaded file.
     */
    override fun getName(): String = uri.toString()

    /**
     * Opens and returns the OutputStream for writing to MediaStore.
     * The stream is buffered for optimal large file I/O performance.
     */
    override fun open(): OutputStream {
        val stream = uploader.getOutputStream(uri)
            ?: throw java.io.IOException("Failed to open MediaStore output stream for $filename")
        outputStream = stream
        return stream
    }

    /**
     * Deletes (cancels) the pending MediaStore entry.
     * Called by NanoHTTPD when upload fails or is cancelled.
     */
    override fun delete() {
        try {
            outputStream?.close()
        } catch (e: Exception) {
            android.util.Log.w("MediaStoreUploadTempFile", "Error closing stream: ${e.message}")
        }
        
        if (!isCancelled) {
            isCancelled = true
            val deleted = uploader.cancelPendingVideo(uri)
            if (deleted) {
                android.util.Log.d("MediaStoreUploadTempFile", "Cancelled pending upload: $filename")
            } else {
                android.util.Log.w("MediaStoreUploadTempFile", "Failed to cancel pending upload: $filename")
            }
        }
    }

    /**
     * Finalizes the upload by setting IS_PENDING=0, making the file visible.
     * Must be called after successful upload completion.
     * 
     * @return true if finalization succeeded, false otherwise
     */
    fun finalize(): Boolean {
        try {
            outputStream?.close()
        } catch (e: Exception) {
            android.util.Log.w("MediaStoreUploadTempFile", "Error closing stream: ${e.message}")
        }
        
        val success = uploader.finalizePendingVideo(uri)
        if (success) {
            android.util.Log.d("MediaStoreUploadTempFile", "Finalized upload: $filename")
        } else {
            android.util.Log.e("MediaStoreUploadTempFile", "Failed to finalize upload: $filename")
        }
        return success
    }

    /**
     * Marks this upload as cancelled to prevent double-deletion.
     */
    fun markCancelled() {
        isCancelled = true
    }
}

