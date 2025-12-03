package com.inotter.travelcompanion.domain.transfer

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.inotter.travelcompanion.data.db.UploadSession
import com.inotter.travelcompanion.data.repo.UploadSessionRepository

/**
 * Detects and manages incomplete (orphaned) uploads.
 *
 * When power is lost during an upload, the MediaStore entry remains with IS_PENDING=1
 * and the upload session record persists in the database. This class helps detect
 * such orphaned uploads and provides options to resume or clean them up.
 *
 * Additionally, this class can detect "orphaned" MediaStore entries - files in
 * Movies/TravelCompanion with IS_PENDING=1 that have no corresponding database record.
 * This can happen if the database was cleared or the app crashed during session creation.
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

    /**
     * Data class representing an incomplete upload with validation status.
     */
    data class IncompleteUpload(
        val session: UploadSession,
        val mediaStoreExists: Boolean,
        val currentSize: Long
    ) {
        /** Whether this upload can be resumed (MediaStore entry still exists) */
        val canResume: Boolean get() = mediaStoreExists && session.bytesReceived > 0

        /** Formatted progress string */
        val progressText: String get() = "${session.progressPercent}%"

        /** Formatted size string */
        val sizeText: String get() = FileValidator.formatBytes(session.expectedSize)

        /** Formatted received bytes string */
        val receivedText: String get() = FileValidator.formatBytes(currentSize)
    }

    /**
     * Data class representing an orphaned MediaStore entry without a database record.
     * These are pending files in Movies/TravelCompanion that we created but lost track of.
     */
    data class OrphanedMediaStoreEntry(
        val mediaStoreId: Long,
        val contentUri: Uri,
        val displayName: String,
        val currentSize: Long
    ) {
        /** Formatted size string */
        val sizeText: String get() = FileValidator.formatBytes(currentSize)
    }

    /**
     * Detects all incomplete uploads by checking both the database and MediaStore.
     * 
     * @return List of incomplete uploads with their validation status
     */
    suspend fun detectIncompleteUploads(): List<IncompleteUpload> {
        val sessions = uploadSessionRepository.getIncompleteSessions()
        
        return sessions.map { session ->
            val uri = Uri.parse(session.mediaStoreUri)
            val (exists, currentSize) = checkMediaStoreEntry(uri)
            
            IncompleteUpload(
                session = session,
                mediaStoreExists = exists,
                currentSize = currentSize
            )
        }
    }

    /**
     * Checks if a MediaStore entry exists and returns its current size.
     * 
     * @param uri Content URI of the MediaStore entry
     * @return Pair of (exists, currentSize)
     */
    private fun checkMediaStoreEntry(uri: Uri): Pair<Boolean, Long> {
        return try {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.Video.Media.SIZE, MediaStore.Video.Media.IS_PENDING),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val size = cursor.getLong(0)
                    Pair(true, size)
                } else {
                    Pair(false, 0L)
                }
            } ?: Pair(false, 0L)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to check MediaStore entry: ${e.message}")
            Pair(false, 0L)
        }
    }

    /**
     * Detects orphaned MediaStore entries in Movies/TravelCompanion with IS_PENDING=1
     * that don't have a corresponding database session record.
     *
     * This catches cases where:
     * - The app crashed during session creation
     * - The database was cleared but MediaStore entries remain
     * - Any other scenario where we lost the database record
     *
     * Note: Only files created by this app instance can be seen (Android security model).
     * Files from previous app installations won't be visible.
     *
     * @return List of orphaned MediaStore entries that can be cleaned up
     */
    fun detectOrphanedMediaStoreEntries(): List<OrphanedMediaStoreEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // IS_PENDING not supported before Android Q
            return emptyList()
        }

        val orphaned = mutableListOf<OrphanedMediaStoreEntry>()

        try {
            // Query for pending videos in our TravelCompanion folder
            // Note: RELATIVE_PATH includes trailing slash in some Android versions
            val selection = "(${MediaStore.Video.Media.RELATIVE_PATH} = ? OR ${MediaStore.Video.Media.RELATIVE_PATH} = ?) AND ${MediaStore.Video.Media.IS_PENDING} = ?"
            val selectionArgs = arrayOf(
                MediaStoreUploader.RELATIVE_PATH,
                "${MediaStoreUploader.RELATIVE_PATH}/",
                "1"
            )

            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE
                ),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    orphaned.add(
                        OrphanedMediaStoreEntry(
                            mediaStoreId = id,
                            contentUri = contentUri,
                            displayName = cursor.getString(nameColumn) ?: "unknown",
                            currentSize = cursor.getLong(sizeColumn)
                        )
                    )
                }
            }

            android.util.Log.d(TAG, "Found ${orphaned.size} pending MediaStore entries in ${MediaStoreUploader.RELATIVE_PATH}")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to query orphaned MediaStore entries: ${e.message}")
        }

        return orphaned
    }

    /**
     * Detects orphaned MediaStore entries that have no corresponding database session.
     * This filters out entries that are already tracked in the database.
     *
     * @return List of truly orphaned entries (in MediaStore but not in DB)
     */
    suspend fun detectTrulyOrphanedMediaStoreEntries(): List<OrphanedMediaStoreEntry> {
        val allPending = detectOrphanedMediaStoreEntries()
        if (allPending.isEmpty()) return emptyList()

        // Get all known MediaStore URIs from the database
        val knownUris = uploadSessionRepository.getIncompleteSessions()
            .map { it.mediaStoreUri }
            .toSet()

        // Filter to only entries not tracked in the database
        return allPending.filter { entry ->
            entry.contentUri.toString() !in knownUris
        }
    }

    /**
     * Cleans up a single incomplete upload by deleting both the MediaStore entry
     * and the database session record.
     *
     * @param upload The incomplete upload to clean up
     * @return true if cleanup succeeded, false otherwise
     */
    suspend fun cleanupUpload(upload: IncompleteUpload): Boolean {
        return try {
            // Delete MediaStore entry if it exists
            if (upload.mediaStoreExists) {
                val uri = Uri.parse(upload.session.mediaStoreUri)
                contentResolver.delete(uri, null, null)
            }

            // Mark session as cancelled in database
            uploadSessionRepository.markCancelled(upload.session.id)

            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to cleanup upload: ${e.message}")
            false
        }
    }

    /**
     * Cleans up an orphaned MediaStore entry (one without a database record).
     *
     * @param entry The orphaned entry to clean up
     * @return true if cleanup succeeded, false otherwise
     */
    fun cleanupOrphanedEntry(entry: OrphanedMediaStoreEntry): Boolean {
        return try {
            val rowsDeleted = contentResolver.delete(entry.contentUri, null, null)
            if (rowsDeleted > 0) {
                android.util.Log.d(TAG, "Cleaned up orphaned MediaStore entry: ${entry.displayName}")
                true
            } else {
                android.util.Log.w(TAG, "Failed to delete orphaned entry (no rows affected): ${entry.displayName}")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to cleanup orphaned entry ${entry.displayName}: ${e.message}")
            false
        }
    }

    /**
     * Cleans up all incomplete uploads and orphaned MediaStore entries.
     *
     * @return Number of uploads successfully cleaned up (including orphaned entries)
     */
    suspend fun cleanupAllIncompleteUploads(): Int {
        var cleanedCount = 0

        // Clean up uploads with database records
        val incompleteUploads = detectIncompleteUploads()
        for (upload in incompleteUploads) {
            if (cleanupUpload(upload)) {
                cleanedCount++
            }
        }

        // Clean up orphaned MediaStore entries (no database record)
        val orphanedEntries = detectTrulyOrphanedMediaStoreEntries()
        for (entry in orphanedEntries) {
            if (cleanupOrphanedEntry(entry)) {
                cleanedCount++
            }
        }

        // Also clean up any finished sessions
        uploadSessionRepository.cleanupFinishedSessions()

        android.util.Log.d(TAG, "Cleaned up $cleanedCount incomplete/orphaned uploads")

        return cleanedCount
    }

    /**
     * Gets the total count of incomplete uploads (including orphaned MediaStore entries).
     */
    suspend fun getIncompleteCount(): Int {
        val dbCount = uploadSessionRepository.getIncompleteSessions().size
        val orphanedCount = detectTrulyOrphanedMediaStoreEntries().size
        return dbCount + orphanedCount
    }

    /**
     * Calculates total storage used by incomplete uploads (including orphaned entries).
     */
    suspend fun getIncompleteStorageUsed(): Long {
        val dbUploads = detectIncompleteUploads()
        val orphanedEntries = detectTrulyOrphanedMediaStoreEntries()

        return dbUploads.sumOf { it.currentSize } + orphanedEntries.sumOf { it.currentSize }
    }
}

