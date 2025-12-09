package com.inotter.travelcompanion.sync.server

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

/**
 * Master coordinator that integrates HTTP video server and WebSocket sync server.
 * 
 * Manages the complete sync session on the master device:
 * - Starts HTTP server for video streaming
 * - Starts WebSocket server for sync commands
 * - Broadcasts play/pause/seek commands to all clients
 * - Tracks client states and readiness
 * - Implements predictive sync with future timestamps
 * 
 * Usage:
 * ```
 * val coordinator = MasterSyncCoordinator(context, deviceId = "quest-master")
 * val session = coordinator.startSession(
 *     videoFile = File("/path/to/movie.mp4"),
 *     movieId = "movie1"
 * )
 * coordinator.waitForClientsReady(timeout = 10000)
 * coordinator.broadcastPlay(position = 0)
 * coordinator.stopSession()
 * ```
 */
class MasterSyncCoordinator(
    private val context: Context,
    private val deviceId: String = UUID.randomUUID().toString()
) {
    companion object {
        private const val TAG = "MasterSyncCoordinator"
        private const val PREDICTIVE_SYNC_DELAY_MS = 100L // Start playback 100ms in future (local network)
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    // Servers
    private var httpServer: HttpMovieServer? = null
    private var syncServer: SyncCommandServer? = null
    
    // Session state
    data class SessionInfo(
        val sessionId: String,
        val movieId: String,
        val httpPort: Int,
        val wsPort: Int,
        val videoFile: File
    )
    
    private val _currentSession = MutableStateFlow<SessionInfo?>(null)
    val currentSession: StateFlow<SessionInfo?> = _currentSession.asStateFlow()
    
    // Client states
    private val clientStates = mutableMapOf<String, SyncResponse>()
    private val _readyClients = MutableStateFlow<Set<String>>(emptySet())
    val readyClients: StateFlow<Set<String>> = _readyClients.asStateFlow()

    // Auto-play state
    private var hasAutoPlayed = false

    // Callback for client commands (to notify SyncViewModel to control local playback)
    private var clientCommandListener: ((clientId: String, command: SyncCommand) -> Unit)? = null

    /**
     * Set listener for client commands.
     * This allows the SyncViewModel to control local playback when clients send commands.
     */
    fun setClientCommandListener(listener: (clientId: String, command: SyncCommand) -> Unit) {
        clientCommandListener = listener
    }

    /**
     * Start a sync session.
     * 
     * @param videoFile Video file to stream
     * @param movieId Unique movie identifier
     * @param httpPort HTTP server port (default: 8080)
     * @param wsPort WebSocket server port (default: 8081)
     * @return Session info if started successfully, null otherwise
     */
    fun startSession(
        videoFile: File,
        movieId: String,
        httpPort: Int = HttpMovieServer.DEFAULT_PORT,
        wsPort: Int = SyncCommandServer.DEFAULT_PORT
    ): SessionInfo? {
        if (_currentSession.value != null) {
            Log.w(TAG, "Session already active")
            return _currentSession.value
        }
        
        try {
            // Start HTTP server
            val httpSrv = HttpMovieServer(context)
            httpSrv.start(httpPort)
            val actualHttpPort = httpSrv.getPort()

            if (!httpSrv.isRunning.value) {
                Log.e(TAG, "Failed to start HTTP server")
                return null
            }

            // Register video
            if (!httpSrv.registerVideo(movieId, videoFile)) {
                Log.e(TAG, "Failed to register video")
                httpSrv.stop()
                return null
            }

            httpServer = httpSrv

            // Start WebSocket server
            val syncSrv = SyncCommandServer()
            if (!syncSrv.start(wsPort)) {
                Log.e(TAG, "Failed to start WebSocket server")
                httpSrv.stop()
                return null
            }
            
            // Set up response listener
            syncSrv.setResponseListener { clientId, response ->
                handleClientResponse(clientId, response)
            }

            // Set up command listener for bidirectional sync
            syncSrv.setCommandListener { clientId, command ->
                handleClientCommand(clientId, command)
            }

            syncServer = syncSrv
            
            // Create session info
            val sessionId = UUID.randomUUID().toString()
            val session = SessionInfo(
                sessionId = sessionId,
                movieId = movieId,
                httpPort = actualHttpPort,
                wsPort = wsPort,
                videoFile = videoFile
            )
            
            _currentSession.value = session
            Log.i(TAG, "Session started: $sessionId (HTTP:$actualHttpPort, WS:$wsPort)")
            
            return session
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start session", e)
            stopSession()
            return null
        }
    }

    /**
     * Stop the current session.
     */
    fun stopSession() {
        try {
            syncServer?.stop()
            httpServer?.stop()

            syncServer = null
            httpServer = null
            _currentSession.value = null
            clientStates.clear()
            _readyClients.value = emptySet()
            hasAutoPlayed = false

            Log.i(TAG, "Session stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping session", e)
        }
    }

    /**
     * Broadcast start command for initial playback (first time only).
     * This is sent automatically when the master player screen loads.
     *
     * @param position Video position in milliseconds (typically 0)
     * @param predictiveDelayMs Delay before starting playback (default: 500ms)
     */
    fun broadcastStart(position: Long = 0L, predictiveDelayMs: Long = PREDICTIVE_SYNC_DELAY_MS) {
        val session = _currentSession.value
        if (session == null) {
            Log.w(TAG, "Cannot broadcast start: no active session")
            return
        }

        val targetStartTime = System.currentTimeMillis() + predictiveDelayMs
        val command = SyncCommand(
            action = SyncCommand.ACTION_START,
            timestamp = System.currentTimeMillis(),
            targetStartTime = targetStartTime,
            videoPosition = position,
            senderId = deviceId
        )

        // Launch on IO dispatcher to avoid NetworkOnMainThreadException
        scope.launch {
            syncServer?.broadcastCommand(command)
            Log.i(TAG, "Broadcast start: position=$position, startTime=$targetStartTime")
        }
    }

    /**
     * Broadcast play command with predictive sync.
     *
     * @param position Video position in milliseconds
     * @param predictiveDelayMs Delay before starting playback (default: 500ms)
     */
    fun broadcastPlay(position: Long, predictiveDelayMs: Long = PREDICTIVE_SYNC_DELAY_MS) {
        val session = _currentSession.value
        if (session == null) {
            Log.w(TAG, "Cannot broadcast play: no active session")
            return
        }

        val targetStartTime = System.currentTimeMillis() + predictiveDelayMs
        val command = SyncCommand(
            action = SyncCommand.ACTION_PLAY,
            timestamp = System.currentTimeMillis(),
            targetStartTime = targetStartTime,
            videoPosition = position,
            senderId = deviceId
        )

        // Launch on IO dispatcher to avoid NetworkOnMainThreadException
        scope.launch {
            syncServer?.broadcastCommand(command)
            Log.i(TAG, "Broadcast play: position=$position, startTime=$targetStartTime")
        }
    }

    /**
     * Broadcast pause command.
     */
    fun broadcastPause() {
        val session = _currentSession.value
        if (session == null) {
            Log.w(TAG, "Cannot broadcast pause: no active session")
            return
        }

        val command = SyncCommand(
            action = SyncCommand.ACTION_PAUSE,
            timestamp = System.currentTimeMillis(),
            senderId = deviceId
        )

        // Launch on IO dispatcher to avoid NetworkOnMainThreadException
        scope.launch {
            syncServer?.broadcastCommand(command)
            Log.i(TAG, "Broadcast pause")
        }
    }

    /**
     * Broadcast seek command.
     *
     * @param position Target position in milliseconds
     */
    fun broadcastSeek(position: Long) {
        val session = _currentSession.value
        if (session == null) {
            Log.w(TAG, "Cannot broadcast seek: no active session")
            return
        }

        val command = SyncCommand(
            action = SyncCommand.ACTION_SEEK,
            timestamp = System.currentTimeMillis(),
            seekPosition = position,
            senderId = deviceId
        )

        // Launch on IO dispatcher to avoid NetworkOnMainThreadException
        scope.launch {
            syncServer?.broadcastCommand(command)
            Log.i(TAG, "Broadcast seek: position=$position")
        }
    }

    /**
     * Wait for all connected clients to report ready.
     *
     * @param timeoutMs Timeout in milliseconds (default: 10 seconds)
     * @return true if all clients are ready, false if timeout
     */
    suspend fun waitForClientsReady(timeoutMs: Long = 10000): Boolean {
        val server = syncServer
        if (server == null) {
            Log.w(TAG, "Cannot wait for clients: no active session")
            return false
        }

        return try {
            withTimeout(timeoutMs) {
                while (true) {
                    val connectedCount = server.getClientCount()
                    val readyCount = _readyClients.value.size

                    if (connectedCount > 0 && readyCount == connectedCount) {
                        Log.i(TAG, "All $connectedCount clients ready")
                        return@withTimeout true
                    }

                    delay(100)
                }
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Timeout waiting for clients to be ready")
            false
        }
    }

    /**
     * Handle response from a client.
     */
    private fun handleClientResponse(clientId: String, response: SyncResponse) {
        clientStates[clientId] = response

        // Update ready clients
        val wasReady = _readyClients.value.contains(clientId)
        if (response.isReady) {
            _readyClients.value = _readyClients.value + clientId

            // DISABLED: Auto-play when first client becomes ready
            // This was causing the client to auto-play without waiting for the host to manually start
            // if (!hasAutoPlayed && !wasReady) {
            //     hasAutoPlayed = true
            //     Log.i(TAG, "First client ready, auto-starting playback")
            //     scope.launch {
            //         // Give client a moment to fully initialize
            //         delay(500)
            //         broadcastPlay(position = 0L)
            //     }
            // }

            if (!wasReady) {
                Log.i(TAG, "Client $clientId is ready (buffer: ${response.bufferPercentage}%, total ready: ${_readyClients.value.size})")
            }
        } else {
            _readyClients.value = _readyClients.value - clientId
        }

        Log.d(TAG, "Client $clientId: pos=${response.videoPosition}, drift=${response.drift}ms, buffer=${response.bufferPercentage}%, ready=${response.isReady}")
    }

    /**
     * Handle command from a client (for bidirectional sync).
     * Notifies the listener to control local playback and rebroadcast with correct position.
     * For PLAY commands, the listener should get the master's position and call broadcastPlay.
     * For PAUSE and SEEK, we rebroadcast immediately.
     */
    private fun handleClientCommand(clientId: String, command: SyncCommand) {
        Log.i(TAG, "Received command from client $clientId: ${command.action}")

        when (command.action) {
            SyncCommand.ACTION_PLAY -> {
                // For PLAY, notify listener first - it will get master's position and broadcast
                // This ensures all clients sync to the master's current position, not the client's
                clientCommandListener?.invoke(clientId, command)
            }
            SyncCommand.ACTION_PAUSE -> {
                // Rebroadcast pause command to all clients
                broadcastPause()

                // Notify listener to control local playback
                clientCommandListener?.invoke(clientId, command)
            }
            SyncCommand.ACTION_SEEK -> {
                // Rebroadcast seek command to all clients
                val position = command.seekPosition ?: 0L
                broadcastSeek(position)

                // Notify listener to control local playback
                clientCommandListener?.invoke(clientId, command)
            }
            else -> {
                Log.w(TAG, "Unknown command from client: ${command.action}")
            }
        }
    }

    /**
     * Get HTTP URL for the current video.
     *
     * @param serverIp Server IP address
     * @return HTTP URL or null if no session
     */
    fun getVideoUrl(serverIp: String): String? {
        val session = _currentSession.value ?: return null
        return httpServer?.getVideoUrl(session.movieId, serverIp)
    }

    /**
     * Get WebSocket URL for sync commands.
     *
     * @param serverIp Server IP address
     * @return WebSocket URL or null if no session
     */
    fun getWebSocketUrl(serverIp: String): String? {
        val session = _currentSession.value ?: return null
        return "ws://$serverIp:${session.wsPort}/sync"
    }

    /**
     * Get the number of connected clients.
     */
    fun getConnectedClientCount(): Int {
        return syncServer?.getClientCount() ?: 0
    }

    /**
     * Get the number of ready clients.
     */
    fun getReadyClientCount(): Int {
        return _readyClients.value.size
    }

    /**
     * Get all client states.
     */
    fun getClientStates(): Map<String, SyncResponse> {
        return clientStates.toMap()
    }

    /**
     * Get minimum buffer percentage across all clients.
     * Useful for determining if all clients have sufficient buffer.
     *
     * @return Minimum buffer percentage, or 100 if no clients connected
     */
    fun getMinimumClientBuffer(): Int {
        if (clientStates.isEmpty()) return 100
        return clientStates.values.minOfOrNull { it.bufferPercentage } ?: 0
    }

    /**
     * Check if all clients have sufficient buffer for playback.
     *
     * @param minBufferPercentage Minimum required buffer percentage (default: 10%)
     * @return true if all clients have sufficient buffer
     */
    fun allClientsBuffered(minBufferPercentage: Int = 10): Boolean {
        if (clientStates.isEmpty()) return false
        return clientStates.values.all { it.bufferPercentage >= minBufferPercentage }
    }

    /**
     * Check if session is active.
     */
    fun isSessionActive(): Boolean {
        return _currentSession.value != null
    }

    /**
     * Get the device ID.
     */
    fun getDeviceId(): String = deviceId

    /**
     * Get the HTTP server.
     */
    fun getHttpServer(): HttpMovieServer? = httpServer

    /**
     * Get the sync server instance for observing connected clients.
     *
     * @return SyncCommandServer instance or null if no session
     */
    fun getSyncServer(): SyncCommandServer? = syncServer
}

