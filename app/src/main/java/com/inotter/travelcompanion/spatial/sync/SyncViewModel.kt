package com.inotter.travelcompanion.spatial.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.inotter.travelcompanion.playback.PlaybackCore
import com.inotter.travelcompanion.sync.discovery.ConnectionManager
import com.inotter.travelcompanion.sync.discovery.ConnectionState
import com.inotter.travelcompanion.sync.discovery.ResolvedService
import com.inotter.travelcompanion.sync.models.DeviceInfo
import com.inotter.travelcompanion.sync.models.SyncCommand
import com.inotter.travelcompanion.sync.models.SyncSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * ViewModel for managing sync session state and UI.
 * 
 * Coordinates between ConnectionManager and UI components.
 * Provides simplified state for UI consumption.
 */
class SyncViewModel(
    private val context: Context,
    private val playbackCore: PlaybackCore
) {
    companion object {
        private const val TAG = "SyncViewModel"
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // Connection manager
    private var connectionManager: ConnectionManager? = null
    
    // UI State
    enum class SyncMode {
        IDLE,           // Not in sync mode
        MASTER,         // Hosting a session
        CLIENT          // Connected to a session
    }
    
    private val _syncMode = MutableStateFlow(SyncMode.IDLE)
    val syncMode: StateFlow<SyncMode> = _syncMode.asStateFlow()
    
    private val _currentSession = MutableStateFlow<SyncSession?>(null)
    val currentSession: StateFlow<SyncSession?> = _currentSession.asStateFlow()
    
    private val _connectedDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val connectedDevices: StateFlow<List<DeviceInfo>> = _connectedDevices.asStateFlow()
    
    private val _discoveredServices = MutableStateFlow<List<ResolvedService>>(emptyList())
    val discoveredServices: StateFlow<List<ResolvedService>> = _discoveredServices.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Playback loading state (for play/pause/seek operations)
    private val _isPlaybackLoading = MutableStateFlow(false)
    val isPlaybackLoading: StateFlow<Boolean> = _isPlaybackLoading.asStateFlow()

    init {
        // Initialize connection manager
        connectionManager = ConnectionManager(
            context = context,
            playbackCore = playbackCore
        )
        
        // Observe connection state
        scope.launch {
            connectionManager?.connectionState?.collect { state ->
                handleConnectionStateChange(state)
            }
        }
        
        // Observe session
        scope.launch {
            connectionManager?.currentSession?.collect { session ->
                _currentSession.value = session
            }
        }
        
        // Observe connected devices
        scope.launch {
            connectionManager?.connectedDevices?.collect { devices ->
                _connectedDevices.value = devices
            }
        }
    }
    
	    /**
	     * Create a new sync session as master.
	     *
	     * @param videoUri URI string for the video (SAF or MediaStore content URI)
	     */
	    fun createSession(videoUri: String, movieId: String, deviceName: String = "Quest") {
	        _isLoading.value = true
	        _errorMessage.value = null
	        
	        scope.launch {
	            try {
	                val manager = connectionManager
	                if (manager == null) {
	                    _errorMessage.value = "Sync connection manager not available"
	                    return@launch
	                }

	                // Ensure we are in a clean idle state before hosting.
	                // This stops any ongoing discovery or previous sessions that
	                // might leave ConnectionManager in a non-idle state.
	                manager.stopDiscovery()
	                manager.leaveSession()
	                manager.closeSession()

	                // Resolve a real file path for the HTTP server.
	                val resolvedPath = resolveVideoPathForSync(videoUri, movieId)
	                if (resolvedPath == null) {
	                    _errorMessage.value = "Video file not accessible for sync"
	                    return@launch
	                }

	                val session = manager.createSession(
	                    videoPath = resolvedPath,
	                    movieId = movieId,
	                    deviceName = deviceName
	                )

	                if (session != null) {
	                    _syncMode.value = SyncMode.MASTER

	                    // Set up listener for client commands to control local playback
	                    val masterCoordinator = manager.getMasterCoordinator()
	                    masterCoordinator?.setClientCommandListener { clientId, command ->
	                        handleClientCommand(clientId, command)
	                    }

	                    Log.i(TAG, "Session created with PIN: ${session.pinCode}")
	                } else {
	                    _errorMessage.value = "Failed to create session"
	                }
	            } catch (e: Exception) {
	                Log.e(TAG, "Error creating session", e)
	                _errorMessage.value = "Error: ${e.message}"
	            } finally {
	                _isLoading.value = false
	            }
	        }
	    }
    
    /**
     * Start discovering available sessions.
     */
    fun startDiscovery(pinCode: String? = null) {
        _isLoading.value = true
        _errorMessage.value = null
        
        connectionManager?.startDiscovery(pinCode)
        
        // Update discovered services periodically
        scope.launch {
            kotlinx.coroutines.delay(2000) // Wait for discovery
            val services = connectionManager?.getDiscoveredServices(pinCode) ?: emptyList()
            _discoveredServices.value = services
            _isLoading.value = false
            
            if (services.isEmpty()) {
                _errorMessage.value = "No sessions found"
            }
        }
    }
    
    /**
     * Stop discovering sessions.
     */
    fun stopDiscovery() {
        connectionManager?.stopDiscovery()
        _discoveredServices.value = emptyList()
    }

    /**
     * Join a discovered session.
     */
    fun joinSession(service: ResolvedService) {
        _isLoading.value = true
        _errorMessage.value = null

        scope.launch {
            try {
                val success = connectionManager?.connectToMaster(service) ?: false

                if (success) {
                    _syncMode.value = SyncMode.CLIENT
                    Log.i(TAG, "Joined session")
                } else {
                    _errorMessage.value = "Failed to join session"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error joining session", e)
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Join a session by PIN code.
     * Starts discovery, waits for the service to be found, then joins.
     */
    fun joinSessionByPin(pinCode: String) {
        _isLoading.value = true
        _errorMessage.value = null

        scope.launch {
            try {
                val manager = connectionManager
                if (manager == null) {
                    _errorMessage.value = "Sync connection manager not available"
                    _isLoading.value = false
                    return@launch
                }

                Log.i(TAG, "Starting discovery for PIN: $pinCode")
                manager.startDiscovery(pinCode)

                // Wait for service discovery with timeout
                var attempts = 0
                val maxAttempts = 10 // 10 attempts * 500ms = 5 seconds max
                var matchingService: ResolvedService? = null

                while (attempts < maxAttempts && matchingService == null) {
                    kotlinx.coroutines.delay(500)
                    val services = manager.getDiscoveredServices(pinCode)
                    Log.d(TAG, "Discovery attempt $attempts: found ${services.size} services")
                    matchingService = services.find { it.pinCode == pinCode }
                    attempts++

                    if (matchingService != null) {
                        Log.i(TAG, "Found matching service after ${attempts * 500}ms: $matchingService")
                        break
                    }
                }

                if (matchingService == null) {
                    _errorMessage.value = "No session found with PIN: $pinCode"
                    _isLoading.value = false
                    manager.stopDiscovery()
                    return@launch
                }

                // Join the session
                Log.i(TAG, "Joining session: $matchingService")
                val success = manager.connectToMaster(matchingService)

                if (success) {
                    _syncMode.value = SyncMode.CLIENT
                    Log.i(TAG, "Successfully joined session")
                } else {
                    _errorMessage.value = "Failed to join session"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error joining session by PIN", e)
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Leave the current session (client mode).
     */
    fun leaveSession() {
        connectionManager?.leaveSession()
        _syncMode.value = SyncMode.IDLE
        _errorMessage.value = null
    }

    /**
     * Close the current session (master mode).
     */
    fun closeSession() {
        connectionManager?.closeSession()
        _syncMode.value = SyncMode.IDLE
        _errorMessage.value = null
    }

    /**
     * Broadcast start command for initial playback (master only).
     * This should be called when the host first starts playback.
     * Also controls the local PlaybackCore with predictive sync delay.
     */
    fun start(position: Long = 0L) {
        if (_syncMode.value != SyncMode.MASTER) {
            Log.w(TAG, "Cannot start: not in master mode")
            return
        }

        // Set loading state
        _isPlaybackLoading.value = true

        // Broadcast start command to clients first
        val masterCoordinator = connectionManager?.getMasterCoordinator()
        masterCoordinator?.broadcastStart(position)

        // Master also waits for the same targetStartTime as clients
        scope.launch {
            // Get the predictive delay (default 100ms for local network)
            val predictiveDelayMs = 100L // Same as PREDICTIVE_SYNC_DELAY_MS in MasterSyncCoordinator

            Log.d(TAG, "Master waiting ${predictiveDelayMs}ms before starting playback")
            delay(predictiveDelayMs)

            // Wait for clients to be ready (with timeout)
            masterCoordinator?.waitForClientsReady(timeoutMs = 5000)

            // Now start local playback synchronized with clients
            playbackCore.seekTo(position)
            playbackCore.play()

            Log.i(TAG, "Master started playback at position $position")

            // Clear loading state
            _isPlaybackLoading.value = false
        }
    }

    /**
     * Broadcast play command (master only).
     * Also controls the local PlaybackCore with predictive sync delay.
     */
    fun play(position: Long) {
        if (_syncMode.value != SyncMode.MASTER) {
            Log.w(TAG, "Cannot play: not in master mode")
            return
        }

        // Set loading state
        _isPlaybackLoading.value = true

        // Broadcast play command to clients first
        val masterCoordinator = connectionManager?.getMasterCoordinator()
        masterCoordinator?.broadcastPlay(position)

        // Master also waits for the same targetStartTime as clients
        scope.launch {
            // Get the predictive delay (default 100ms for local network)
            val predictiveDelayMs = 100L // Same as PREDICTIVE_SYNC_DELAY_MS in MasterSyncCoordinator

            Log.d(TAG, "Master waiting ${predictiveDelayMs}ms before resuming playback")
            delay(predictiveDelayMs)

            // Wait for clients to be ready (with timeout)
            masterCoordinator?.waitForClientsReady(timeoutMs = 5000)

            // Now resume local playback synchronized with clients
            playbackCore.seekTo(position)
            playbackCore.play()

            Log.i(TAG, "Master resumed playback at position $position")

            // Clear loading state
            _isPlaybackLoading.value = false
        }
    }

    /**
     * Broadcast pause command (master only).
     * Also controls the local PlaybackCore.
     */
    fun pause() {
        if (_syncMode.value != SyncMode.MASTER) {
            Log.w(TAG, "Cannot pause: not in master mode")
            return
        }

        // Set loading state
        _isPlaybackLoading.value = true

        // Control local playback
        playbackCore.pause()

        // Broadcast to clients
        connectionManager?.getMasterCoordinator()?.broadcastPause()

        // Pause is immediate, clear loading state after a short delay
        scope.launch {
            delay(100) // Small delay to show the loading indicator
            _isPlaybackLoading.value = false
        }
    }

    /**
     * Broadcast seek command (master only).
     * Also controls the local PlaybackCore.
     */
    fun seekTo(position: Long) {
        if (_syncMode.value != SyncMode.MASTER) {
            Log.w(TAG, "Cannot seek: not in master mode")
            return
        }

        // Set loading state
        _isPlaybackLoading.value = true

        // Control local playback
        playbackCore.seekTo(position)

        // Broadcast to clients
        connectionManager?.getMasterCoordinator()?.broadcastSeek(position)

        // Wait for clients to buffer at new position
        scope.launch {
            val masterCoordinator = connectionManager?.getMasterCoordinator()

            // Wait for clients to be ready (with timeout)
            masterCoordinator?.waitForClientsReady(timeoutMs = 5000)

            // Clear loading state
            _isPlaybackLoading.value = false
        }
    }

    /**
     * Handle command from a client (for bidirectional sync).
     * Controls the local PlaybackCore when clients send commands.
     */
    private fun handleClientCommand(clientId: String, command: SyncCommand) {
        Log.i(TAG, "Handling client command from $clientId: ${command.action}")

        when (command.action) {
            SyncCommand.ACTION_PLAY -> {
                // Client wants to play - ALWAYS use the master's current position
                // This ensures all clients sync to where the host is, not where the client was
                val masterPosition = playbackCore.getCurrentPosition()

                // Set loading state
                _isPlaybackLoading.value = true

                // Broadcast play command to all clients with master's position
                val masterCoordinator = connectionManager?.getMasterCoordinator()
                masterCoordinator?.broadcastPlay(masterPosition)

                scope.launch {
                    // Get the predictive delay (default 100ms for local network)
                    val predictiveDelayMs = 100L

                    Log.d(TAG, "Master waiting ${predictiveDelayMs}ms before resuming playback (client-initiated)")
                    delay(predictiveDelayMs)

                    // Wait for clients to be ready (with timeout)
                    masterCoordinator?.waitForClientsReady(timeoutMs = 5000)

                    // Now resume local playback synchronized with clients
                    playbackCore.seekTo(masterPosition)
                    playbackCore.play()

                    Log.i(TAG, "Master resumed playback at position $masterPosition (client-initiated)")

                    // Clear loading state
                    _isPlaybackLoading.value = false
                }
            }
            SyncCommand.ACTION_PAUSE -> {
                // Client wants to pause - pause immediately
                _isPlaybackLoading.value = true

                playbackCore.pause()
                Log.i(TAG, "Master paused playback (client-initiated)")

                // Pause is immediate, clear loading state after a short delay
                scope.launch {
                    delay(100)
                    _isPlaybackLoading.value = false
                }
            }
            SyncCommand.ACTION_SEEK -> {
                // Client wants to seek
                val position = command.seekPosition ?: 0L

                _isPlaybackLoading.value = true

                playbackCore.seekTo(position)
                Log.i(TAG, "Master seeked to position $position (client-initiated)")

                // Wait for clients to buffer at new position
                scope.launch {
                    val masterCoordinator = connectionManager?.getMasterCoordinator()
                    masterCoordinator?.waitForClientsReady(timeoutMs = 5000)

                    _isPlaybackLoading.value = false
                }
            }
        }
    }

    /**
     * Check if in master mode.
     */
    fun isMaster(): Boolean = _syncMode.value == SyncMode.MASTER

    /**
     * Check if in client mode.
     */
    fun isClient(): Boolean = _syncMode.value == SyncMode.CLIENT

    /**
     * Check if in any sync mode.
     */
    fun isInSyncMode(): Boolean = _syncMode.value != SyncMode.IDLE

    /**
     * Get the underlying PlaybackCore for direct access (e.g., for sync client player).
     */
    fun getPlaybackCore(): PlaybackCore = playbackCore

    /**
     * Get the ConnectionManager for direct access (e.g., for sending client commands).
     */
    fun getConnectionManager(): ConnectionManager? = connectionManager

    /**
     * Clear error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Handle connection state changes.
     */
    private fun handleConnectionStateChange(state: ConnectionState) {
        when (state) {
            is ConnectionState.Idle -> {
                _syncMode.value = SyncMode.IDLE
                _isLoading.value = false
            }
            is ConnectionState.CreatingSession -> {
                _isLoading.value = true
            }
            is ConnectionState.MasterSession -> {
                _syncMode.value = SyncMode.MASTER
                _isLoading.value = false
            }
            is ConnectionState.DiscoveringServices -> {
                _isLoading.value = true
            }
            is ConnectionState.JoiningSession -> {
                _isLoading.value = true
            }
            is ConnectionState.ClientSession -> {
                _syncMode.value = SyncMode.CLIENT
                _isLoading.value = false
            }
            is ConnectionState.Error -> {
                _errorMessage.value = state.message
                _isLoading.value = false
            }
        }
    }

    /**
     * Cleanup resources.
     */
    fun onCleared() {
        // Always stop discovery to return ConnectionManager to idle state
        stopDiscovery()

        if (_syncMode.value == SyncMode.MASTER) {
            closeSession()
        } else if (_syncMode.value == SyncMode.CLIENT) {
            leaveSession()
        }
    }
    
	    /**
	     * Resolve a playable file path for sync from a URI string.
	     *
	     * For content:// URIs, this copies the content into the app's cache directory
	     * so that the HTTP server can stream it from a regular File.
	     */
	    private suspend fun resolveVideoPathForSync(videoUri: String, movieId: String): String? {
	        return withContext(Dispatchers.IO) {
	            try {
	                val uri = Uri.parse(videoUri)
	                val scheme = uri.scheme?.lowercase()

	                when (scheme) {
	                    null, "file" -> uri.path
	                    "content" -> copyContentUriToCache(uri, movieId)?.absolutePath
	                    else -> {
	                        Log.w(TAG, "Unsupported URI scheme for sync: $scheme")
	                        null
	                    }
	                }
	            } catch (e: Exception) {
	                Log.e(TAG, "Failed to resolve video path for sync: $videoUri", e)
	                null
	            }
	        }
	    }

	    /**
	     * Copy a content:// URI into a temporary file under cacheDir for streaming.
	     */
	    private fun copyContentUriToCache(uri: Uri, movieId: String): File? {
	        return try {
	            val resolver = context.contentResolver
	            val extension = "mp4" // Best-effort; servlet defaults to video/mp4 for unknown
	            val outFile = File(context.cacheDir, "sync_${'$'}movieId.${'$'}extension")
	
	            resolver.openInputStream(uri)?.use { input ->
	                FileOutputStream(outFile).use { output ->
	                    input.copyTo(output)
	                }
	                outFile
	            } ?: run {
	                Log.e(TAG, "Failed to open input stream for URI: $uri")
	                null
	            }
	        } catch (e: Exception) {
	            Log.e(TAG, "Failed to cache content URI for sync: $uri", e)
	            null
	        }
	    }
}

