package com.inotter.travelcompanion.data.repositories.ScanSettingsRepository

import com.inotter.travelcompanion.data.datasources.videolibrary.models.ScanSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing scan settings.
 * Provides operations to query and update MediaStore auto-scan configuration.
 */
interface ScanSettingsRepository {
    /**
     * Returns a Flow of the current scan settings.
     * Emits default settings if none exist.
     */
    fun getSettings(): Flow<ScanSettings>

    /**
     * Gets the current scan settings synchronously.
     * Returns default settings if none exist.
     */
    suspend fun getSettingsSync(): ScanSettings

    /**
     * Initializes scan settings if they don't exist.
     * Should be called on first app launch.
     */
    suspend fun ensureInitialized()

    /**
     * Enables or disables auto-scan from MediaStore.
     *
     * @param enabled Whether auto-scan should be enabled
     */
    suspend fun setAutoScanEnabled(enabled: Boolean)

    /**
     * Returns whether auto-scan is currently enabled.
     */
    suspend fun isAutoScanEnabled(): Boolean

    /**
     * Updates the last MediaStore scan timestamp.
     *
     * @param timestamp Epoch millis of the scan completion
     */
    suspend fun setLastMediaStoreScan(timestamp: Long)

    /**
     * Returns the timestamp of the last MediaStore scan.
     * Returns 0 if no scan has been performed.
     */
    suspend fun getLastMediaStoreScan(): Long
}

