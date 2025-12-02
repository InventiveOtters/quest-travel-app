package com.example.travelcompanion.vrvideo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.travelcompanion.vrvideo.data.db.ScanSettings
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ScanSettings entity.
 * Provides CRUD operations for scan configuration settings.
 */
@Dao
interface ScanSettingsDao {
    /**
     * Get the current scan settings as a Flow.
     * Returns null if settings haven't been initialized yet.
     */
    @Query("SELECT * FROM scan_settings WHERE id = 1")
    fun getSettings(): Flow<ScanSettings?>

    /**
     * Get the current scan settings synchronously.
     * Returns null if settings haven't been initialized yet.
     */
    @Query("SELECT * FROM scan_settings WHERE id = 1")
    suspend fun getSettingsSync(): ScanSettings?

    /**
     * Insert or update scan settings.
     * Uses REPLACE strategy to ensure singleton pattern.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: ScanSettings)

    /**
     * Enable or disable auto-scan.
     */
    @Query("UPDATE scan_settings SET autoScanEnabled = :enabled WHERE id = 1")
    suspend fun setAutoScanEnabled(enabled: Boolean)

    /**
     * Update the last MediaStore scan timestamp.
     */
    @Query("UPDATE scan_settings SET lastMediaStoreScan = :timestamp WHERE id = 1")
    suspend fun setLastMediaStoreScan(timestamp: Long)
}

