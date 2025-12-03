package com.inotter.travelcompanion.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "thumbnails",
    foreignKeys = [
      ForeignKey(
        entity = VideoItem::class,
        parentColumns = ["id"],
        childColumns = ["videoId"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
    indices = [Index(value = ["videoId"], unique = true)],
)
data class Thumbnail(
    @PrimaryKey val videoId: Long,
    val generationStatus: ThumbnailGenerationStatus = ThumbnailGenerationStatus.Pending,
    val lastGeneratedAt: Long? = null,
)

