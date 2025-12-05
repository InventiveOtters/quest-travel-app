package com.inotter.travelcompanion.data.repositories.UploadSessionRepository

import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSession
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing TUS resumable upload sessions.
 * Provides operations to create, update, and cleanup upload sessions.
 *
 * This repository integrates with tus-java-server's UploadStorageService
 * by providing TUS-specific lookup methods (by uploadUrl, tusUploadId).
 */
interface UploadSessionRepository {
    /**
     * Creates a new TUS upload session when an upload starts.
     *
     * @param tusUploadId Unique TUS upload ID (UUID)
     * @param uploadUrl Full TUS upload URL for resume
     * @param filename Original filename being uploaded
     * @param expectedSize Total expected file size in bytes
     * @param mediaStoreUri Content URI of the pending MediaStore entry
     * @param mimeType MIME type of the file
     * @return The ID of the created session
     */
    suspend fun createSession(
        tusUploadId: String,
        uploadUrl: String,
        filename: String,
        expectedSize: Long,
        mediaStoreUri: String,
        mimeType: String
    ): Long

    /**
     * Legacy method for backwards compatibility - creates session without TUS fields.
     * @deprecated Use createSession with TUS fields instead
     */
    @Deprecated("Use createSession with TUS fields", ReplaceWith("createSession(tusUploadId, uploadUrl, filename, expectedSize, mediaStoreUri, mimeType)"))
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
     * Updates the progress by TUS upload ID (used by MediaStoreUploadStorageService).
     *
     * @param tusUploadId TUS upload ID
     * @param bytesReceived Number of bytes received so far
     */
    suspend fun updateProgressByTusId(tusUploadId: String, bytesReceived: Long)

    /**
     * Marks an upload session as completed successfully.
     *
     * @param sessionId ID of the upload session
     */
    suspend fun markCompleted(sessionId: Long)

    /**
     * Marks an upload session as completed by TUS upload ID.
     *
     * @param tusUploadId TUS upload ID
     */
    suspend fun markCompletedByTusId(tusUploadId: String)

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
     * Gets an upload session by TUS upload URL (used for resume lookups).
     */
    suspend fun getByUploadUrl(uploadUrl: String): UploadSession?

    /**
     * Gets an upload session by TUS upload ID.
     */
    suspend fun getByTusId(tusUploadId: String): UploadSession?

    /**
     * Deletes an upload session by ID.
     */
    suspend fun deleteSession(sessionId: Long)

    /**
     * Deletes an upload session by TUS upload ID.
     */
    suspend fun deleteByTusId(tusUploadId: String)

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
     * Deletes expired TUS sessions (older than 24 hours and still in progress).
     * @return Number of sessions deleted
     */
    suspend fun cleanupExpiredSessions(): Int

    /**
     * Gets all upload sessions.
     */
    fun getAllSessions(): Flow<List<UploadSession>>

    /**
     * Gets all upload sessions synchronously.
     */
    suspend fun getAllSessionsSync(): List<UploadSession>
}

