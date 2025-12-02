package com.example.travelcompanion.vrvideo.data.repo

import com.example.travelcompanion.vrvideo.data.db.ScanSettings
import com.example.travelcompanion.vrvideo.data.db.VideoLibraryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing scan settings.
 * Provides operations to query and update MediaStore auto-scan configuration.
 *
 * @property db Video library database instance
 */
class ScanSettingsRepository(
    private val db: VideoLibraryDatabase,
) {
    private val dao = db.scanSettingsDao()

    /**
     * Returns a Flow of the current scan settings.
     * Emits default settings if none exist.
     */
    fun getSettings(): Flow<ScanSettings> = dao.getSettings().map { 
        it ?: ScanSettings() 
    }

    /**
     * Gets the current scan settings synchronously.
     * Returns default settings if none exist.
     */
    suspend fun getSettingsSync(): ScanSettings = 
        dao.getSettingsSync() ?: ScanSettings()

    /**
     * Initializes scan settings if they don't exist.
     * Should be called on first app launch.
     */
    suspend fun ensureInitialized() {
        if (dao.getSettingsSync() == null) {
            dao.upsert(ScanSettings())
        }
    }

    /**
     * Enables or disables auto-scan from MediaStore.
     *
     * @param enabled Whether auto-scan should be enabled
     */
    suspend fun setAutoScanEnabled(enabled: Boolean) {
        ensureInitialized()
        dao.setAutoScanEnabled(enabled)
    }

    /**
     * Returns whether auto-scan is currently enabled.
     */
    suspend fun isAutoScanEnabled(): Boolean = 
        getSettingsSync().autoScanEnabled

    /**
     * Updates the last MediaStore scan timestamp.
     *
     * @param timestamp Epoch millis of the scan completion
     */
    suspend fun setLastMediaStoreScan(timestamp: Long) {
        ensureInitialized()
        dao.setLastMediaStoreScan(timestamp)
    }

    /**
     * Returns the timestamp of the last MediaStore scan.
     * Returns 0 if no scan has been performed.
     */
    suspend fun getLastMediaStoreScan(): Long = 
        getSettingsSync().lastMediaStoreScan
}

