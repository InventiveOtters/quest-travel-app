package com.inotter.travelcompanion.sync.models

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Represents a synchronized video playback session.
 * 
 * Contains all information needed for clients to discover and join a session,
 * including PIN code, URLs, and connected devices.
 * 
 * Example JSON:
 * ```json
 * {
 *   "sessionId": "550e8400-e29b-41d4-a716-446655440000",
 *   "pinCode": "123456",
 *   "masterDevice": {...},
 *   "connectedClients": [...],
 *   "videoPath": "/storage/emulated/0/Movies/movie.mp4",
 *   "movieId": "movie1",
 *   "httpUrl": "http://192.168.43.100:8080/video/movie1",
 *   "wsUrl": "ws://192.168.43.100:8081/sync",
 *   "createdAt": 1234567890
 * }
 * ```
 */
data class SyncSession(
    /**
     * Unique session identifier (UUID)
     */
    @SerializedName("sessionId")
    val sessionId: String = UUID.randomUUID().toString(),
    
    /**
     * 6-digit PIN code for joining the session
     */
    @SerializedName("pinCode")
    val pinCode: String,
    
    /**
     * Information about the master device hosting the session
     */
    @SerializedName("masterDevice")
    val masterDevice: DeviceInfo,
    
    /**
     * List of connected client devices
     */
    @SerializedName("connectedClients")
    val connectedClients: List<DeviceInfo> = emptyList(),
    
    /**
     * Local file path to the video on master device
     */
    @SerializedName("videoPath")
    val videoPath: String,
    
    /**
     * Unique identifier for the movie
     */
    @SerializedName("movieId")
    val movieId: String,
    
    /**
     * HTTP URL for video streaming
     */
    @SerializedName("httpUrl")
    val httpUrl: String,
    
    /**
     * WebSocket URL for sync commands
     */
    @SerializedName("wsUrl")
    val wsUrl: String,
    
    /**
     * Timestamp when session was created (milliseconds since epoch)
     */
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Generate a random 6-digit PIN code.
         */
        fun generatePinCode(): String {
            return (100000..999999).random().toString()
        }
        
        /**
         * Create a new session with generated PIN.
         * 
         * @param masterDevice Master device information
         * @param videoPath Local path to video file
         * @param movieId Unique movie identifier
         * @param httpPort HTTP server port
         * @param wsPort WebSocket server port
         * @return New SyncSession instance
         */
        fun create(
            masterDevice: DeviceInfo,
            videoPath: String,
            movieId: String,
            httpPort: Int = 8080,
            wsPort: Int = 8081
        ): SyncSession {
            val pinCode = generatePinCode()
            val httpUrl = "http://${masterDevice.ipAddress}:$httpPort/video/$movieId"
            val wsUrl = "ws://${masterDevice.ipAddress}:$wsPort/sync"
            
            return SyncSession(
                pinCode = pinCode,
                masterDevice = masterDevice,
                videoPath = videoPath,
                movieId = movieId,
                httpUrl = httpUrl,
                wsUrl = wsUrl
            )
        }
    }
    
    /**
     * Add a client to the session.
     */
    fun addClient(client: DeviceInfo): SyncSession {
        return copy(connectedClients = connectedClients + client)
    }
    
    /**
     * Remove a client from the session.
     */
    fun removeClient(clientId: String): SyncSession {
        return copy(connectedClients = connectedClients.filter { it.deviceId != clientId })
    }
    
    /**
     * Update a client's ready state.
     */
    fun updateClientReady(clientId: String, isReady: Boolean): SyncSession {
        val updatedClients = connectedClients.map { client ->
            if (client.deviceId == clientId) {
                client.withReadyState(isReady)
            } else {
                client
            }
        }
        return copy(connectedClients = updatedClients)
    }
    
    /**
     * Check if all clients are ready.
     */
    fun areAllClientsReady(): Boolean {
        return connectedClients.isNotEmpty() && connectedClients.all { it.isReady }
    }
    
    /**
     * Get total number of devices (master + clients).
     */
    fun getTotalDeviceCount(): Int {
        return 1 + connectedClients.size
    }
    
    override fun toString(): String {
        return "SyncSession(id=$sessionId, pin=$pinCode, clients=${connectedClients.size})"
    }
}

