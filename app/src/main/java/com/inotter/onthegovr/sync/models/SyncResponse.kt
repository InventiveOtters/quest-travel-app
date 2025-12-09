package com.inotter.onthegovr.sync.models

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Response sent from clients to master with playback status.
 * 
 * Clients send responses to report their current state, readiness, and drift.
 * 
 * Example JSON:
 * ```json
 * {
 *   "clientId": "quest-device-2",
 *   "videoPosition": 60500,
 *   "isPlaying": true,
 *   "drift": 50,
 *   "bufferPercentage": 85,
 *   "isReady": true,
 *   "timestamp": 1234567890
 * }
 * ```
 */
data class SyncResponse(
    /**
     * Client device ID
     */
    @SerializedName("clientId")
    val clientId: String,
    
    /**
     * Current video position in milliseconds
     */
    @SerializedName("videoPosition")
    val videoPosition: Long,
    
    /**
     * Whether video is currently playing
     */
    @SerializedName("isPlaying")
    val isPlaying: Boolean,
    
    /**
     * Drift from master in milliseconds (positive = ahead, negative = behind)
     */
    @SerializedName("drift")
    val drift: Long = 0L,
    
    /**
     * Buffer percentage (0-100)
     */
    @SerializedName("bufferPercentage")
    val bufferPercentage: Int = 0,
    
    /**
     * Whether client is ready to start playback
     */
    @SerializedName("isReady")
    val isReady: Boolean = false,
    
    /**
     * Timestamp when response was sent (milliseconds since epoch)
     */
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private val gson = Gson()
        
        /**
         * Serialize response to JSON string
         */
        fun toJson(response: SyncResponse): String {
            return gson.toJson(response)
        }
        
        /**
         * Deserialize response from JSON string
         */
        fun fromJson(json: String): SyncResponse {
            return gson.fromJson(json, SyncResponse::class.java)
        }
    }
    
    /**
     * Convert to JSON string
     */
    fun toJson(): String = toJson(this)
}

