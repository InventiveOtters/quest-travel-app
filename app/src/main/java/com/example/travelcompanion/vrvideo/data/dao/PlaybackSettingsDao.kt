package com.example.travelcompanion.vrvideo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.travelcompanion.vrvideo.data.db.PlaybackSettings
import com.example.travelcompanion.vrvideo.data.db.StereoLayout
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackSettingsDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(settings: PlaybackSettings)

  @Query("SELECT * FROM playback_settings WHERE id = 1")
  fun getFlow(): Flow<PlaybackSettings?>

  @Query("SELECT * FROM playback_settings WHERE id = 1")
  suspend fun get(): PlaybackSettings?

  @Query("UPDATE playback_settings SET defaultViewMode = :mode, skipIntervalMs = :skip, resumeEnabled = :resume WHERE id = 1")
  suspend fun update(mode: StereoLayout, skip: Int, resume: Boolean)
}

