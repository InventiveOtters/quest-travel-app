package com.inotter.onthegovr.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.inotter.onthegovr.data.datasources.videolibrary.VideoLibraryDataSource
import com.inotter.onthegovr.data.datasources.videolibrary.models.SourceType
import com.inotter.onthegovr.data.datasources.videolibrary.models.VideoItem
import com.inotter.onthegovr.data.managers.ScannerManager.ScannerManagerImpl
import com.inotter.onthegovr.data.managers.ThumbnailManager.ThumbnailManagerImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that scans videos from MediaStore.
 * Uses READ_MEDIA_VIDEO permission to discover all device videos.
 * Integrates with existing ThumbnailGenerator and deduplication logic.
 *
 * @param appContext Android application context
 * @param params Worker parameters
 * @param dataSource Video library data source for database operations
 */
@HiltWorker
class MediaStoreScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dataSource: VideoLibraryDataSource,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val scanner = ScannerManagerImpl(applicationContext)
        val thumbnailManager = ThumbnailManagerImpl(applicationContext)
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
                val existingBySignature = dataSource.findVideoBySignature(signature)
                if (existingBySignature != null) {
                    // Video already exists (possibly from SAF scan), skip it
                    // Update mediaStoreId if this was originally SAF-discovered
                    if (existingBySignature.mediaStoreId == null) {
                        val updated = existingBySignature.copy(
                            mediaStoreId = video.mediaStoreId
                        )
                        dataSource.insertOrReplaceVideo(updated)
                    }
                    processedCount++
                    continue
                }

                // Check if we already have this MediaStore ID
                val existingByMediaStoreId = dataSource.findVideoByMediaStoreId(video.mediaStoreId)
                if (existingByMediaStoreId != null) {
                    // Update existing entry if file changed
                    if (existingByMediaStoreId.contentSignature != signature) {
                        val (thumbnailPath, extractedDuration) = thumbnailManager.generate(
                            video.contentUri,
                            signature.take(16)
                        ).let { it.thumbnailPath to it.durationMs }
                        val updated = existingByMediaStoreId.copy(
                            fileUri = video.contentUri.toString(),
                            title = video.displayName,
                            sizeBytes = video.sizeBytes,
                            durationMs = extractedDuration.takeIf { it > 0 } ?: video.durationMs,
                            contentSignature = signature,
                            unavailable = false,
                            thumbnailPath = thumbnailPath,
                        )
                        dataSource.insertOrReplaceVideo(updated)
                    } else {
                        // Mark as available if it was previously unavailable
                        if (existingByMediaStoreId.unavailable) {
                            dataSource.insertOrReplaceVideo(existingByMediaStoreId.copy(unavailable = false))
                        }
                    }
                    processedCount++
                    continue
                }

                // New video - generate thumbnail and insert
                val (thumbnailPath, extractedDuration) = thumbnailManager.generate(
                    video.contentUri,
                    signature.take(16)
                ).let { it.thumbnailPath to it.durationMs }

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
                dataSource.insertOrReplaceVideo(videoItem)
                processedCount++
            }

            // Mark videos that are no longer in MediaStore as unavailable
            val existingMediaStoreIds = dataSource.getAllMediaStoreIds()
            val missingIds = existingMediaStoreIds.filter { it !in foundMediaStoreIds }
            if (missingIds.isNotEmpty()) {
                val missingItems = missingIds.mapNotNull { dataSource.findVideoByMediaStoreId(it) }
                if (missingItems.isNotEmpty()) {
                    dataSource.markVideosUnavailable(missingItems.map { it.id }, true)
                }
            }

            // Update last scan timestamp
            dataSource.setLastMediaStoreScan(now)

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

