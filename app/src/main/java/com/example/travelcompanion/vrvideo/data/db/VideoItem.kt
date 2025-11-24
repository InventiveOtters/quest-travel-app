package com.example.travelcompanion.vrvideo.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a video file in the library.
 * De-duplicated by content signature (partial hash + size).
 *
 * @property id Auto-generated primary key
 * @property folderId Foreign key to the parent library folder
 * @property fileUri SAF document URI for the video file
 * @property title Video title (from file name)
 * @property durationMs Video duration in milliseconds
 * @property sizeBytes File size in bytes
 * @property contentSignature Unique signature (first/last 8MiB hash + size)
 * @property createdAt Timestamp when first indexed (immutable on rescan)
 * @property lastPlayedAt Timestamp of last playback (epoch millis)
 * @property lastPositionMs Last playback position in milliseconds
 * @property stereoLayout Detected stereo layout from metadata/filename
 * @property stereoLayoutOverride User-specified stereo layout override
 * @property unavailable True if file is missing during rescan
 * @property thumbnailPath Absolute path to cached thumbnail file
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
    indices = [Index("folderId"), Index(value = ["contentSignature"], unique = true)],
)
data class VideoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val fileUri: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val contentSignature: String,
    val createdAt: Long,
    val lastPlayedAt: Long? = null,
    val lastPositionMs: Long? = null,
    val stereoLayout: StereoLayout = StereoLayout.Unknown,
    val stereoLayoutOverride: StereoLayout? = null,
    val unavailable: Boolean = false,
    val thumbnailPath: String? = null,
)

