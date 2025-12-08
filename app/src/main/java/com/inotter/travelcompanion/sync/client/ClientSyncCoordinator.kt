package com.inotter.travelcompanion.sync.client

import android.util.Log
import com.inotter.travelcompanion.playback.PlaybackCore
import com.inotter.travelcompanion.sync.models.SyncCommand
import com.inotter.travelcompanion.sync.models.SyncResponse
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
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // Clients
    private var httpClient: HttpMovieClient? = null
    private var syncClient: SyncCommandClient? = null
    
    // Session state
    private val _isInSession = MutableStateFlow(false)
    val isInSession: StateFlow<Boolean> = _isInSession.asStateFlow()
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    // Status update job
    private var statusUpdateJob: Job? = null
    
    // Last known master position (for drift calculation)
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
            
            // Start sending periodic status updates
            startStatusUpdates()
            
            // Wait for video to buffer, then send ready status
            scope.launch {
                delay(2000) // Wait 2 seconds for buffering
                _isReady.value = true
                sendStatusUpdate(isReady = true)
                Log.i(TAG, "Client ready")
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
            SyncCommand.ACTION_PLAY -> handlePlayCommand(command)
            SyncCommand.ACTION_PAUSE -> handlePauseCommand(command)
            SyncCommand.ACTION_SEEK -> handleSeekCommand(command)
            SyncCommand.ACTION_LOAD -> handleLoadCommand(command)
            SyncCommand.ACTION_SYNC_CHECK -> handleSyncCheckCommand(command)
            else -> Log.w(TAG, "Unknown command: ${command.action}")
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

        // Update last known master position
        lastMasterPosition = videoPosition
        lastMasterTimestamp = command.timestamp

        scope.launch {
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
        playbackCore.pause()
        Log.i(TAG, "Paused playback")
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

        playbackCore.seekTo(seekPosition)
        Log.i(TAG, "Seeked to position $seekPosition")
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
     * Send status update to master.
     */
    private fun sendStatusUpdate(isReady: Boolean = _isReady.value) {
        val client = syncClient
        if (client == null || !client.isConnected()) {
            return
        }

        val currentPosition = playbackCore.getCurrentPosition()
        val isPlaying = playbackCore.isPlaying()

        // Calculate drift from master
        val drift = calculateDrift(currentPosition)

        val response = SyncResponse(
            clientId = clientId,
            videoPosition = currentPosition,
            isPlaying = isPlaying,
            drift = drift,
            bufferPercentage = 0, // TODO: Get actual buffer percentage
            isReady = isReady,
            timestamp = System.currentTimeMillis()
        )

        client.sendResponse(response)
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

