package com.inotter.travelcompanion.domain.thumb

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

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
 * Generates thumbnail images for video files and extracts metadata.
 * Creates ~320px wide JPEG thumbnails and caches them to disk.
 */
object ThumbnailGenerator {
  // Extract frame at 10% of duration, with min 10s and max 60s offset
  // This approach is similar to VLC/Kodi which use 10-30% of duration
  private const val THUMBNAIL_POSITION_PERCENT = 0.10
  private const val MIN_OFFSET_MS = 10_000L
  private const val MAX_OFFSET_MS = 60_000L

  /**
   * Generates a thumbnail for a video file and extracts duration.
   * Extracts a frame at ~10% into the video (similar to VLC/Kodi), scales it to ~320px width,
   * and saves it as a JPEG.
   *
   * @param context Android application context
   * @param videoUri URI of the video file
   * @param fileKey Unique key for the thumbnail file name
   * @return VideoMetadataResult containing thumbnail path and duration
   */
  fun generate(context: Context, videoUri: Uri, fileKey: String): VideoMetadataResult {
    val mmr = MediaMetadataRetriever()
    return try {
      mmr.setDataSource(context, videoUri)

      // Extract duration
      val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
          ?.toLongOrNull() ?: 0L

      // Calculate thumbnail position: 10% of duration, clamped between 10s and 60s
      // For short videos (< 10s), use 10% of duration
      val thumbnailTimeUs = if (durationMs < MIN_OFFSET_MS) {
        (durationMs * THUMBNAIL_POSITION_PERCENT * 1000).toLong() // Convert to microseconds
      } else {
        val offsetMs = (durationMs * THUMBNAIL_POSITION_PERCENT).toLong()
            .coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
        offsetMs * 1000 // Convert to microseconds
      }

      // Extract frame at calculated position, fallback to frame 0 if it fails
      val frame = mmr.getFrameAtTime(thumbnailTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
          ?: mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

      val thumbnailPath = frame?.let { bmp ->
        val scale = 320.0 / bmp.width.coerceAtLeast(1)
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        val outFile = File(context.cacheDir, "thumb_$fileKey.jpg")
        FileOutputStream(outFile).use { fos ->
          scaled.compress(Bitmap.CompressFormat.JPEG, 80, fos)
        }
        outFile.absolutePath
      }

      VideoMetadataResult(thumbnailPath, durationMs)
    } catch (_: Throwable) {
      VideoMetadataResult(null, 0L)
    } finally {
      mmr.release()
    }
  }
}

