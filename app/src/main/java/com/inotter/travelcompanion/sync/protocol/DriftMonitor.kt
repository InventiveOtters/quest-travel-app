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
 * Monitors playback drift and provides statistics.
 * 
 * Tracks drift over time and provides:
 * - Current drift
 * - Average drift
 * - Maximum drift
 * - Drift history
 * - Auto-correction when drift exceeds threshold
 * 
 * Usage:
 * ```
 * val monitor = DriftMonitor(playbackCore)
 * monitor.startMonitoring()
 * monitor.updateMasterPosition(position, timestamp)
 * val stats = monitor.getDriftStatistics()
 * monitor.stopMonitoring()
 * ```
 */
class DriftMonitor(
    private val playbackCore: PlaybackCore
) {
    companion object {
        private const val TAG = "DriftMonitor"
        
        // Monitoring settings
        private const val CHECK_INTERVAL_MS = 5000L // Check every 5 seconds
        private const val DRIFT_CORRECTION_THRESHOLD_MS = 100L // Auto-correct if drift > 100ms
        private const val HISTORY_SIZE = 20 // Keep last 20 drift measurements
    }
    
    data class DriftStatistics(
        val currentDrift: Long,
        val averageDrift: Long,
        val maxDrift: Long,
        val minDrift: Long,
        val correctionCount: Int
    )
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // Master state
    private var lastMasterPosition: Long = 0L
    private var lastMasterTimestamp: Long = 0L
    
    // Drift tracking
    private val driftHistory = mutableListOf<Long>()
    private val _currentDrift = MutableStateFlow(0L)
    val currentDrift: StateFlow<Long> = _currentDrift.asStateFlow()
    
    private var correctionCount = 0
    
    // Monitoring
    private var monitorJob: Job? = null
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    
    /**
     * Start monitoring drift.
     */
    fun startMonitoring() {
        if (_isMonitoring.value) {
            return
        }
        
        _isMonitoring.value = true
        monitorJob = scope.launch {
            while (_isMonitoring.value) {
                checkDrift()
                delay(CHECK_INTERVAL_MS)
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
     */
    fun updateMasterPosition(position: Long, timestamp: Long) {
        lastMasterPosition = position
        lastMasterTimestamp = timestamp
    }
    
    /**
     * Check current drift.
     * 
     * @return Current drift in milliseconds (positive = ahead, negative = behind)
     */
    fun checkDrift(): Long {
        if (lastMasterTimestamp == 0L) {
            return 0L
        }
        
        val currentPosition = playbackCore.getCurrentPosition()
        val timeElapsed = System.currentTimeMillis() - lastMasterTimestamp
        val expectedMasterPosition = lastMasterPosition + timeElapsed
        
        val drift = currentPosition - expectedMasterPosition
        _currentDrift.value = drift
        
        // Add to history
        driftHistory.add(drift)
        if (driftHistory.size > HISTORY_SIZE) {
            driftHistory.removeAt(0)
        }
        
        // Auto-correct if drift is too high
        if (abs(drift) > DRIFT_CORRECTION_THRESHOLD_MS) {
            correctDrift(drift, expectedMasterPosition)
        }
        
        return drift
    }
    
    /**
     * Correct drift by seeking to the correct position.
     * 
     * @param drift Current drift in milliseconds
     * @param expectedPosition Expected master position
     */
    fun correctDrift(drift: Long, expectedPosition: Long) {
        Log.w(TAG, "Correcting drift: ${drift}ms, seeking to $expectedPosition")
        
        playbackCore.seekTo(expectedPosition)
        correctionCount++
        
        // Reset drift after correction
        _currentDrift.value = 0L
    }
    
    /**
     * Get drift statistics.
     * 
     * @return Drift statistics
     */
    fun getDriftStatistics(): DriftStatistics {
        val current = _currentDrift.value
        val average = if (driftHistory.isNotEmpty()) {
            driftHistory.average().toLong()
        } else {
            0L
        }
        val max = driftHistory.maxOrNull() ?: 0L
        val min = driftHistory.minOrNull() ?: 0L
        
        return DriftStatistics(
            currentDrift = current,
            averageDrift = average,
            maxDrift = max,
            minDrift = min,
            correctionCount = correctionCount
        )
    }
    
    /**
     * Reset drift statistics.
     */
    fun resetStatistics() {
        driftHistory.clear()
        correctionCount = 0
        _currentDrift.value = 0L
        Log.i(TAG, "Reset drift statistics")
    }
}
