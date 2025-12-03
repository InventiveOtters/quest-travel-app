package com.inotter.travelcompanion.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inotter.travelcompanion.data.db.Thumbnail

@Dao
interface ThumbnailDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(thumb: Thumbnail)

  @Query("SELECT * FROM thumbnails WHERE videoId = :videoId")
  suspend fun get(videoId: Long): Thumbnail?
}

