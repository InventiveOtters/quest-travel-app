package com.inotter.travelcompanion.data.datasources.videolibrary

import com.inotter.travelcompanion.data.datasources.videolibrary.models.LibraryFolder
import com.inotter.travelcompanion.data.datasources.videolibrary.models.PlaybackSettings
import com.inotter.travelcompanion.data.datasources.videolibrary.models.ScanSettings
import com.inotter.travelcompanion.data.datasources.videolibrary.models.SourceType
import com.inotter.travelcompanion.data.datasources.videolibrary.models.Thumbnail
import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSession
import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSessionStatus
import com.inotter.travelcompanion.data.datasources.videolibrary.models.VideoItem
import kotlinx.coroutines.flow.Flow

/**
 * Data source interface for the video library database.
 * Provides all data operations for video library entities.
 */
interface VideoLibraryDataSource {

    // ============== Library Folder Operations ==============

    suspend fun insertFolder(folder: LibraryFolder): Long
    fun getAllFolders(): Flow<List<LibraryFolder>>
    suspend fun deleteFolderById(id: Long)
    suspend fun getFolderById(id: Long): LibraryFolder?
    suspend fun getFolderByTreeUri(treeUri: String): LibraryFolder?

    // ============== Video Item Operations ==============

    suspend fun insertOrReplaceVideo(item: VideoItem): Long
    fun getAllVideos(): Flow<List<VideoItem>>
    suspend fun deleteVideoById(id: Long)
    suspend fun findVideoBySignature(sig: String): VideoItem?
    suspend fun updateVideoPlaybackProgress(id: Long, lastPlayedAt: Long?, lastPositionMs: Long?)
    suspend fun markVideosUnavailable(ids: List<Long>, flag: Boolean = true)
    suspend fun getVideosByFolderId(folderId: Long): List<VideoItem>
    fun getVideosBySourceType(sourceType: SourceType): Flow<List<VideoItem>>
    suspend fun getVideosBySourceTypeSync(sourceType: SourceType): List<VideoItem>
    suspend fun findVideoByMediaStoreId(mediaStoreId: Long): VideoItem?
    suspend fun deleteVideosBySourceType(sourceType: SourceType)
    suspend fun getAllMediaStoreIds(): List<Long>

    // ============== Thumbnail Operations ==============

    suspend fun upsertThumbnail(thumb: Thumbnail)
    suspend fun getThumbnail(videoId: Long): Thumbnail?

    // ============== Playback Settings Operations ==============

    suspend fun upsertPlaybackSettings(settings: PlaybackSettings)
    fun getPlaybackSettingsFlow(): Flow<PlaybackSettings?>
    suspend fun getPlaybackSettings(): PlaybackSettings?

    // ============== Scan Settings Operations ==============

    fun getScanSettingsFlow(): Flow<ScanSettings?>
    suspend fun getScanSettings(): ScanSettings?
    suspend fun upsertScanSettings(settings: ScanSettings)
    suspend fun setScanAutoEnabled(enabled: Boolean)
    suspend fun setLastMediaStoreScan(timestamp: Long)

    // ============== Upload Session Operations ==============

    suspend fun insertUploadSession(session: UploadSession): Long
    suspend fun updateUploadSession(session: UploadSession)
    fun getAllUploadSessions(): Flow<List<UploadSession>>
    suspend fun getAllUploadSessionsSync(): List<UploadSession>
    suspend fun getUploadSessionById(id: Long): UploadSession?
    suspend fun getUploadSessionByMediaStoreUri(uri: String): UploadSession?
    suspend fun getIncompleteUploadSessions(): List<UploadSession>
    fun getIncompleteUploadSessionsFlow(): Flow<List<UploadSession>>
    suspend fun updateUploadProgress(id: Long, bytes: Long, timestamp: Long = System.currentTimeMillis())
    suspend fun markUploadCompleted(id: Long, timestamp: Long = System.currentTimeMillis())
    suspend fun markUploadFailed(id: Long, timestamp: Long = System.currentTimeMillis())
    suspend fun markUploadCancelled(id: Long, timestamp: Long = System.currentTimeMillis())
    suspend fun deleteUploadSessionById(id: Long)
    suspend fun deleteUploadSessionsByStatus(status: UploadSessionStatus)
    suspend fun deleteUploadSessionsOlderThan(timestamp: Long)
    suspend fun deleteFinishedUploadSessions()
    suspend fun getIncompleteUploadCount(): Int
    fun getIncompleteUploadCountFlow(): Flow<Int>

    // ============== TUS-specific Upload Session Operations ==============

    /** Get upload session by TUS upload URL (for resume lookups) */
    suspend fun getUploadSessionByUploadUrl(url: String): UploadSession?

    /** Get upload session by TUS upload ID */
    suspend fun getUploadSessionByTusId(tusId: String): UploadSession?

    /** Update progress by TUS upload ID */
    suspend fun updateUploadProgressByTusId(tusId: String, bytes: Long, timestamp: Long = System.currentTimeMillis())

    /** Mark upload completed by TUS upload ID */
    suspend fun markUploadCompletedByTusId(tusId: String, timestamp: Long = System.currentTimeMillis())

    /** Delete upload session by TUS upload ID */
    suspend fun deleteUploadSessionByTusId(tusId: String)

    /** Delete expired TUS sessions (older than 24 hours) */
    suspend fun deleteExpiredUploadSessions(cutoffMillis: Long = UploadSession.EXPIRATION_MILLIS): Int
}

