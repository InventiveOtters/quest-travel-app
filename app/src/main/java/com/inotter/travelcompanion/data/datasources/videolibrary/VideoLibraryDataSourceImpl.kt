package com.inotter.travelcompanion.data.datasources.videolibrary

import com.inotter.travelcompanion.data.datasources.videolibrary.models.LibraryFolder
import com.inotter.travelcompanion.data.datasources.videolibrary.models.PlaybackSettings
import com.inotter.travelcompanion.data.datasources.videolibrary.models.ScanSettings
import com.inotter.travelcompanion.data.datasources.videolibrary.models.SourceType
import com.inotter.travelcompanion.data.datasources.videolibrary.models.StereoLayout
import com.inotter.travelcompanion.data.datasources.videolibrary.models.Thumbnail
import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSession
import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSessionStatus
import com.inotter.travelcompanion.data.datasources.videolibrary.models.VideoItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [VideoLibraryDataSource] that wraps Room database DAOs.
 * All database operations are delegated to the appropriate DAO.
 */
@Singleton
class VideoLibraryDataSourceImpl @Inject constructor(
    private val database: VideoLibraryDatabase
) : VideoLibraryDataSource {

    private val libraryFolderDao = database.libraryFolderDao()
    private val videoItemDao = database.videoItemDao()
    private val thumbnailDao = database.thumbnailDao()
    private val playbackSettingsDao = database.playbackSettingsDao()
    private val scanSettingsDao = database.scanSettingsDao()
    private val uploadSessionDao = database.uploadSessionDao()

    // ============== Library Folder Operations ==============

    override suspend fun insertFolder(folder: LibraryFolder): Long =
        libraryFolderDao.insert(folder)

    override fun getAllFolders(): Flow<List<LibraryFolder>> =
        libraryFolderDao.getAll()

    override suspend fun deleteFolderById(id: Long) =
        libraryFolderDao.deleteById(id)

    override suspend fun getFolderById(id: Long): LibraryFolder? =
        libraryFolderDao.getById(id)

    override suspend fun getFolderByTreeUri(treeUri: String): LibraryFolder? =
        libraryFolderDao.getByTreeUri(treeUri)

    // ============== Video Item Operations ==============

    override suspend fun insertOrReplaceVideo(item: VideoItem): Long =
        videoItemDao.insertOrReplace(item)

    override fun getAllVideos(): Flow<List<VideoItem>> =
        videoItemDao.getAll()

    override suspend fun deleteVideoById(id: Long) =
        videoItemDao.deleteById(id)

    override suspend fun findVideoBySignature(sig: String): VideoItem? =
        videoItemDao.findBySignature(sig)

    override suspend fun setVideoStereoLayoutOverride(id: Long, layout: StereoLayout?) =
        videoItemDao.setStereoLayoutOverride(id, layout)

    override suspend fun updateVideoPlaybackProgress(id: Long, lastPlayedAt: Long?, lastPositionMs: Long?) =
        videoItemDao.updatePlaybackProgress(id, lastPlayedAt, lastPositionMs)

    override suspend fun markVideosUnavailable(ids: List<Long>, flag: Boolean) =
        videoItemDao.markUnavailable(ids, flag)

    override suspend fun getVideosByFolderId(folderId: Long): List<VideoItem> =
        videoItemDao.getByFolderId(folderId)

    override fun getVideosBySourceType(sourceType: SourceType): Flow<List<VideoItem>> =
        videoItemDao.getBySourceType(sourceType)

    override suspend fun getVideosBySourceTypeSync(sourceType: SourceType): List<VideoItem> =
        videoItemDao.getBySourceTypeSync(sourceType)

    override suspend fun findVideoByMediaStoreId(mediaStoreId: Long): VideoItem? =
        videoItemDao.findByMediaStoreId(mediaStoreId)

    override suspend fun deleteVideosBySourceType(sourceType: SourceType) =
        videoItemDao.deleteBySourceType(sourceType)

    override suspend fun getAllMediaStoreIds(): List<Long> =
        videoItemDao.getAllMediaStoreIds()

    // ============== Thumbnail Operations ==============

    override suspend fun upsertThumbnail(thumb: Thumbnail) =
        thumbnailDao.upsert(thumb)

    override suspend fun getThumbnail(videoId: Long): Thumbnail? =
        thumbnailDao.get(videoId)

    // ============== Playback Settings Operations ==============

    override suspend fun upsertPlaybackSettings(settings: PlaybackSettings) =
        playbackSettingsDao.upsert(settings)

    override fun getPlaybackSettingsFlow(): Flow<PlaybackSettings?> =
        playbackSettingsDao.getFlow()

    override suspend fun getPlaybackSettings(): PlaybackSettings? =
        playbackSettingsDao.get()

    override suspend fun updatePlaybackSettings(mode: StereoLayout, skip: Int, resume: Boolean) =
        playbackSettingsDao.update(mode, skip, resume)

    // ============== Scan Settings Operations ==============

    override fun getScanSettingsFlow(): Flow<ScanSettings?> =
        scanSettingsDao.getSettings()

    override suspend fun getScanSettings(): ScanSettings? =
        scanSettingsDao.getSettingsSync()

    override suspend fun upsertScanSettings(settings: ScanSettings) =
        scanSettingsDao.upsert(settings)

    override suspend fun setScanAutoEnabled(enabled: Boolean) =
        scanSettingsDao.setAutoScanEnabled(enabled)

    override suspend fun setLastMediaStoreScan(timestamp: Long) =
        scanSettingsDao.setLastMediaStoreScan(timestamp)

    // ============== Upload Session Operations ==============

    override suspend fun insertUploadSession(session: UploadSession): Long =
        uploadSessionDao.insert(session)

    override suspend fun updateUploadSession(session: UploadSession) =
        uploadSessionDao.update(session)

    override fun getAllUploadSessions(): Flow<List<UploadSession>> =
        uploadSessionDao.getAll()

    override suspend fun getAllUploadSessionsSync(): List<UploadSession> =
        uploadSessionDao.getAllSync()

    override suspend fun getUploadSessionById(id: Long): UploadSession? =
        uploadSessionDao.getById(id)

    override suspend fun getUploadSessionByMediaStoreUri(uri: String): UploadSession? =
        uploadSessionDao.getByMediaStoreUri(uri)

    override suspend fun getIncompleteUploadSessions(): List<UploadSession> =
        uploadSessionDao.getIncomplete()

    override fun getIncompleteUploadSessionsFlow(): Flow<List<UploadSession>> =
        uploadSessionDao.getIncompleteFlow()

    override suspend fun updateUploadProgress(id: Long, bytes: Long, timestamp: Long) =
        uploadSessionDao.updateProgress(id, bytes, timestamp)

    override suspend fun markUploadCompleted(id: Long, timestamp: Long) =
        uploadSessionDao.markCompleted(id, timestamp)

    override suspend fun markUploadFailed(id: Long, timestamp: Long) =
        uploadSessionDao.markFailed(id, timestamp)

    override suspend fun markUploadCancelled(id: Long, timestamp: Long) =
        uploadSessionDao.markCancelled(id, timestamp)

    override suspend fun deleteUploadSessionById(id: Long) =
        uploadSessionDao.deleteById(id)

    override suspend fun deleteUploadSessionsByStatus(status: UploadSessionStatus) =
        uploadSessionDao.deleteByStatus(status)

    override suspend fun deleteUploadSessionsOlderThan(timestamp: Long) =
        uploadSessionDao.deleteOlderThan(timestamp)

    override suspend fun deleteFinishedUploadSessions() =
        uploadSessionDao.deleteFinished()

    override suspend fun getIncompleteUploadCount(): Int =
        uploadSessionDao.getIncompleteCount()

    override fun getIncompleteUploadCountFlow(): Flow<Int> =
        uploadSessionDao.getIncompleteCountFlow()
}

