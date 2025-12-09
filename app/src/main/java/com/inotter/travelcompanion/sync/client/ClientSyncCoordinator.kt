package com.inotter.travelcompanion.sync.client

import android.util.Log
import com.inotter.travelcompanion.playback.PlaybackCore
import com.inotter.travelcompanion.sync.models.SyncCommand
import com.inotter.travelcompanion.sync.models.SyncResponse
import com.inotter.travelcompanion.sync.protocol.PlaybackSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Client coordinator that integrates HTTP video client and WebSocket sync client.
 * 
 * Manages the complete sync session on a client device:
 * - Connects to master's HTTP server for video streaming
 * - Connects to master's WebSocket server for sync commands
 * - Executes received sync commands (play/pause/seek)
 * - Sends periodic status updates to master
 * - Implements predictive sync with future timestamps
 * 
 * Usage:
 * ```
 * val coordinator = ClientSyncCoordinator(
 *     playbackCore = playbackCore,
 *     clientId = "quest-client-1"
 * )
 * coordinator.joinSession(
 *     httpUrl = "http://192.168.43.100:8080/video/movie1",
 *     wsUrl = "ws://192.168.43.100:8081/sync"
 * )
 * // Commands are handled automatically
 * coordinator.leaveSession()
 * ```
 */
class ClientSyncCoordinator(
    private val playbackCore: PlaybackCore,
    private val clientId: String = UUID.randomUUID().toString()
) {
    companion object {
        private const val TAG = "ClientSyncCoordinator"
        private const val STATUS_UPDATE_INTERVAL_MS = 5000L // Send status every 5 seconds
        private const val BUFFER_CHECK_INTERVAL_MS = 500L // Check buffer every 500ms
        private const val MIN_BUFFER_PERCENTAGE = 10 // Minimum 10% buffer before ready
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Clients
    private var httpClient: HttpMovieClient? = null
    private var syncClient: SyncCommandClient? = null

    // Drift correction manager
    private val syncManager: PlaybackSyncManager = PlaybackSyncManager(playbackCore)

    // Session state
    private val _isInSession = MutableStateFlow(false)
    val isInSession: StateFlow<Boolean> = _isInSession.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // Status update job
    private var statusUpdateJob: Job? = null

    // Last known master position (for drift calculation - kept for status reporting)
    private var lastMasterPosition: Long = 0L
    private var lastMasterTimestamp: Long = 0L
    
    /**
     * Join a sync session.
     * 
     * @param httpUrl HTTP URL for video streaming
     * @param wsUrl WebSocket URL for sync commands
     * @return true if joined successfully
     */
    fun joinSession(httpUrl: String, wsUrl: String): Boolean {
        if (_isInSession.value) {
            Log.w(TAG, "Already in session")
            return true
        }
        
        try {
            // Create HTTP client
            val httpCli = HttpMovieClient(playbackCore)
            if (!httpCli.connectToServer(httpUrl)) {
                Log.e(TAG, "Failed to connect to HTTP server")
                return false
            }
            httpClient = httpCli
            
            // Create WebSocket client
            val syncCli = SyncCommandClient(clientId)
            syncCli.setCommandListener { command ->
                handleCommand(command)
            }
            
            // Extract server IP and port from WebSocket URL
            val wsUri = java.net.URI(wsUrl)
            val serverIp = wsUri.host
            val wsPort = wsUri.port
            
            if (!syncCli.connect(serverIp, wsPort)) {
                Log.e(TAG, "Failed to connect to WebSocket server")
                httpCli.disconnect()
                return false
            }
            syncClient = syncCli

            _isInSession.value = true

            // Start drift monitoring for automatic correction
            syncManager.startMonitoring()
            Log.i(TAG, "Started drift monitoring")

            // Start sending periodic status updates
            startStatusUpdates()

            // Start buffering immediately and wait for readiness
            scope.launch {
                Log.i(TAG, "Starting buffering...")
                waitForBufferReadiness()
                _isReady.value = true
                sendStatusUpdate(isReady = true)
                Log.i(TAG, "Client ready - buffering complete")
            }

            Log.i(TAG, "Joined session: HTTP=$httpUrl, WS=$wsUrl")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to join session", e)
            leaveSession()
            return false
        }
    }
    
    /**
     * Leave the current session.
     */
    fun leaveSession() {
        try {
            // Stop drift monitoring
            syncManager.stopMonitoring()
            Log.i(TAG, "Stopped drift monitoring")

            statusUpdateJob?.cancel()
            syncClient?.disconnect()
            httpClient?.disconnect()

            syncClient = null
            httpClient = null
            _isInSession.value = false
            _isReady.value = false

            Log.i(TAG, "Left session")
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving session", e)
        }
    }

    /**
     * Handle sync command from master.
     */
    private fun handleCommand(command: SyncCommand) {
        Log.d(TAG, "Received command: ${command.action}")

        when (command.action) {
            SyncCommand.ACTION_START -> handleStartCommand(command)
            SyncCommand.ACTION_PLAY -> handlePlayCommand(command)
            SyncCommand.ACTION_PAUSE -> handlePauseCommand(command)
            SyncCommand.ACTION_SEEK -> handleSeekCommand(command)
            SyncCommand.ACTION_LOAD -> handleLoadCommand(command)
            SyncCommand.ACTION_SYNC_CHECK -> handleSyncCheckCommand(command)
            else -> Log.w(TAG, "Unknown command: ${command.action}")
        }
    }

    /**
     * Handle start command for initial playback with predictive sync.
     * This is sent when the host first starts watching together.
     */
    private fun handleStartCommand(command: SyncCommand) {
        val targetStartTime = command.targetStartTime
        val videoPosition = command.videoPosition

        if (targetStartTime == null || videoPosition == null) {
            Log.w(TAG, "Invalid start command: missing targetStartTime or videoPosition")
            return
        }

        // Update last known master position (for status reporting)
        lastMasterPosition = videoPosition
        lastMasterTimestamp = command.timestamp

        // Update sync manager for drift correction
        syncManager.updateMasterPosition(videoPosition, command.timestamp, isPlaying = true)

        scope.launch(Dispatchers.Main) {
            // Calculate delay until target start time
            val currentTime = System.currentTimeMillis()
            val delayMs = targetStartTime - currentTime

            if (delayMs > 0) {
                Log.d(TAG, "Waiting ${delayMs}ms before starting initial playback")
                delay(delayMs)
            }

            // Seek to position and start playback
            playbackCore.seekTo(videoPosition)
            playbackCore.play()

            Log.i(TAG, "Started initial playback at position $videoPosition")
        }
    }

    /**
     * Handle play command with predictive sync.
     */
    private fun handlePlayCommand(command: SyncCommand) {
        val targetStartTime = command.targetStartTime
        val videoPosition = command.videoPosition

        if (targetStartTime == null || videoPosition == null) {
            Log.w(TAG, "Invalid play command: missing targetStartTime or videoPosition")
            return
        }

        // Update last known master position (for status reporting)
        lastMasterPosition = videoPosition
        lastMasterTimestamp = command.timestamp

        // Update sync manager for drift correction
        syncManager.updateMasterPosition(videoPosition, command.timestamp, isPlaying = true)

        scope.launch(Dispatchers.Main) {
            // Calculate delay until target start time
            val currentTime = System.currentTimeMillis()
            val delayMs = targetStartTime - currentTime

            if (delayMs > 0) {
                Log.d(TAG, "Waiting ${delayMs}ms before starting playback")
                delay(delayMs)
            }

            // Seek to position and start playback
            playbackCore.seekTo(videoPosition)
            playbackCore.play()

            Log.i(TAG, "Started playback at position $videoPosition")
        }
    }

    /**
     * Handle pause command.
     */
    private fun handlePauseCommand(command: SyncCommand) {
        scope.launch(Dispatchers.Main) {
            // Update sync manager - master is now paused
            val videoPosition = command.videoPosition ?: playbackCore.getCurrentPosition()
            syncManager.updateMasterPosition(videoPosition, command.timestamp, isPlaying = false)

            playbackCore.pause()
            Log.i(TAG, "Paused playback")
        }
    }

    /**
     * Handle seek command.
     */
    private fun handleSeekCommand(command: SyncCommand) {
        val seekPosition = command.seekPosition
        if (seekPosition == null) {
            Log.w(TAG, "Invalid seek command: missing seekPosition")
            return
        }

        scope.launch(Dispatchers.Main) {
            // Update sync manager with new position
            val isPlaying = playbackCore.isPlaying()
            syncManager.updateMasterPosition(seekPosition, command.timestamp, isPlaying = isPlaying)

            playbackCore.seekTo(seekPosition)
            Log.i(TAG, "Seeked to position $seekPosition")
        }
    }

    /**
     * Handle load command.
     */
    private fun handleLoadCommand(command: SyncCommand) {
        // Load command is handled during joinSession
        Log.d(TAG, "Load command received (already loaded)")
    }

    /**
     * Handle sync check command.
     */
    private fun handleSyncCheckCommand(command: SyncCommand) {
        sendStatusUpdate()
    }

    /**
     * Start sending periodic status updates to master.
     */
    private fun startStatusUpdates() {
        statusUpdateJob?.cancel()
        statusUpdateJob = scope.launch {
            while (_isInSession.value) {
                sendStatusUpdate()
                delay(STATUS_UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * Wait for video to buffer before marking as ready.
     * Monitors buffer percentage and readiness state.
     */
    private suspend fun waitForBufferReadiness() {
        var bufferPercentage = 0
        var checkCount = 0
        val maxChecks = 60 // Maximum 30 seconds (60 * 500ms)

        while (checkCount < maxChecks) {
            bufferPercentage = playbackCore.getBufferPercentage()
            val isReady = playbackCore.isReadyToPlay()

            Log.d(TAG, "Buffer check: $bufferPercentage%, ready=$isReady")

            // Ready when either:
            // 1. Player reports ready (5+ seconds buffered), OR
            // 2. At least MIN_BUFFER_PERCENTAGE buffered
            if (isReady || bufferPercentage >= MIN_BUFFER_PERCENTAGE) {
                Log.i(TAG, "Buffer ready: $bufferPercentage%")
                return
            }

            delay(BUFFER_CHECK_INTERVAL_MS)
            checkCount++
        }

        // Timeout - proceed anyway but log warning
        Log.w(TAG, "Buffer readiness timeout after 30s (buffer: $bufferPercentage%)")
    }

    /**
     * Send status update to master.
     */
    private fun sendStatusUpdate(isReady: Boolean = _isReady.value) {
        val client = syncClient
        if (client == null || !client.isConnected()) {
            return
        }

        scope.launch(Dispatchers.Main) {
            val currentPosition = playbackCore.getCurrentPosition()
            val isPlaying = playbackCore.isPlaying()

            // Calculate drift from master
            val drift = calculateDrift(currentPosition)

            // Get actual buffer percentage
            val bufferPercentage = playbackCore.getBufferPercentage()

            val response = SyncResponse(
                clientId = clientId,
                videoPosition = currentPosition,
                isPlaying = isPlaying,
                drift = drift,
                bufferPercentage = bufferPercentage,
                isReady = isReady,
                timestamp = System.currentTimeMillis()
            )

            client.sendResponse(response)
        }
    }

    /**
     * Calculate drift from master position.
     */
    private fun calculateDrift(currentPosition: Long): Long {
        if (lastMasterTimestamp == 0L) {
            return 0L
        }

        // Calculate expected master position based on time elapsed
        val timeElapsed = System.currentTimeMillis() - lastMasterTimestamp
        val expectedMasterPosition = lastMasterPosition + timeElapsed

        // Drift = client position - expected master position
        // Positive = ahead, negative = behind
        return currentPosition - expectedMasterPosition
    }

    /**
     * Send play command to master (for bidirectional sync).
     * The master will rebroadcast this to all clients.
     */
    fun sendPlayCommand(position: Long) {
        val client = syncClient
        if (client == null || !client.isConnected()) {
            Log.w(TAG, "Cannot send play command: not connected")
            return
        }

        val command = SyncCommand(
            action = SyncCommand.ACTION_PLAY,
            timestamp = System.currentTimeMillis(),
            videoPosition = position,
            senderId = clientId
        )

        client.sendCommand(command)
        Log.i(TAG, "Sent play command: position=$position")
    }

    /**
     * Send pause command to master (for bidirectional sync).
     * The master will rebroadcast this to all clients.
     */
    fun sendPauseCommand() {
        val client = syncClient
        if (client == null || !client.isConnected()) {
            Log.w(TAG, "Cannot send pause command: not connected")
            return
        }

        val command = SyncCommand(
            action = SyncCommand.ACTION_PAUSE,
            timestamp = System.currentTimeMillis(),
            senderId = clientId
        )

        client.sendCommand(command)
        Log.i(TAG, "Sent pause command")
    }

    /**
     * Send seek command to master (for bidirectional sync).
     * The master will rebroadcast this to all clients.
     */
    fun sendSeekCommand(position: Long) {
        val client = syncClient
        if (client == null || !client.isConnected()) {
            Log.w(TAG, "Cannot send seek command: not connected")
            return
        }

        val command = SyncCommand(
            action = SyncCommand.ACTION_SEEK,
            timestamp = System.currentTimeMillis(),
            seekPosition = position,
            senderId = clientId
        )

        client.sendCommand(command)
        Log.i(TAG, "Sent seek command: position=$position")
    }
}

