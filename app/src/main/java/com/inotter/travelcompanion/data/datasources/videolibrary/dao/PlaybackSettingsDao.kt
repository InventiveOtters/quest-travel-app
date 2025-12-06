package com.inotter.travelcompanion.data.datasources.videolibrary.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inotter.travelcompanion.data.datasources.videolibrary.models.PlaybackSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackSettingsDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(settings: PlaybackSettings)

  @Query("SELECT * FROM playback_settings WHERE id = 1")
  fun getFlow(): Flow<PlaybackSettings?>

  @Query("SELECT * FROM playback_settings WHERE id = 1")
  suspend fun get(): PlaybackSettings?
}

