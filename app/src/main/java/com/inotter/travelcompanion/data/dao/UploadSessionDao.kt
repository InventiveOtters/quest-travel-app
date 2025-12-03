package com.inotter.travelcompanion.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.inotter.travelcompanion.data.db.UploadSession
import com.inotter.travelcompanion.data.db.UploadSessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for UploadSession entity.
 * Provides CRUD operations for managing upload sessions used in resumable uploads.
 */
@Dao
interface UploadSessionDao {
    /**
     * Inserts a new upload session.
     * @return The ID of the inserted session
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: UploadSession): Long

    /**
     * Updates an existing upload session.
     */
    @Update
    suspend fun update(session: UploadSession)

    /**
     * Gets all upload sessions as a Flow.
     */
    @Query("SELECT * FROM upload_sessions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<UploadSession>>

    /**
     * Gets all upload sessions synchronously.
     */
    @Query("SELECT * FROM upload_sessions ORDER BY createdAt DESC")
    suspend fun getAllSync(): List<UploadSession>

    /**
     * Gets an upload session by its ID.
     */
    @Query("SELECT * FROM upload_sessions WHERE id = :id")
    suspend fun getById(id: Long): UploadSession?

    /**
     * Gets an upload session by its MediaStore URI.
     */
    @Query("SELECT * FROM upload_sessions WHERE mediaStoreUri = :uri")
    suspend fun getByMediaStoreUri(uri: String): UploadSession?

    /**
     * Gets all incomplete (in-progress) upload sessions.
     */
    @Query("SELECT * FROM upload_sessions WHERE status = 'IN_PROGRESS' ORDER BY createdAt DESC")
    suspend fun getIncomplete(): List<UploadSession>

    /**
     * Gets incomplete upload sessions as a Flow for observing changes.
     */
    @Query("SELECT * FROM upload_sessions WHERE status = 'IN_PROGRESS' ORDER BY createdAt DESC")
    fun getIncompleteFlow(): Flow<List<UploadSession>>

    /**
     * Updates the bytes received for an upload session.
     */
    @Query("UPDATE upload_sessions SET bytesReceived = :bytes, lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Marks an upload session as completed.
     */
    @Query("UPDATE upload_sessions SET status = 'COMPLETED', lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun markCompleted(id: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Marks an upload session as failed.
     */
    @Query("UPDATE upload_sessions SET status = 'FAILED', lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun markFailed(id: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Marks an upload session as cancelled.
     */
    @Query("UPDATE upload_sessions SET status = 'CANCELLED', lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun markCancelled(id: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Deletes an upload session by ID.
     */
    @Query("DELETE FROM upload_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Deletes upload sessions by status.
     */
    @Query("DELETE FROM upload_sessions WHERE status = :status")
    suspend fun deleteByStatus(status: UploadSessionStatus)

    /**
     * Deletes all upload sessions older than the specified timestamp.
     */
    @Query("DELETE FROM upload_sessions WHERE createdAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Deletes all completed and cancelled sessions (cleanup).
     */
    @Query("DELETE FROM upload_sessions WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED')")
    suspend fun deleteFinished()

    /**
     * Gets count of incomplete uploads.
     */
    @Query("SELECT COUNT(*) FROM upload_sessions WHERE status = 'IN_PROGRESS'")
    suspend fun getIncompleteCount(): Int

    /**
     * Gets count of incomplete uploads as a Flow.
     */
    @Query("SELECT COUNT(*) FROM upload_sessions WHERE status = 'IN_PROGRESS'")
    fun getIncompleteCountFlow(): Flow<Int>
}

