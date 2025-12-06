package com.inotter.travelcompanion.data.datasources.videolibrary.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a video file in the library.
 * De-duplicated by content signature (partial hash + size).
 *
 * @property id Auto-generated primary key
 * @property folderId Foreign key to the parent library folder (NULL for MediaStore-discovered)
 * @property fileUri SAF document URI or content:// URI for the video file
 * @property title Video title (from file name)
 * @property durationMs Video duration in milliseconds
 * @property sizeBytes File size in bytes
 * @property contentSignature Unique signature (first/last 8MiB hash + size)
 * @property createdAt Timestamp when first indexed (immutable on rescan)
 * @property lastPlayedAt Timestamp of last playback (epoch millis)
 * @property lastPositionMs Last playback position in milliseconds
 * @property unavailable True if file is missing during rescan
 * @property thumbnailPath Absolute path to cached thumbnail file
 * @property sourceType Source of discovery (SAF or MEDIASTORE)
 * @property mediaStoreId MediaStore._ID for tracking (null for SAF-discovered)
 */
@Entity(
    tableName = "video_items",
    foreignKeys = [
      ForeignKey(
        entity = LibraryFolder::class,
        parentColumns = ["id"],
        childColumns = ["folderId"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
    indices = [Index("folderId"), Index(value = ["contentSignature"], unique = true), Index("sourceType")],
)
data class VideoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val fileUri: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val contentSignature: String,
    val createdAt: Long,
    val lastPlayedAt: Long? = null,
    val lastPositionMs: Long? = null,
    val unavailable: Boolean = false,
    val thumbnailPath: String? = null,
    val sourceType: SourceType = SourceType.SAF,
    val mediaStoreId: Long? = null,
)

