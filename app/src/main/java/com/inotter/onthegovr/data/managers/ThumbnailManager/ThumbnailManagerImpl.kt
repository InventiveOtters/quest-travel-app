package com.inotter.onthegovr.data.managers.ThumbnailManager

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ThumbnailManager] that generates thumbnail images for video files.
 *
 * @property context Android application context
 */
@Singleton
class ThumbnailManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ThumbnailManager {

    companion object {
        // Extract frame at 10% of duration, with min 10s and max 60s offset
        // This approach is similar to VLC/Kodi which use 10-30% of duration
        private const val THUMBNAIL_POSITION_PERCENT = 0.10
        private const val MIN_OFFSET_MS = 10_000L
        private const val MAX_OFFSET_MS = 60_000L
    }

    override fun generate(videoUri: Uri, fileKey: String): VideoMetadataResult {
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

