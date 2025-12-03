package com.inotter.travelcompanion.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton entity storing scan configuration settings.
 * Controls whether MediaStore auto-scan is enabled and tracks last scan time.
 *
 * @property id Always 1 (singleton pattern)
 * @property autoScanEnabled Whether MediaStore auto-scan is enabled
 * @property lastMediaStoreScan Timestamp of last MediaStore scan (epoch millis)
 */
@Entity(tableName = "scan_settings")
data class ScanSettings(
    @PrimaryKey val id: Int = 1,
    val autoScanEnabled: Boolean = false,
    val lastMediaStoreScan: Long = 0,
)

