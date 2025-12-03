package com.inotter.travelcompanion.domain.scan

import android.net.Uri

/**
 * Represents a video discovered from MediaStore.
 * This is a transient data class used during scanning before conversion to VideoItem.
 *
 * @property mediaStoreId The unique ID from MediaStore (_ID column)
 * @property contentUri The content:// URI to access the video
 * @property displayName The file name displayed to users
 * @property filePath The absolute file path (DATA column, for signature computation)
 * @property sizeBytes File size in bytes
 * @property durationMs Video duration in milliseconds (0 if unknown)
 * @property dateAdded Timestamp when the video was added to MediaStore (epoch seconds)
 * @property mimeType The MIME type of the video (e.g., "video/mp4")
 */
data class ScannedVideo(
    val mediaStoreId: Long,
    val contentUri: Uri,
    val displayName: String,
    val filePath: String?,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val mimeType: String?,
) {
    /**
     * Checks if this video has a supported format.
     * Currently supports MP4 and MKV containers.
     */
    fun isSupported(): Boolean {
        val name = displayName.lowercase()
        return name.endsWith(".mp4") || name.endsWith(".mkv") ||
               mimeType == "video/mp4" || mimeType == "video/x-matroska"
    }
}

