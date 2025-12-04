package com.inotter.travelcompanion.data.repositories.VideoRepository

import com.inotter.travelcompanion.data.datasources.videolibrary.VideoLibraryDataSource
import com.inotter.travelcompanion.data.datasources.videolibrary.models.StereoLayout
import com.inotter.travelcompanion.data.datasources.videolibrary.models.VideoItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [VideoRepository] for managing video items in the library.
 *
 * @property dataSource Video library data source
 */
@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val dataSource: VideoLibraryDataSource,
) : VideoRepository {

    override fun queryVideos(): Flow<List<VideoItem>> = dataSource.getAllVideos()

    override suspend fun deleteById(id: Long) = dataSource.deleteVideoById(id)

    override suspend fun setStereoLayoutOverride(id: Long, layout: StereoLayout?) =
        dataSource.setVideoStereoLayoutOverride(id, layout)

    override suspend fun updatePlaybackProgress(id: Long, lastPlayedAt: Long?, lastPositionMs: Long?) =
        dataSource.updateVideoPlaybackProgress(id, lastPlayedAt, lastPositionMs)

    override suspend fun upsert(video: VideoItem): Long = dataSource.insertOrReplaceVideo(video)

    override suspend fun findBySignature(sig: String): VideoItem? = dataSource.findVideoBySignature(sig)
}

