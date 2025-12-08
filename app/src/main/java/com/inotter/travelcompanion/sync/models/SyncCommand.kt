package com.inotter.travelcompanion.sync.models

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Sync command sent from master to clients or from clients to master.
 *
 * Commands are sent over WebSocket and serialized as JSON.
 *
 * Actions:
 * - `load`: Load a specific movie
 * - `start`: Initial playback start (first time only, from "Start watching together")
 * - `play`: Resume playback at timestamp (with future target time for sync)
 * - `pause`: Pause playback
 * - `seek`: Jump to specific timestamp
 * - `sync_check`: Request sync status from clients
 *
 * Example JSON:
 * ```json
 * {
 *   "action": "start",
 *   "timestamp": 1234567890,
 *   "targetStartTime": 1234568390,
 *   "videoPosition": 0,
 *   "movieId": "movie1",
 *   "senderId": "quest-device-1"
 * }
 * ```
 */
data class SyncCommand(
    /**
     * Action to perform: start, play, pause, seek, load, sync_check
     */
    @SerializedName("action")
    val action: String,
    
    /**
     * Timestamp when command was sent (milliseconds since epoch)
     */
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    /**
     * Target time to start playback (for predictive sync)
     * Used with "play" action to synchronize start time across devices
     */
    @SerializedName("targetStartTime")
    val targetStartTime: Long? = null,
    
    /**
     * Current video position in milliseconds
     * Used with "play" and "sync_check" actions
     */
    @SerializedName("videoPosition")
    val videoPosition: Long? = null,
    
    /**
     * Target position for seek operation in milliseconds
     * Used with "seek" action
     */
    @SerializedName("seekPosition")
    val seekPosition: Long? = null,
    
    /**
     * Movie identifier
     * Used with "load" action
     */
    @SerializedName("movieId")
    val movieId: String? = null,
    
    /**
     * Device ID of sender
     */
    @SerializedName("senderId")
    val senderId: String,
    
    /**
     * Optional metadata (movie title, duration, etc.)
     */
    @SerializedName("metadata")
    val metadata: Map<String, String>? = null
) {
    companion object {
        // Action types
        const val ACTION_LOAD = "load"
        const val ACTION_START = "start"
        const val ACTION_PLAY = "play"
        const val ACTION_PAUSE = "pause"
        const val ACTION_SEEK = "seek"
        const val ACTION_SYNC_CHECK = "sync_check"
        
        private val gson = Gson()
        
        /**
         * Serialize command to JSON string
         */
        fun toJson(command: SyncCommand): String {
            return gson.toJson(command)
        }
        
        /**
         * Deserialize command from JSON string
         */
        fun fromJson(json: String): SyncCommand {
            return gson.fromJson(json, SyncCommand::class.java)
        }
    }
    
    /**
     * Convert to JSON string
     */
    fun toJson(): String = toJson(this)
}

