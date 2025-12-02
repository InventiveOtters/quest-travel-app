package com.example.travelcompanion.vrvideo.domain.scan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.travelcompanion.vrvideo.data.db.SourceType
import com.example.travelcompanion.vrvideo.data.db.VideoItem
import com.example.travelcompanion.vrvideo.data.db.VideoLibraryDatabase
import com.example.travelcompanion.vrvideo.domain.thumb.ThumbnailGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that scans videos from MediaStore.
 * Uses READ_MEDIA_VIDEO permission to discover all device videos.
 * Integrates with existing ThumbnailGenerator and deduplication logic.
 *
 * @param appContext Android application context
 * @param params Worker parameters
 */
class MediaStoreScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = VideoLibraryDatabase.getInstance(applicationContext)
        val dao = db.videoItemDao()
        val scanSettingsDao = db.scanSettingsDao()
        val scanner = MediaStoreScanner(applicationContext)
        val now = System.currentTimeMillis()

        try {
            // Scan all videos from MediaStore
            val scannedVideos = scanner.scanAllVideos()
            
            // Track which MediaStore IDs we found during this scan
            val foundMediaStoreIds = mutableSetOf<Long>()
            var processedCount = 0

            for (video in scannedVideos) {
                foundMediaStoreIds.add(video.mediaStoreId)

                // Compute content signature for deduplication
                val signature = scanner.computeSignature(video.contentUri, video.sizeBytes)

                // Check for existing video with same signature (deduplication)
                val existingBySignature = dao.findBySignature(signature)
                if (existingBySignature != null) {
                    // Video already exists (possibly from SAF scan), skip it
                    // Update mediaStoreId if this was originally SAF-discovered
                    if (existingBySignature.mediaStoreId == null) {
                        val updated = existingBySignature.copy(
                            mediaStoreId = video.mediaStoreId
                        )
                        dao.insertOrReplace(updated)
                    }
                    processedCount++
                    continue
                }

                // Check if we already have this MediaStore ID
                val existingByMediaStoreId = dao.findByMediaStoreId(video.mediaStoreId)
                if (existingByMediaStoreId != null) {
                    // Update existing entry if file changed
                    if (existingByMediaStoreId.contentSignature != signature) {
                        val (thumbnailPath, extractedDuration) = ThumbnailGenerator.generate(
                            applicationContext,
                            video.contentUri,
                            signature.take(16)
                        )
                        val updated = existingByMediaStoreId.copy(
                            fileUri = video.contentUri.toString(),
                            title = video.displayName,
                            sizeBytes = video.sizeBytes,
                            durationMs = extractedDuration.takeIf { it > 0 } ?: video.durationMs,
                            contentSignature = signature,
                            unavailable = false,
                            thumbnailPath = thumbnailPath,
                        )
                        dao.insertOrReplace(updated)
                    } else {
                        // Mark as available if it was previously unavailable
                        if (existingByMediaStoreId.unavailable) {
                            dao.insertOrReplace(existingByMediaStoreId.copy(unavailable = false))
                        }
                    }
                    processedCount++
                    continue
                }

                // New video - generate thumbnail and insert
                val (thumbnailPath, extractedDuration) = ThumbnailGenerator.generate(
                    applicationContext,
                    video.contentUri,
                    signature.take(16)
                )

                val videoItem = VideoItem(
                    folderId = null, // MediaStore videos don't belong to a folder
                    fileUri = video.contentUri.toString(),
                    title = video.displayName,
                    durationMs = extractedDuration.takeIf { it > 0 } ?: video.durationMs,
                    sizeBytes = video.sizeBytes,
                    contentSignature = signature,
                    createdAt = now,
                    sourceType = SourceType.MEDIASTORE,
                    mediaStoreId = video.mediaStoreId,
                    thumbnailPath = thumbnailPath,
                )
                dao.insertOrReplace(videoItem)
                processedCount++
            }

            // Mark videos that are no longer in MediaStore as unavailable
            val existingMediaStoreIds = dao.getAllMediaStoreIds()
            val missingIds = existingMediaStoreIds.filter { it !in foundMediaStoreIds }
            if (missingIds.isNotEmpty()) {
                val missingItems = missingIds.mapNotNull { dao.findByMediaStoreId(it) }
                if (missingItems.isNotEmpty()) {
                    dao.markUnavailable(missingItems.map { it.id }, true)
                }
            }

            // Update last scan timestamp
            scanSettingsDao.setLastMediaStoreScan(now)

            Result.success(
                Data.Builder()
                    .putInt(KEY_VIDEOS_FOUND, scannedVideos.size)
                    .putInt(KEY_VIDEOS_PROCESSED, processedCount)
                    .build()
            )
        } catch (e: Exception) {
            Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR, e.message)
                    .build()
            )
        }
    }

    companion object {
        const val WORK_NAME = "mediastore_scan"
        const val KEY_VIDEOS_FOUND = "videos_found"
        const val KEY_VIDEOS_PROCESSED = "videos_processed"
        const val KEY_ERROR = "error"
    }
}

