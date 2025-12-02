package com.example.travelcompanion.vrvideo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.travelcompanion.vrvideo.data.db.SourceType
import com.example.travelcompanion.vrvideo.data.db.StereoLayout
import com.example.travelcompanion.vrvideo.data.db.VideoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoItemDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrReplace(item: VideoItem): Long

  @Query("SELECT * FROM video_items ORDER BY createdAt DESC")
  fun getAll(): Flow<List<VideoItem>>

  @Query("DELETE FROM video_items WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("SELECT * FROM video_items WHERE contentSignature = :sig LIMIT 1")
  suspend fun findBySignature(sig: String): VideoItem?

  @Query("UPDATE video_items SET stereoLayoutOverride = :layout WHERE id = :id")
  suspend fun setStereoLayoutOverride(id: Long, layout: StereoLayout?)

  @Query("UPDATE video_items SET lastPlayedAt = :lastPlayedAt, lastPositionMs = :lastPositionMs WHERE id = :id")
  suspend fun updatePlaybackProgress(id: Long, lastPlayedAt: Long?, lastPositionMs: Long?)

  @Query("UPDATE video_items SET unavailable = :flag WHERE id IN (:ids)")
  suspend fun markUnavailable(ids: List<Long>, flag: Boolean = true)

  @Query("SELECT * FROM video_items WHERE folderId = :folderId")
  suspend fun getByFolderId(folderId: Long): List<VideoItem>

  // MediaStore-specific queries

  @Query("SELECT * FROM video_items WHERE sourceType = :sourceType ORDER BY createdAt DESC")
  fun getBySourceType(sourceType: SourceType): Flow<List<VideoItem>>

  @Query("SELECT * FROM video_items WHERE sourceType = :sourceType")
  suspend fun getBySourceTypeSync(sourceType: SourceType): List<VideoItem>

  @Query("SELECT * FROM video_items WHERE mediaStoreId = :mediaStoreId LIMIT 1")
  suspend fun findByMediaStoreId(mediaStoreId: Long): VideoItem?

  @Query("DELETE FROM video_items WHERE sourceType = :sourceType")
  suspend fun deleteBySourceType(sourceType: SourceType)

  @Query("SELECT mediaStoreId FROM video_items WHERE sourceType = 'MEDIASTORE' AND mediaStoreId IS NOT NULL")
  suspend fun getAllMediaStoreIds(): List<Long>
}

