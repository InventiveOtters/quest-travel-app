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
        const val DRIFT_THRESHOLD_GOOD_MS = 100L // Green: < 100ms
        const val DRIFT_THRESHOLD_WARNING_MS = 300L // Yellow: 100-300ms
        const val DRIFT_THRESHOLD_CRITICAL_MS = 500L // Red: > 500ms, auto-correct

        // Monitoring interval
        private const val MONITOR_INTERVAL_MS = 5000L // Check every 5 seconds

        // Cooldown periods
        private const val INITIAL_PLAYBACK_COOLDOWN_MS = 15000L // Wait 15 seconds after initial playback starts
        private const val CORRECTION_COOLDOWN_MS = 10000L // Wait 10 seconds after each correction
    }
    
    // Sync quality levels
    enum class SyncQuality {
        EXCELLENT, // < 100ms drift
        GOOD,      // 100-300ms drift
        POOR,      // 300-500ms drift
        CRITICAL   // > 500ms drift
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Last known master state
    private var lastMasterPosition: Long = 0L
    private var lastMasterTimestamp: Long = 0L
    private var isMasterPlaying: Boolean = false

    // Cooldown tracking
    private var lastCorrectionTime: Long = 0L
    private var playbackStartTime: Long = 0L // Track when playback first started
    private var isInitialPlayback: Boolean = true // Flag for initial playback period

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

        // Reset cooldown state
        lastCorrectionTime = 0L
        playbackStartTime = 0L
        isInitialPlayback = true

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

        // Track when playback starts for initial cooldown
        val wasPlaying = isMasterPlaying
        isMasterPlaying = isPlaying

        if (isPlaying && !wasPlaying) {
            // Playback just started - begin initial cooldown period
            playbackStartTime = System.currentTimeMillis()
            isInitialPlayback = true
            Log.i(TAG, "Playback started - initial cooldown period begins (${INITIAL_PLAYBACK_COOLDOWN_MS}ms)")
        }
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

        val currentTime = System.currentTimeMillis()

        // Check if we're in initial playback cooldown
        if (isInitialPlayback) {
            val timeSincePlaybackStart = currentTime - playbackStartTime
            if (timeSincePlaybackStart < INITIAL_PLAYBACK_COOLDOWN_MS) {
                Log.d(TAG, "In initial playback cooldown (${timeSincePlaybackStart}ms/${INITIAL_PLAYBACK_COOLDOWN_MS}ms) - drift: ${drift}ms")
                return
            } else {
                // Initial cooldown period has ended
                isInitialPlayback = false
                Log.i(TAG, "Initial playback cooldown ended - drift correction now active")
            }
        }

        // Check if we're in post-correction cooldown
        val timeSinceLastCorrection = currentTime - lastCorrectionTime
        val isInCorrectionCooldown = lastCorrectionTime > 0 && timeSinceLastCorrection < CORRECTION_COOLDOWN_MS

        if (isInCorrectionCooldown) {
            Log.d(TAG, "In correction cooldown (${timeSinceLastCorrection}ms/${CORRECTION_COOLDOWN_MS}ms) - drift: ${drift}ms")
            return
        }

        // Only auto-correct if:
        // 1. Drift is too high (> 500ms)
        // 2. Master is playing (don't correct during pause)
        // 3. Not in any cooldown period
        if (abs(drift) > DRIFT_THRESHOLD_CRITICAL_MS && isMasterPlaying) {
            correctDrift(drift)
        } else if (abs(drift) > DRIFT_THRESHOLD_CRITICAL_MS) {
            Log.d(TAG, "Drift is high (${drift}ms) but master is paused - skipping correction")
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

        // Update last correction time for cooldown
        lastCorrectionTime = System.currentTimeMillis()

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

