package com.example.travelcompanion.vrvideo.domain.transfer

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.OutputStream

/**
 * Utility class for uploading files directly to the public MediaStore.
 * 
 * Files are written to Movies/TravelCompanion/ and:
 * - Survive app uninstall
 * - Are visible to other apps (file managers, galleries)
 * - Are discovered by MediaStoreScanWorker automatically
 * 
 * Uses IS_PENDING flag to hide incomplete uploads from other apps.
 * No special permissions required - apps can write their own media files.
 *
 * @param contentResolver Android ContentResolver for MediaStore operations
 */
class MediaStoreUploader(
    private val contentResolver: ContentResolver
) {
    companion object {
        /** Subfolder within Movies where uploaded files are stored */
        const val SUBFOLDER_NAME = "TravelCompanion"
        
        /** Full relative path for MediaStore */
        const val RELATIVE_PATH = "Movies/$SUBFOLDER_NAME"
        
        /** Buffer size for writing uploaded files (256KB for optimal large file I/O) */
        const val BUFFER_SIZE = 256 * 1024
    }

    /**
     * Creates a new pending video entry in MediaStore.
     * The file is hidden from other apps until finalized with [finalizePendingVideo].
     *
     * @param filename Display name for the file (e.g., "video.mp4")
     * @param mimeType MIME type of the video (e.g., "video/mp4")
     * @return Content URI for writing the file data, or null if creation failed
     */
    fun createPendingVideo(filename: String, mimeType: String): Uri? {
        val videoDetails = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1) // Hide until complete
            }
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        
        return try {
            contentResolver.insert(collection, videoDetails)
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreUploader", "Failed to create pending video", e)
            null
        }
    }

    /**
     * Opens an OutputStream for writing file data to a MediaStore URI.
     *
     * @param uri Content URI obtained from [createPendingVideo]
     * @return OutputStream for writing, or null if open failed
     */
    fun getOutputStream(uri: Uri): OutputStream? {
        return try {
            contentResolver.openOutputStream(uri)?.let { outputStream ->
                // Wrap in BufferedOutputStream for optimal large file I/O
                java.io.BufferedOutputStream(outputStream, BUFFER_SIZE)
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreUploader", "Failed to open output stream", e)
            null
        }
    }

    /**
     * Marks a pending video as complete, making it visible to other apps.
     * Call this after successfully writing all file data.
     *
     * @param uri Content URI of the pending video
     * @return true if finalization succeeded, false otherwise
     */
    fun finalizePendingVideo(uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // IS_PENDING not supported before Android Q
            return true
        }
        
        val updateValues = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        
        return try {
            val rowsUpdated = contentResolver.update(uri, updateValues, null, null)
            rowsUpdated > 0
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreUploader", "Failed to finalize video", e)
            false
        }
    }

    /**
     * Cancels a pending video upload and deletes the MediaStore entry.
     * Call this when an upload fails or is cancelled.
     *
     * @param uri Content URI of the pending video to cancel
     * @return true if deletion succeeded, false otherwise
     */
    fun cancelPendingVideo(uri: Uri): Boolean {
        return try {
            val rowsDeleted = contentResolver.delete(uri, null, null)
            rowsDeleted > 0
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreUploader", "Failed to cancel pending video", e)
            false
        }
    }

    /**
     * Extracts the display name from a MediaStore content URI.
     *
     * @param uri Content URI of the video
     * @return Display name of the file, or null if not found
     */
    fun getDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.Video.Media.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreUploader", "Failed to get display name", e)
            null
        }
    }
}

