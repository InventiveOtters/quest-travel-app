package com.inotter.travelcompanion.data.datasources.videolibrary.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_settings")
data class PlaybackSettings(
    @PrimaryKey val id: Int = 1,
    val skipIntervalMs: Int = 10_000,
    val resumeEnabled: Boolean = true,
    val volume: Float = 0.5f,
)

