package com.example.travelcompanion.vrvideo.domain.thumb

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Generates thumbnail images for video files.
 * Creates ~320px wide JPEG thumbnails and caches them to disk.
 */
object ThumbnailGenerator {
  /**
   * Generates a thumbnail for a video file.
   * Extracts a frame from the video, scales it to ~320px width, and saves it as a JPEG.
   *
   * @param context Android application context
   * @param videoUri URI of the video file
   * @param fileKey Unique key for the thumbnail file name
   * @return Absolute path to the generated thumbnail file, or null if generation fails
   */
  fun generate(context: Context, videoUri: Uri, fileKey: String): String? {
    val mmr = MediaMetadataRetriever()
    return try {
      mmr.setDataSource(context, videoUri)
      val frame = mmr.getFrameAtTime(0)
      val bmp = frame ?: return null
      val scale = 320.0 / bmp.width.coerceAtLeast(1)
      val w = (bmp.width * scale).toInt().coerceAtLeast(1)
      val h = (bmp.height * scale).toInt().coerceAtLeast(1)
      val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
      val outFile = File(context.cacheDir, "thumb_${'$'}fileKey.jpg")
      FileOutputStream(outFile).use { fos ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, fos)
      }
      outFile.absolutePath
    } catch (_: Throwable) {
      null
    } finally {
      mmr.release()
    }
  }
}

