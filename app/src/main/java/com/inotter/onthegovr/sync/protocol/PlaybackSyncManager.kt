package com.inotter.onthegovr.sync.protocol

import android.util.Log
import com.inotter.onthegovr.playback.PlaybackCore
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
        const val DRIFT_THRESHOLD_SPEED_ADJUST_MS = 500L // Use speed adjustment for drift < 500ms
        const val DRIFT_THRESHOLD_SEEK_MS = 1000L // Use seek for drift > 1000ms

        // Monitoring interval
        private const val MONITOR_INTERVAL_MS = 5000L // Check every 5 seconds

        // Cooldown periods
        private const val INITIAL_PLAYBACK_COOLDOWN_MS = 15000L // Wait 15 seconds after initial playback starts
        private const val CORRECTION_COOLDOWN_MS = 10000L // Wait 10 seconds after each seek correction
        private const val SPEED_ADJUST_COOLDOWN_MS = 2000L // Wait 2 seconds after speed adjustment
    }
    
    // Sync quality levels
    enum class SyncQuality {
        EXCELLENT, // < 100ms drift
        GOOD,      // 100-300ms drift
        POOR,      // 300-500ms drift
        CRITICAL   // > 1000ms drift
    }

    // Correction method
    enum class CorrectionMethod {
        NONE,          // No correction needed
        SPEED_ADJUST,  // Use playback speed adjustment (smooth)
        SEEK           // Use seek (disruptive but necessary for large drift)
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Last known master state
    private var lastMasterPosition: Long = 0L
    private var lastMasterTimestamp: Long = 0L
    private var isMasterPlaying: Boolean = false

    // Cooldown tracking
    private var lastSeekCorrectionTime: Long = 0L
    private var lastSpeedAdjustTime: Long = 0L
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

        // Reset playback speed to normal
        playbackCore.setPlaybackSpeed(1.0f)

        // Reset cooldown state
        lastSeekCorrectionTime = 0L
        lastSpeedAdjustTime = 0L
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
            abs(drift) < DRIFT_THRESHOLD_SPEED_ADJUST_MS -> SyncQuality.POOR
            else -> SyncQuality.CRITICAL
        }

        val currentTime = System.currentTimeMillis()

        // Don't correct if master is paused
        if (!isMasterPlaying) {
            // Reset speed to normal when paused
            if (playbackCore.getPlaybackSpeed() != 1.0f) {
                playbackCore.setPlaybackSpeed(1.0f)
                Log.d(TAG, "Reset playback speed to 1.0x (master paused)")
            }
            return
        }

        // Determine correction method based on drift magnitude
        val correctionMethod = when {
            abs(drift) < DRIFT_THRESHOLD_SPEED_ADJUST_MS -> CorrectionMethod.SPEED_ADJUST
            abs(drift) >= DRIFT_THRESHOLD_SEEK_MS -> CorrectionMethod.SEEK
            else -> CorrectionMethod.NONE
        }

        when (correctionMethod) {
            CorrectionMethod.SPEED_ADJUST -> {
                // Speed adjustments can start immediately (non-disruptive)
                // Check speed adjustment cooldown
                val timeSinceSpeedAdjust = currentTime - lastSpeedAdjustTime
                if (lastSpeedAdjustTime > 0 && timeSinceSpeedAdjust < SPEED_ADJUST_COOLDOWN_MS) {
                    return
                }
                correctDriftWithSpeed(drift)
            }
            CorrectionMethod.SEEK -> {
                // Check if we're in initial playback cooldown (only for seeks)
                if (isInitialPlayback) {
                    val timeSincePlaybackStart = currentTime - playbackStartTime
                    if (timeSincePlaybackStart < INITIAL_PLAYBACK_COOLDOWN_MS) {
                        Log.d(TAG, "In initial playback cooldown for seeks (${timeSincePlaybackStart}ms/${INITIAL_PLAYBACK_COOLDOWN_MS}ms) - drift: ${drift}ms")
                        return
                    } else {
                        // Initial cooldown period has ended
                        isInitialPlayback = false
                        Log.i(TAG, "Initial playback cooldown ended - seek correction now active")
                    }
                }

                // Check seek cooldown
                val timeSinceSeek = currentTime - lastSeekCorrectionTime
                if (lastSeekCorrectionTime > 0 && timeSinceSeek < CORRECTION_COOLDOWN_MS) {
                    Log.d(TAG, "In seek cooldown (${timeSinceSeek}ms/${CORRECTION_COOLDOWN_MS}ms) - drift: ${drift}ms")
                    return
                }
                correctDriftWithSeek(drift)
            }
            CorrectionMethod.NONE -> {
                // Reset speed to normal if drift is acceptable
                if (playbackCore.getPlaybackSpeed() != 1.0f) {
                    playbackCore.setPlaybackSpeed(1.0f)
                    Log.d(TAG, "Reset playback speed to 1.0x (drift acceptable)")
                }
            }
        }
    }

    /**
     * Correct drift using playback speed adjustment (smooth, no pause).
     * For small drifts (< 500ms), gradually adjust speed to catch up or slow down.
     *
     * @param drift Current drift in milliseconds (negative = behind, positive = ahead)
     */
    private fun correctDriftWithSpeed(drift: Long) {
        // Calculate speed adjustment based on drift
        // For every 100ms of drift, adjust speed by 2%
        val speedAdjustment = (drift / 100.0f) * 0.02f
        val targetSpeed = (1.0f - speedAdjustment).coerceIn(0.95f, 1.05f)

        playbackCore.setPlaybackSpeed(targetSpeed)
        lastSpeedAdjustTime = System.currentTimeMillis()

        Log.d(TAG, "Adjusting playback speed to ${targetSpeed}x to correct drift: ${drift}ms")
    }

    /**
     * Correct drift by seeking to the correct position (disruptive but necessary for large drift).
     * Only used when drift exceeds 1000ms.
     *
     * @param drift Current drift in milliseconds
     */
    private fun correctDriftWithSeek(drift: Long) {
        val expectedPosition = calculateExpectedMasterPosition()

        Log.w(TAG, "Correcting large drift with seek: ${drift}ms, seeking to $expectedPosition")

        // Reset speed to normal before seeking
        playbackCore.setPlaybackSpeed(1.0f)

        // Seek to correct position
        playbackCore.seekTo(expectedPosition)

        // Update last correction time for cooldown
        lastSeekCorrectionTime = System.currentTimeMillis()

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
            SyncQuality.EXCELLENT -> "green"   // < 100ms
            SyncQuality.GOOD -> "yellow"       // 100-300ms
            SyncQuality.POOR -> "orange"       // 300-500ms (speed adjust)
            SyncQuality.CRITICAL -> "red"      // > 1000ms (seek required)
        }
    }

    /**
     * Get current correction method being used.
     *
     * @return Description of current correction method
     */
    fun getCorrectionMethod(): String {
        val drift = abs(_currentDrift.value)
        return when {
            drift < DRIFT_THRESHOLD_SPEED_ADJUST_MS -> "Speed Adjust"
            drift >= DRIFT_THRESHOLD_SEEK_MS -> "Seek"
            else -> "None"
        }
    }
}

