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
        private const val PREDICTIVE_SYNC_DELAY_MS = 500L // Start playback 500ms in future
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

        syncServer?.broadcastCommand(command)
        Log.i(TAG, "Broadcast play: position=$position, startTime=$targetStartTime")
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

        syncServer?.broadcastCommand(command)
        Log.i(TAG, "Broadcast pause")
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

        syncServer?.broadcastCommand(command)
        Log.i(TAG, "Broadcast seek: position=$position")
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

            // Auto-play when first client becomes ready
            if (!hasAutoPlayed && !wasReady) {
                hasAutoPlayed = true
                Log.i(TAG, "First client ready, auto-starting playback")
                scope.launch {
                    // Give client a moment to fully initialize
                    delay(500)
                    broadcastPlay(position = 0L)
                }
            }
        } else {
            _readyClients.value = _readyClients.value - clientId
        }

        Log.d(TAG, "Client $clientId: pos=${response.videoPosition}, drift=${response.drift}ms, ready=${response.isReady}")
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
     * Get the sync server instance for observing connected clients.
     *
     * @return SyncCommandServer instance or null if no session
     */
    fun getSyncServer(): SyncCommandServer? = syncServer
}

