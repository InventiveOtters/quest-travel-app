package com.inotter.onthegovr.data.managers.ThumbnailManager

import android.net.Uri

/**
 * Result of video metadata extraction and thumbnail generation.
 *
 * @property thumbnailPath Absolute path to the generated thumbnail file, or null if generation fails
 * @property durationMs Video duration in milliseconds, or 0 if extraction fails
 */
data class VideoMetadataResult(
    val thumbnailPath: String?,
    val durationMs: Long,
)

/**
 * Manager interface for generating thumbnail images for video files and extracting metadata.
 * Creates ~320px wide JPEG thumbnails and caches them to disk.
 */
interface ThumbnailManager {

    /**
     * Generates a thumbnail for a video file and extracts duration.
     * Extracts a frame at ~10% into the video (similar to VLC/Kodi), scales it to ~320px width,
     * and saves it as a JPEG.
     *
     * @param videoUri URI of the video file
     * @param fileKey Unique key for the thumbnail file name
     * @return VideoMetadataResult containing thumbnail path and duration
     */
    fun generate(videoUri: Uri, fileKey: String): VideoMetadataResult
}

