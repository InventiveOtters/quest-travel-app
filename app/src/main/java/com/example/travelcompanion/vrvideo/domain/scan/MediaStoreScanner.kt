package com.example.travelcompanion.vrvideo.domain.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

/**
 * Scans videos from MediaStore using READ_MEDIA_VIDEO permission.
 * Provides efficient querying of all videos on the device without SAF folder selection.
 *
 * @property context Android application context
 */
class MediaStoreScanner(private val context: Context) {

    private val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATA,           // File path for signature
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.MIME_TYPE,
    )

    /**
     * Scans all videos available in MediaStore.
     * Filters to only supported formats (MP4, MKV).
     *
     * @return List of discovered videos, sorted by date added (newest first)
     */
    suspend fun scanAllVideos(): List<ScannedVideo> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<ScannedVideo>()

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Video.Media.MIME_TYPE} LIKE ?",
            arrayOf("video/%"),
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                val scannedVideo = ScannedVideo(
                    mediaStoreId = id,
                    contentUri = contentUri,
                    displayName = cursor.getString(nameColumn) ?: "video_$id",
                    filePath = cursor.getString(dataColumn),
                    sizeBytes = cursor.getLong(sizeColumn),
                    durationMs = cursor.getLong(durationColumn),
                    dateAdded = cursor.getLong(dateColumn),
                    mimeType = cursor.getString(mimeColumn),
                )
                
                // Only add supported formats
                if (scannedVideo.isSupported()) {
                    videos.add(scannedVideo)
                }
            }
        }

        videos
    }

    /**
     * Computes a content signature for a video using first/last 8MiB segments + size.
     * This matches the signature format used by IndexWorker for SAF videos.
     *
     * @param uri Content URI of the video
     * @param size Size of the video in bytes
     * @return Content signature string in format "hash:size"
     */
    suspend fun computeSignature(uri: Uri, size: Long): String = withContext(Dispatchers.IO) {
        val chunk = 8L * 1024L * 1024L // 8 MiB
        val first = readSegment(uri, 0L, minOf(size, chunk))
        val lastStart = if (size > chunk) size - chunk else 0L
        val last = if (lastStart > 0L) readSegment(uri, lastStart, size - lastStart) else byteArrayOf()
        
        val md = MessageDigest.getInstance("SHA-256")
        md.update(first)
        md.update(last)
        val hash = md.digest().joinToString("") { b -> "%02x".format(b) }
        "$hash:$size"
    }

    private fun readSegment(uri: Uri, start: Long, length: Long): ByteArray {
        context.contentResolver.openInputStream(uri)?.use { input ->
            skipFully(input, start)
            val buf = ByteArray(length.toInt())
            var total = 0
            while (total < buf.size) {
                val read = input.read(buf, total, buf.size - total)
                if (read <= 0) break
                total += read
            }
            return if (total == buf.size) buf else buf.copyOf(total)
        }
        return byteArrayOf()
    }

    private fun skipFully(input: InputStream, toSkip: Long) {
        var remaining = toSkip
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }
}

