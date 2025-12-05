package com.inotter.travelcompanion.workers

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSession
import com.inotter.travelcompanion.data.repositories.UploadSessionRepository.UploadSessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WorkManager worker that performs periodic cleanup of expired TUS upload sessions.
 *
 * This worker:
 * 1. Finds all expired upload sessions (>24 hours old, still in progress)
 * 2. Deletes their associated MediaStore entries (pending videos)
 * 3. Cleans up TUS temporary files from cache directory
 * 4. Removes the session records from the database
 *
 * Should be scheduled to run periodically (e.g., every 6 hours) or on app startup.
 *
 * @param appContext Android application context
 * @param params Worker parameters
 * @param uploadSessionRepository Repository for managing upload sessions
 * @param contentResolver Content resolver for MediaStore operations
 */
@HiltWorker
class UploadCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val uploadSessionRepository: UploadSessionRepository,
    private val contentResolver: ContentResolver
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting upload cleanup...")

        try {
            var cleanedSessions = 0
            var cleanedFiles = 0
            var cleanedMediaStore = 0

            // Get expired sessions before deleting them
            val expiredSessions = getExpiredSessions()
            Log.d(TAG, "Found ${expiredSessions.size} expired sessions")

            // Clean up each expired session
            for (session in expiredSessions) {
                // 1. Clean up MediaStore pending entry
                if (cleanupMediaStoreEntry(session.mediaStoreUri)) {
                    cleanedMediaStore++
                }

                // 2. Clean up TUS temp files
                if (cleanupTusFiles(session.tusUploadId)) {
                    cleanedFiles++
                }
            }

            // 3. Delete expired sessions from database
            cleanedSessions = uploadSessionRepository.cleanupExpiredSessions()
            Log.d(TAG, "Deleted $cleanedSessions expired sessions from database")

            // 4. Also clean up any orphaned TUS temp files (files without session records)
            val orphanedFiles = cleanupOrphanedTusFiles()
            cleanedFiles += orphanedFiles

            // 5. Clean up finished sessions (completed, cancelled, failed)
            uploadSessionRepository.cleanupFinishedSessions()

            Log.i(TAG, "Cleanup complete: $cleanedSessions sessions, $cleanedMediaStore MediaStore entries, $cleanedFiles TUS files")

            Result.success(
                Data.Builder()
                    .putInt(KEY_SESSIONS_CLEANED, cleanedSessions)
                    .putInt(KEY_MEDIASTORE_CLEANED, cleanedMediaStore)
                    .putInt(KEY_FILES_CLEANED, cleanedFiles)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed: ${e.message}", e)
            Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR, e.message)
                    .build()
            )
        }
    }

    /**
     * Gets all expired upload sessions (>24h old and still in progress).
     */
    private suspend fun getExpiredSessions(): List<UploadSession> {
        return uploadSessionRepository.getIncompleteSessions().filter { it.isExpired() }
    }

    /**
     * Deletes a pending MediaStore entry.
     */
    private fun cleanupMediaStoreEntry(mediaStoreUri: String): Boolean {
        return try {
            val uri = Uri.parse(mediaStoreUri)
            val deleted = contentResolver.delete(uri, null, null) > 0
            if (deleted) {
                Log.d(TAG, "Deleted MediaStore entry: $mediaStoreUri")
            }
            deleted
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete MediaStore entry: $mediaStoreUri - ${e.message}")
            false
        }
    }

    /**
     * Cleans up TUS temporary files for a given upload ID.
     * TUS files are stored in cache/tus/ directory with the upload ID as filename.
     */
    private fun cleanupTusFiles(tusUploadId: String): Boolean {
        val tusDir = File(applicationContext.cacheDir, "tus")
        if (!tusDir.exists()) return false

        var deleted = false
        // TUS library stores files with the upload ID as the base name
        // Look for both data and info files
        tusDir.listFiles()?.filter { file ->
            file.name.startsWith(tusUploadId) || file.name.contains(tusUploadId)
        }?.forEach { file ->
            if (file.delete()) {
                Log.d(TAG, "Deleted TUS file: ${file.name}")
                deleted = true
            }
        }
        return deleted
    }

    /**
     * Cleans up orphaned TUS files (files older than 24h without matching session records).
     */
    private suspend fun cleanupOrphanedTusFiles(): Int {
        val tusDir = File(applicationContext.cacheDir, "tus")
        if (!tusDir.exists()) return 0

        val cutoffTime = System.currentTimeMillis() - UploadSession.EXPIRATION_MILLIS
        var cleaned = 0

        tusDir.listFiles()?.filter { file ->
            file.lastModified() < cutoffTime
        }?.forEach { file ->
            if (file.delete()) {
                Log.d(TAG, "Deleted orphaned TUS file: ${file.name}")
                cleaned++
            }
        }
        return cleaned
    }

    companion object {
        private const val TAG = "UploadCleanupWorker"
        const val WORK_NAME = "upload_cleanup"
        const val KEY_SESSIONS_CLEANED = "sessions_cleaned"
        const val KEY_MEDIASTORE_CLEANED = "mediastore_cleaned"
        const val KEY_FILES_CLEANED = "files_cleaned"
        const val KEY_ERROR = "error"
    }
}

