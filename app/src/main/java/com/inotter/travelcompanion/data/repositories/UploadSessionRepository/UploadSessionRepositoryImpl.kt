package com.inotter.travelcompanion.data.repositories.UploadSessionRepository

import com.inotter.travelcompanion.data.datasources.videolibrary.VideoLibraryDataSource
import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [UploadSessionRepository] for managing upload sessions.
 *
 * @property dataSource Video library data source
 */
@Singleton
class UploadSessionRepositoryImpl @Inject constructor(
    private val dataSource: VideoLibraryDataSource
) : UploadSessionRepository {

    override suspend fun createSession(
        filename: String,
        expectedSize: Long,
        mediaStoreUri: String,
        mimeType: String
    ): Long {
        val session = UploadSession(
            filename = filename,
            expectedSize = expectedSize,
            mediaStoreUri = mediaStoreUri,
            mimeType = mimeType
        )
        return dataSource.insertUploadSession(session)
    }

    override suspend fun updateProgress(sessionId: Long, bytesReceived: Long) {
        dataSource.updateUploadProgress(sessionId, bytesReceived)
    }

    override suspend fun markCompleted(sessionId: Long) {
        dataSource.markUploadCompleted(sessionId)
    }

    override suspend fun markFailed(sessionId: Long) {
        dataSource.markUploadFailed(sessionId)
    }

    override suspend fun markCancelled(sessionId: Long) {
        dataSource.markUploadCancelled(sessionId)
    }

    override suspend fun getIncompleteSessions(): List<UploadSession> {
        return dataSource.getIncompleteUploadSessions()
    }

    override fun getIncompleteSessionsFlow(): Flow<List<UploadSession>> {
        return dataSource.getIncompleteUploadSessionsFlow()
    }

    override fun getIncompleteCountFlow(): Flow<Int> {
        return dataSource.getIncompleteUploadCountFlow()
    }

    override suspend fun getByMediaStoreUri(uri: String): UploadSession? {
        return dataSource.getUploadSessionByMediaStoreUri(uri)
    }

    override suspend fun getById(id: Long): UploadSession? {
        return dataSource.getUploadSessionById(id)
    }

    override suspend fun deleteSession(sessionId: Long) {
        dataSource.deleteUploadSessionById(sessionId)
    }

    override suspend fun cleanupFinishedSessions() {
        dataSource.deleteFinishedUploadSessions()
    }

    override suspend fun cleanupOldSessions(maxAgeMillis: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        dataSource.deleteUploadSessionsOlderThan(cutoff)
    }

    override fun getAllSessions(): Flow<List<UploadSession>> {
        return dataSource.getAllUploadSessions()
    }

    override suspend fun getAllSessionsSync(): List<UploadSession> {
        return dataSource.getAllUploadSessionsSync()
    }
}

