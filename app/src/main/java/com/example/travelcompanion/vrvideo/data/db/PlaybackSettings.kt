package com.example.travelcompanion.vrvideo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_settings")
data class PlaybackSettings(
    @PrimaryKey val id: Int = 1,
    val defaultViewMode: StereoLayout = StereoLayout.TwoD,
    val skipIntervalMs: Int = 10_000,
    val resumeEnabled: Boolean = true,
)

