package com.inotter.travelcompanion.data.datasources.videolibrary.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSession
import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for UploadSession entity.
 * Provides CRUD operations for managing upload sessions used in resumable uploads.
 */
@Dao
interface UploadSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: UploadSession): Long

    @Update
    suspend fun update(session: UploadSession)

    @Query("SELECT * FROM upload_sessions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<UploadSession>>

    @Query("SELECT * FROM upload_sessions ORDER BY createdAt DESC")
    suspend fun getAllSync(): List<UploadSession>

    @Query("SELECT * FROM upload_sessions WHERE id = :id")
    suspend fun getById(id: Long): UploadSession?

    @Query("SELECT * FROM upload_sessions WHERE mediaStoreUri = :uri")
    suspend fun getByMediaStoreUri(uri: String): UploadSession?

    @Query("SELECT * FROM upload_sessions WHERE status = 'IN_PROGRESS' ORDER BY createdAt DESC")
    suspend fun getIncomplete(): List<UploadSession>

    @Query("SELECT * FROM upload_sessions WHERE status = 'IN_PROGRESS' ORDER BY createdAt DESC")
    fun getIncompleteFlow(): Flow<List<UploadSession>>

    @Query("UPDATE upload_sessions SET bytesReceived = :bytes, lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_sessions SET status = 'COMPLETED', lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun markCompleted(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_sessions SET status = 'FAILED', lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun markFailed(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE upload_sessions SET status = 'CANCELLED', lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun markCancelled(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM upload_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM upload_sessions WHERE status = :status")
    suspend fun deleteByStatus(status: UploadSessionStatus)

    @Query("DELETE FROM upload_sessions WHERE createdAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM upload_sessions WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED')")
    suspend fun deleteFinished()

    @Query("SELECT COUNT(*) FROM upload_sessions WHERE status = 'IN_PROGRESS'")
    suspend fun getIncompleteCount(): Int

    @Query("SELECT COUNT(*) FROM upload_sessions WHERE status = 'IN_PROGRESS'")
    fun getIncompleteCountFlow(): Flow<Int>
}

