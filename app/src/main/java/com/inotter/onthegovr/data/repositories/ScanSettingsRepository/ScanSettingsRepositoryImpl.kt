package com.inotter.onthegovr.data.repositories.ScanSettingsRepository

import com.inotter.onthegovr.data.datasources.videolibrary.VideoLibraryDataSource
import com.inotter.onthegovr.data.datasources.videolibrary.models.ScanSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ScanSettingsRepository] for managing scan settings.
 *
 * @property dataSource Video library data source
 */
@Singleton
class ScanSettingsRepositoryImpl @Inject constructor(
    private val dataSource: VideoLibraryDataSource,
) : ScanSettingsRepository {

    override fun getSettings(): Flow<ScanSettings> = dataSource.getScanSettingsFlow().map {
        it ?: ScanSettings()
    }

    override suspend fun getSettingsSync(): ScanSettings =
        dataSource.getScanSettings() ?: ScanSettings()

    override suspend fun ensureInitialized() {
        if (dataSource.getScanSettings() == null) {
            dataSource.upsertScanSettings(ScanSettings())
        }
    }

    override suspend fun setAutoScanEnabled(enabled: Boolean) {
        ensureInitialized()
        dataSource.setScanAutoEnabled(enabled)
    }

    override suspend fun isAutoScanEnabled(): Boolean =
        getSettingsSync().autoScanEnabled

    override suspend fun setLastMediaStoreScan(timestamp: Long) {
        ensureInitialized()
        dataSource.setLastMediaStoreScan(timestamp)
    }

    override suspend fun getLastMediaStoreScan(): Long =
        getSettingsSync().lastMediaStoreScan
}

