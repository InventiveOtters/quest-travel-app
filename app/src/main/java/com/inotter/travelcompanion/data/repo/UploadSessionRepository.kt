package com.inotter.travelcompanion.data.repo

import com.inotter.travelcompanion.data.db.UploadSession
import com.inotter.travelcompanion.data.db.VideoLibraryDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing upload sessions used in resumable uploads.
 * Provides operations to create, update, and cleanup upload sessions.
 *
 * @property db Video library database instance
 */
class UploadSessionRepository(
    private val db: VideoLibraryDatabase
) {
    private val dao = db.uploadSessionDao()

    /**
     * Creates a new upload session when an upload starts.
     *
     * @param filename Original filename being uploaded
     * @param expectedSize Total expected file size in bytes
     * @param mediaStoreUri Content URI of the pending MediaStore entry
     * @param mimeType MIME type of the file
     * @return The ID of the created session
     */
    suspend fun createSession(
        filename: String,
        expectedSize: Long,
        mediaStoreUri: String,
        mimeType: String
    ): Long {
        val session = UploadSession(
            filename = filename,
            expectedSize = expectedSize,
            mediaStoreUri = mediaStoreUri,
            mimeType = mimeType
        )
        return dao.insert(session)
    }

    /**
     * Updates the progress of an upload session.
     *
     * @param sessionId ID of the upload session
     * @param bytesReceived Number of bytes received so far
     */
    suspend fun updateProgress(sessionId: Long, bytesReceived: Long) {
        dao.updateProgress(sessionId, bytesReceived)
    }

    /**
     * Marks an upload session as completed successfully.
     *
     * @param sessionId ID of the upload session
     */
    suspend fun markCompleted(sessionId: Long) {
        dao.markCompleted(sessionId)
    }

    /**
     * Marks an upload session as failed.
     *
     * @param sessionId ID of the upload session
     */
    suspend fun markFailed(sessionId: Long) {
        dao.markFailed(sessionId)
    }

    /**
     * Marks an upload session as cancelled.
     *
     * @param sessionId ID of the upload session
     */
    suspend fun markCancelled(sessionId: Long) {
        dao.markCancelled(sessionId)
    }

    /**
     * Gets all incomplete (in-progress) upload sessions.
     */
    suspend fun getIncompleteSessions(): List<UploadSession> {
        return dao.getIncomplete()
    }

    /**
     * Gets incomplete upload sessions as a Flow for observing changes.
     */
    fun getIncompleteSessionsFlow(): Flow<List<UploadSession>> {
        return dao.getIncompleteFlow()
    }

    /**
     * Gets the count of incomplete uploads as a Flow.
     */
    fun getIncompleteCountFlow(): Flow<Int> {
        return dao.getIncompleteCountFlow()
    }

    /**
     * Gets an upload session by its MediaStore URI.
     */
    suspend fun getByMediaStoreUri(uri: String): UploadSession? {
        return dao.getByMediaStoreUri(uri)
    }

    /**
     * Gets an upload session by its ID.
     */
    suspend fun getById(id: Long): UploadSession? {
        return dao.getById(id)
    }

    /**
     * Deletes an upload session by ID.
     */
    suspend fun deleteSession(sessionId: Long) {
        dao.deleteById(sessionId)
    }

    /**
     * Deletes all completed and cancelled sessions (cleanup).
     */
    suspend fun cleanupFinishedSessions() {
        dao.deleteFinished()
    }

    /**
     * Deletes all sessions older than the specified age.
     *
     * @param maxAgeMillis Maximum age in milliseconds (default: 7 days)
     */
    suspend fun cleanupOldSessions(maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        dao.deleteOlderThan(cutoff)
    }

    /**
     * Gets all upload sessions.
     */
    fun getAllSessions(): Flow<List<UploadSession>> {
        return dao.getAll()
    }

    /**
     * Gets all upload sessions synchronously.
     */
    suspend fun getAllSessionsSync(): List<UploadSession> {
        return dao.getAllSync()
    }
}

