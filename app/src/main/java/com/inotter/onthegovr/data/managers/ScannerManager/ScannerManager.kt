package com.inotter.onthegovr.data.managers.ScannerManager

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

/**
 * Manager interface for scanning videos from MediaStore.
 * Provides efficient querying of all videos on the device using READ_MEDIA_VIDEO permission.
 */
interface ScannerManager {

    /**
     * Scans all videos available in MediaStore.
     * Filters to only supported formats (MP4, MKV).
     *
     * @return List of discovered videos, sorted by date added (newest first)
     */
    suspend fun scanAllVideos(): List<ScannedVideo>

    /**
     * Computes a content signature for a video using first/last 8MiB segments + size.
     * This matches the signature format used by IndexWorker for SAF videos.
     *
     * @param uri Content URI of the video
     * @param size Size of the video in bytes
     * @return Content signature string in format "hash:size"
     */
    suspend fun computeSignature(uri: Uri, size: Long): String
}

