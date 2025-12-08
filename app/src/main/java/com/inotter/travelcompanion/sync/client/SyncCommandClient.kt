package com.inotter.travelcompanion.sync.client

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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for receiving sync commands from master.
 * 
 * Connects to master's WebSocket server and listens for sync commands.
 * Sends responses back to master with playback status.
 * Implements automatic reconnection with exponential backoff.
 * 
 * Usage:
 * ```
 * val client = SyncCommandClient(clientId = "quest-device-2")
 * client.setCommandListener { command ->
 *     when (command.action) {
 *         "play" -> handlePlay(command)
 *         "pause" -> handlePause()
 *     }
 * }
 * client.connect(serverIp = "192.168.43.100", port = 8081)
 * client.sendResponse(SyncResponse(...))
 * client.disconnect()
 * ```
 */
class SyncCommandClient(
    private val clientId: String
) {
    companion object {
        private const val TAG = "SyncCommandClient"
        const val DEFAULT_PORT = 8081
        
        // Reconnection settings
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
        private const val RECONNECT_BACKOFF_MULTIPLIER = 2.0
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .pingInterval(30, TimeUnit.SECONDS) // Keep connection alive
        .build()
    
    private var webSocket: WebSocket? = null
    private var serverUrl: String? = null
    
    // Connection state
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val serverUrl: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Callback for receiving commands from master
    private var commandListener: ((SyncCommand) -> Unit)? = null
    
    // Reconnection
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var reconnectJob: Job? = null
    private var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
    private var shouldReconnect = false
    
    /**
     * Connect to master's WebSocket server.
     * 
     * @param serverIp Master server IP address
     * @param port WebSocket port (default: 8081)
     * @return true if connection initiated successfully
     */
    fun connect(serverIp: String, port: Int = DEFAULT_PORT): Boolean {
        if (_connectionState.value is ConnectionState.Connected) {
            Log.w(TAG, "Already connected")
            return true
        }
        
        val url = "ws://$serverIp:$port/sync"
        serverUrl = url
        shouldReconnect = true
        
        return connectInternal(url)
    }
    
    /**
     * Internal connection method.
     */
    private fun connectInternal(url: String): Boolean {
        try {
            Log.i(TAG, "Connecting to WebSocket: $url")
            _connectionState.value = ConnectionState.Connecting
            
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Client-Id", clientId)
                .build()
            
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket connected: $url")
                    _connectionState.value = ConnectionState.Connected(url)
                    reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS // Reset backoff
                    reconnectJob?.cancel()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }
                
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleMessage(bytes.utf8())
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closing: $code - $reason")
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closed: $code - $reason")
                    _connectionState.value = ConnectionState.Disconnected
                    scheduleReconnect()
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket error", t)
                    _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                    scheduleReconnect()
                }
            })
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to WebSocket", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
            scheduleReconnect()
            return false
        }
    }

    /**
     * Disconnect from master's WebSocket server.
     */
    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()

        try {
            webSocket?.close(1000, "Client disconnecting")
            webSocket = null
            _connectionState.value = ConnectionState.Disconnected
            Log.i(TAG, "Disconnected from WebSocket")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }

    /**
     * Send a response to master.
     *
     * @param response Response to send
     * @return true if sent successfully
     */
    fun sendResponse(response: SyncResponse): Boolean {
        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "Cannot send response: not connected")
            return false
        }

        return try {
            val json = response.toJson()
            ws.send(json)
            Log.d(TAG, "Sent response: pos=${response.videoPosition}, drift=${response.drift}ms")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send response", e)
            false
        }
    }

    /**
     * Set listener for receiving commands from master.
     *
     * @param listener Callback invoked when a command is received
     */
    fun setCommandListener(listener: (SyncCommand) -> Unit) {
        this.commandListener = listener
    }

    /**
     * Handle incoming message from master.
     */
    private fun handleMessage(message: String) {
        try {
            val command = SyncCommand.fromJson(message)
            commandListener?.invoke(command)
            Log.d(TAG, "Received command: ${command.action}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse command", e)
        }
    }

    /**
     * Schedule reconnection with exponential backoff.
     */
    private fun scheduleReconnect() {
        if (!shouldReconnect) {
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.i(TAG, "Reconnecting in ${reconnectDelayMs}ms...")
            delay(reconnectDelayMs)

            serverUrl?.let { url ->
                connectInternal(url)
            }

            // Increase backoff delay for next attempt
            reconnectDelayMs = (reconnectDelayMs * RECONNECT_BACKOFF_MULTIPLIER).toLong()
                .coerceAtMost(MAX_RECONNECT_DELAY_MS)
        }
    }

    /**
     * Check if connected to master.
     */
    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected
}

