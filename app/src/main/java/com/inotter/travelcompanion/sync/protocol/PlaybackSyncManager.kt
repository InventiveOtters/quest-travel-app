package com.inotter.travelcompanion.sync.protocol

import android.util.Log
import com.inotter.travelcompanion.playback.PlaybackCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Manages playback synchronization with predictive sync and drift correction.
 * 
 * Implements:
 * - Predictive sync: Calculate when to start playback based on network latency
 * - Drift detection: Compare local position with master position
 * - Drift correction: Auto-correct when drift exceeds threshold
 * - Sync quality indicator: Green/Yellow/Red based on drift
 * 
 * Usage:
 * ```
 * val syncManager = PlaybackSyncManager(playbackCore)
 * syncManager.startMonitoring()
 * syncManager.updateMasterPosition(position = 60000, timestamp = System.currentTimeMillis())
 * val drift = syncManager.getCurrentDrift()
 * syncManager.stopMonitoring()
 * ```
 */
class PlaybackSyncManager(
    private val playbackCore: PlaybackCore
) {
    companion object {
        private const val TAG = "PlaybackSyncManager"
        
        // Drift thresholds
        const val DRIFT_THRESHOLD_GOOD_MS = 50L // Green: < 50ms
        const val DRIFT_THRESHOLD_WARNING_MS = 100L // Yellow: 50-100ms
        const val DRIFT_THRESHOLD_CRITICAL_MS = 200L // Red: > 100ms, auto-correct
        
        // Monitoring interval
        private const val MONITOR_INTERVAL_MS = 5000L // Check every 5 seconds
    }
    
    // Sync quality levels
    enum class SyncQuality {
        EXCELLENT, // < 50ms drift
        GOOD,      // 50-100ms drift
        POOR,      // 100-200ms drift
        CRITICAL   // > 200ms drift
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // Last known master state
    private var lastMasterPosition: Long = 0L
    private var lastMasterTimestamp: Long = 0L
    private var isMasterPlaying: Boolean = false
    
    // Current drift
    private val _currentDrift = MutableStateFlow(0L)
    val currentDrift: StateFlow<Long> = _currentDrift.asStateFlow()
    
    // Sync quality
    private val _syncQuality = MutableStateFlow(SyncQuality.EXCELLENT)
    val syncQuality: StateFlow<SyncQuality> = _syncQuality.asStateFlow()
    
    // Monitoring
    private var monitorJob: Job? = null
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    
    /**
     * Start monitoring drift and auto-correction.
     */
    fun startMonitoring() {
        if (_isMonitoring.value) {
            return
        }
        
        _isMonitoring.value = true
        monitorJob = scope.launch {
            while (_isMonitoring.value) {
                checkAndCorrectDrift()
                delay(MONITOR_INTERVAL_MS)
            }
        }
        
        Log.i(TAG, "Started drift monitoring")
    }
    
    /**
     * Stop monitoring drift.
     */
    fun stopMonitoring() {
        _isMonitoring.value = false
        monitorJob?.cancel()
        monitorJob = null
        Log.i(TAG, "Stopped drift monitoring")
    }
    
    /**
     * Update master position for drift calculation.
     * 
     * @param position Master's video position in milliseconds
     * @param timestamp When this position was recorded
     * @param isPlaying Whether master is currently playing
     */
    fun updateMasterPosition(position: Long, timestamp: Long, isPlaying: Boolean = true) {
        lastMasterPosition = position
        lastMasterTimestamp = timestamp
        isMasterPlaying = isPlaying
    }
    
    /**
     * Get current drift from master.
     * 
     * @return Drift in milliseconds (positive = ahead, negative = behind)
     */
    fun getCurrentDrift(): Long {
        if (lastMasterTimestamp == 0L) {
            return 0L
        }
        
        val currentPosition = playbackCore.getCurrentPosition()
        val expectedMasterPosition = calculateExpectedMasterPosition()
        
        return currentPosition - expectedMasterPosition
    }
    
    /**
     * Calculate expected master position based on time elapsed.
     */
    private fun calculateExpectedMasterPosition(): Long {
        if (!isMasterPlaying) {
            // If master is paused, expected position doesn't change
            return lastMasterPosition
        }
        
        val timeElapsed = System.currentTimeMillis() - lastMasterTimestamp
        return lastMasterPosition + timeElapsed
    }
    
    /**
     * Check drift and correct if necessary.
     */
    private fun checkAndCorrectDrift() {
        val drift = getCurrentDrift()
        _currentDrift.value = drift
        
        // Update sync quality
        _syncQuality.value = when {
            abs(drift) < DRIFT_THRESHOLD_GOOD_MS -> SyncQuality.EXCELLENT
            abs(drift) < DRIFT_THRESHOLD_WARNING_MS -> SyncQuality.GOOD
            abs(drift) < DRIFT_THRESHOLD_CRITICAL_MS -> SyncQuality.POOR
            else -> SyncQuality.CRITICAL
        }
        
        // Auto-correct if drift is too high
        if (abs(drift) > DRIFT_THRESHOLD_CRITICAL_MS) {
            correctDrift(drift)
        }
    }

    /**
     * Correct drift by seeking to the correct position.
     *
     * @param drift Current drift in milliseconds
     */
    private fun correctDrift(drift: Long) {
        val expectedPosition = calculateExpectedMasterPosition()

        Log.w(TAG, "Correcting drift: ${drift}ms, seeking to $expectedPosition")

        // Seek to correct position
        playbackCore.seekTo(expectedPosition)

        // Reset drift after correction
        _currentDrift.value = 0L
    }

    /**
     * Calculate predictive start time for synchronized playback.
     *
     * @param networkLatencyMs Estimated network latency
     * @return Target start time (milliseconds since epoch)
     */
    fun calculatePredictiveStartTime(networkLatencyMs: Long = 500L): Long {
        return System.currentTimeMillis() + networkLatencyMs
    }

    /**
     * Get sync quality as a color indicator.
     *
     * @return Color string: "green", "yellow", "orange", "red"
     */
    fun getSyncQualityColor(): String {
        return when (_syncQuality.value) {
            SyncQuality.EXCELLENT -> "green"
            SyncQuality.GOOD -> "yellow"
            SyncQuality.POOR -> "orange"
            SyncQuality.CRITICAL -> "red"
        }
    }
}

