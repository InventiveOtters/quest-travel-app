package com.inotter.onthegovr.sync.server

import android.util.Log
import com.inotter.onthegovr.sync.models.SyncCommand
import com.inotter.onthegovr.sync.models.SyncResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * WebSocket server for broadcasting sync commands to connected clients.
 * 
 * Uses OkHttp WebSocket to handle real-time communication with clients.
 * Tracks connected clients and broadcasts commands to all or specific clients.
 * 
 * Usage:
 * ```
 * val server = SyncCommandServer()
 * server.start(port = 8081)
 * server.setResponseListener { clientId, response ->
 *     Log.d(TAG, "Client $clientId: position=${response.videoPosition}")
 * }
 * server.broadcastCommand(SyncCommand(action = "play", ...))
 * server.stop()
 * ```
 */
class SyncCommandServer {
    companion object {
        private const val TAG = "SyncCommandServer"
        const val DEFAULT_PORT = 8081
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .build()
    
    // Map of clientId -> WebSocket
    private val connectedClients = ConcurrentHashMap<String, WebSocket>()
    
    // Server state
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _connectedClientIds = MutableStateFlow<List<String>>(emptyList())
    val connectedClientIds: StateFlow<List<String>> = _connectedClientIds.asStateFlow()
    
    // Callback for receiving responses from clients
    private var responseListener: ((clientId: String, response: SyncResponse) -> Unit)? = null

    // Callback for receiving commands from clients (for bidirectional sync)
    private var commandListener: ((clientId: String, command: SyncCommand) -> Unit)? = null

    // Jetty server for WebSocket connections
    private var jettyServer: Server? = null
    private var currentPort: Int = DEFAULT_PORT
    
    /**
     * Start the WebSocket server on the specified port.
     *
     * @param port Port to listen on (default: 8081)
     * @return true if server started successfully
     */
    fun start(port: Int = DEFAULT_PORT): Boolean {
        if (_isRunning.value) {
            Log.w(TAG, "Server already running on port $currentPort")
            return true
        }

        try {
            currentPort = port

            // Create Jetty server
            val server = Server(port)
            val contextHandler = ServletContextHandler(ServletContextHandler.NO_SESSIONS)
            contextHandler.contextPath = "/"

            // Create and register WebSocket servlet
            val wsServlet = SyncWebSocketServlet(this)
            contextHandler.addServlet(ServletHolder(wsServlet), "/sync")

            server.handler = contextHandler
            server.start()

            jettyServer = server
            _isRunning.value = true

            Log.i(TAG, "WebSocket sync server started on port $port")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WebSocket server on port $port", e)
            jettyServer = null
            _isRunning.value = false
            return false
        }
    }
    
    /**
     * Stop the WebSocket server and disconnect all clients.
     */
    fun stop() {
        if (!_isRunning.value) {
            return
        }

        try {
            // Close all client connections
            connectedClients.values.forEach { webSocket ->
                webSocket.close(1000, "Server shutting down")
            }
            connectedClients.clear()
            _connectedClientIds.value = emptyList()

            // Stop Jetty server
            jettyServer?.stop()
            jettyServer = null

            _isRunning.value = false
            Log.i(TAG, "WebSocket sync server stopped")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WebSocket server", e)
        }
    }
    
    /**
     * Register a client WebSocket connection.
     * Called when a client connects to the server.
     * 
     * @param clientId Unique client identifier
     * @param webSocket WebSocket connection
     */
    fun registerClient(clientId: String, webSocket: WebSocket) {
        connectedClients[clientId] = webSocket
        _connectedClientIds.value = connectedClients.keys.toList()
        Log.i(TAG, "Client connected: $clientId (total: ${connectedClients.size})")
    }
    
    /**
     * Unregister a client WebSocket connection.
     * Called when a client disconnects.
     *
     * @param clientId Client identifier
     */
    fun unregisterClient(clientId: String) {
        connectedClients.remove(clientId)
        _connectedClientIds.value = connectedClients.keys.toList()
        Log.i(TAG, "Client disconnected: $clientId (remaining: ${connectedClients.size})")
    }

    /**
     * Broadcast a sync command to all connected clients.
     *
     * @param command Command to broadcast
     * @return Number of clients the command was sent to
     */
    fun broadcastCommand(command: SyncCommand): Int {
        if (!_isRunning.value) {
            Log.w(TAG, "Cannot broadcast: server not running")
            return 0
        }

        val json = command.toJson()
        var sentCount = 0

        connectedClients.forEach { (clientId, webSocket) ->
            try {
                webSocket.send(json)
                sentCount++
                Log.d(TAG, "Sent command to $clientId: ${command.action}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command to $clientId", e)
            }
        }

        Log.i(TAG, "Broadcast command '${command.action}' to $sentCount clients")
        return sentCount
    }

    /**
     * Send a sync command to a specific client.
     *
     * @param clientId Target client ID
     * @param command Command to send
     * @return true if sent successfully
     */
    fun sendToClient(clientId: String, command: SyncCommand): Boolean {
        if (!_isRunning.value) {
            Log.w(TAG, "Cannot send: server not running")
            return false
        }

        val webSocket = connectedClients[clientId]
        if (webSocket == null) {
            Log.w(TAG, "Client not found: $clientId")
            return false
        }

        return try {
            val json = command.toJson()
            webSocket.send(json)
            Log.d(TAG, "Sent command to $clientId: ${command.action}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command to $clientId", e)
            false
        }
    }

    /**
     * Set listener for receiving responses from clients.
     *
     * @param listener Callback invoked when a response is received
     */
    fun setResponseListener(listener: (clientId: String, response: SyncResponse) -> Unit) {
        this.responseListener = listener
    }

    /**
     * Set listener for client commands (for bidirectional sync).
     *
     * @param listener Callback invoked when a command is received from a client
     */
    fun setCommandListener(listener: (clientId: String, command: SyncCommand) -> Unit) {
        this.commandListener = listener
    }

    /**
     * Handle incoming message from a client.
     * Called internally when a message is received.
     *
     * @param clientId Client that sent the message
     * @param message JSON message
     */
    internal fun handleClientMessage(clientId: String, message: String) {
        try {
            // Try to parse as SyncCommand first (for bidirectional sync)
            try {
                val command = SyncCommand.fromJson(message)
                commandListener?.invoke(clientId, command)
                Log.d(TAG, "Received command from $clientId: action=${command.action}")
                return
            } catch (e: Exception) {
                // Not a command, try parsing as response
            }

            // Parse as SyncResponse
            val response = SyncResponse.fromJson(message)
            responseListener?.invoke(clientId, response)
            Log.d(TAG, "Received response from $clientId: pos=${response.videoPosition}, drift=${response.drift}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message from $clientId", e)
        }
    }

    /**
     * Get number of connected clients.
     */
    fun getClientCount(): Int = connectedClients.size

    /**
     * Check if a specific client is connected.
     */
    fun isClientConnected(clientId: String): Boolean = connectedClients.containsKey(clientId)
}

