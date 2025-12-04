package com.inotter.travelcompanion.data.managers.TransferManager

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.inotter.travelcompanion.data.managers.TransferManager.models.IncompleteUpload
import com.inotter.travelcompanion.data.managers.TransferManager.models.OrphanedMediaStoreEntry
import com.inotter.travelcompanion.data.repositories.UploadSessionRepository.UploadSessionRepository

/**
 * Detects and manages incomplete (orphaned) uploads.
 *
 * @property contentResolver ContentResolver for MediaStore operations
 * @property uploadSessionRepository Repository for upload session data
 */
class IncompleteUploadDetector(
    private val contentResolver: ContentResolver,
    private val uploadSessionRepository: UploadSessionRepository
) {
    companion object {
        private const val TAG = "IncompleteUploadDetector"
    }

    suspend fun detectIncompleteUploads(): List<IncompleteUpload> {
        val sessions = uploadSessionRepository.getIncompleteSessions()
        return sessions.map { session ->
            val uri = Uri.parse(session.mediaStoreUri)
            val (exists, currentSize) = checkMediaStoreEntry(uri)
            IncompleteUpload(session = session, mediaStoreExists = exists, currentSize = currentSize)
        }
    }

    private fun checkMediaStoreEntry(uri: Uri): Pair<Boolean, Long> {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.Video.Media.SIZE, MediaStore.Video.Media.IS_PENDING), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) Pair(true, cursor.getLong(0)) else Pair(false, 0L)
            } ?: Pair(false, 0L)
        } catch (_: Exception) { Pair(false, 0L) }
    }

    fun detectOrphanedMediaStoreEntries(): List<OrphanedMediaStoreEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val orphaned = mutableListOf<OrphanedMediaStoreEntry>()
        try {
            val selection = "(${MediaStore.Video.Media.RELATIVE_PATH} = ? OR ${MediaStore.Video.Media.RELATIVE_PATH} = ?) AND ${MediaStore.Video.Media.IS_PENDING} = ?"
            val selectionArgs = arrayOf(MediaStoreUploader.RELATIVE_PATH, "${MediaStoreUploader.RELATIVE_PATH}/", "1")
            contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE),
                selection, selectionArgs, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    orphaned.add(OrphanedMediaStoreEntry(
                        mediaStoreId = id,
                        contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        displayName = cursor.getString(nameColumn) ?: "unknown",
                        currentSize = cursor.getLong(sizeColumn)
                    ))
                }
            }
        } catch (_: Exception) { }
        return orphaned
    }

    suspend fun detectTrulyOrphanedMediaStoreEntries(): List<OrphanedMediaStoreEntry> {
        val allPending = detectOrphanedMediaStoreEntries()
        if (allPending.isEmpty()) return emptyList()
        val knownUris = uploadSessionRepository.getIncompleteSessions().map { it.mediaStoreUri }.toSet()
        return allPending.filter { it.contentUri.toString() !in knownUris }
    }

    suspend fun cleanupUpload(upload: IncompleteUpload): Boolean {
        return try {
            if (upload.mediaStoreExists) contentResolver.delete(Uri.parse(upload.session.mediaStoreUri), null, null)
            uploadSessionRepository.markCancelled(upload.session.id)
            true
        } catch (_: Exception) { false }
    }

    fun cleanupOrphanedEntry(entry: OrphanedMediaStoreEntry): Boolean {
        return try { contentResolver.delete(entry.contentUri, null, null) > 0 } catch (_: Exception) { false }
    }

    suspend fun cleanupAllIncompleteUploads(): Int {
        var count = 0
        detectIncompleteUploads().forEach { if (cleanupUpload(it)) count++ }
        detectTrulyOrphanedMediaStoreEntries().forEach { if (cleanupOrphanedEntry(it)) count++ }
        uploadSessionRepository.cleanupFinishedSessions()
        return count
    }

    suspend fun getIncompleteCount(): Int {
        return uploadSessionRepository.getIncompleteSessions().size + detectTrulyOrphanedMediaStoreEntries().size
    }

    suspend fun getIncompleteStorageUsed(): Long {
        return detectIncompleteUploads().sumOf { it.currentSize } + detectTrulyOrphanedMediaStoreEntries().sumOf { it.currentSize }
    }
}

