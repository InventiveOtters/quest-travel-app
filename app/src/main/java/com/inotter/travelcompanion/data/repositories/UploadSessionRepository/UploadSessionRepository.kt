package com.inotter.travelcompanion.data.repositories.UploadSessionRepository

import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSession
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing upload sessions used in resumable uploads.
 * Provides operations to create, update, and cleanup upload sessions.
 */
interface UploadSessionRepository {
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
    ): Long

    /**
     * Updates the progress of an upload session.
     *
     * @param sessionId ID of the upload session
     * @param bytesReceived Number of bytes received so far
     */
    suspend fun updateProgress(sessionId: Long, bytesReceived: Long)

    /**
     * Marks an upload session as completed successfully.
     *
     * @param sessionId ID of the upload session
     */
    suspend fun markCompleted(sessionId: Long)

    /**
     * Marks an upload session as failed.
     *
     * @param sessionId ID of the upload session
     */
    suspend fun markFailed(sessionId: Long)

    /**
     * Marks an upload session as cancelled.
     *
     * @param sessionId ID of the upload session
     */
    suspend fun markCancelled(sessionId: Long)

    /**
     * Gets all incomplete (in-progress) upload sessions.
     */
    suspend fun getIncompleteSessions(): List<UploadSession>

    /**
     * Gets incomplete upload sessions as a Flow for observing changes.
     */
    fun getIncompleteSessionsFlow(): Flow<List<UploadSession>>

    /**
     * Gets the count of incomplete uploads as a Flow.
     */
    fun getIncompleteCountFlow(): Flow<Int>

    /**
     * Gets an upload session by its MediaStore URI.
     */
    suspend fun getByMediaStoreUri(uri: String): UploadSession?

    /**
     * Gets an upload session by its ID.
     */
    suspend fun getById(id: Long): UploadSession?

    /**
     * Deletes an upload session by ID.
     */
    suspend fun deleteSession(sessionId: Long)

    /**
     * Deletes all completed and cancelled sessions (cleanup).
     */
    suspend fun cleanupFinishedSessions()

    /**
     * Deletes all sessions older than the specified age.
     *
     * @param maxAgeMillis Maximum age in milliseconds (default: 7 days)
     */
    suspend fun cleanupOldSessions(maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L)

    /**
     * Gets all upload sessions.
     */
    fun getAllSessions(): Flow<List<UploadSession>>

    /**
     * Gets all upload sessions synchronously.
     */
    suspend fun getAllSessionsSync(): List<UploadSession>
}

