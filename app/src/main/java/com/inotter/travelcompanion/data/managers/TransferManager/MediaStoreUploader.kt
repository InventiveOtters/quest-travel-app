package com.inotter.travelcompanion.data.managers.TransferManager

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
     */
    fun createPendingVideo(filename: String, mimeType: String): Uri? {
        val videoDetails = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
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

    /** Opens an OutputStream for writing file data to a MediaStore URI. */
    fun getOutputStream(uri: Uri): OutputStream? {
        return try {
            contentResolver.openOutputStream(uri)?.let { outputStream ->
                java.io.BufferedOutputStream(outputStream, BUFFER_SIZE)
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreUploader", "Failed to open output stream", e)
            null
        }
    }

    /** Opens an OutputStream for appending to an existing MediaStore URI. */
    fun getAppendOutputStream(uri: Uri): OutputStream? {
        return try {
            contentResolver.openOutputStream(uri, "wa")?.let { outputStream ->
                java.io.BufferedOutputStream(outputStream, BUFFER_SIZE)
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaStoreUploader", "Failed to open append stream", e)
            null
        }
    }

    /** Gets the current size of a MediaStore entry. */
    fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.Video.Media.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (_: Exception) { 0L }
    }

    /** Checks if a MediaStore entry is still pending. */
    fun isPending(uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.Video.Media.IS_PENDING), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) == 1 else false
            } ?: false
        } catch (_: Exception) { false }
    }

    /** Marks a pending video as complete, making it visible to other apps. */
    fun finalizePendingVideo(uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val updateValues = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        return try {
            contentResolver.update(uri, updateValues, null, null) > 0
        } catch (_: Exception) { false }
    }

    /** Cancels a pending video upload and deletes the MediaStore entry. */
    fun cancelPendingVideo(uri: Uri): Boolean {
        return try {
            contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) { false }
    }

    /** Extracts the display name from a MediaStore content URI. */
    fun getDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null }
    }
}

