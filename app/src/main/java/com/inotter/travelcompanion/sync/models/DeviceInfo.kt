package com.inotter.travelcompanion.sync.models

import com.google.gson.annotations.SerializedName

/**
 * Information about a device in a sync session.
 * 
 * Represents either the master device or a connected client device.
 * Used for tracking connection status and device identification.
 * 
 * Example JSON:
 * ```json
 * {
 *   "deviceId": "quest-device-1",
 *   "deviceName": "Quest 3 - Living Room",
 *   "ipAddress": "192.168.43.100",
 *   "connectedAt": 1234567890,
 *   "isReady": true
 * }
 * ```
 */
data class DeviceInfo(
    /**
     * Unique identifier for the device (e.g., "quest-device-1")
     */
    @SerializedName("deviceId")
    val deviceId: String,
    
    /**
     * Human-readable device name (e.g., "Quest 3 - Living Room")
     */
    @SerializedName("deviceName")
    val deviceName: String,
    
    /**
     * IP address of the device on the local network (e.g., "192.168.43.100")
     */
    @SerializedName("ipAddress")
    val ipAddress: String,
    
    /**
     * Timestamp when device connected (milliseconds since epoch)
     */
    @SerializedName("connectedAt")
    val connectedAt: Long = System.currentTimeMillis(),
    
    /**
     * Whether the device is ready for synchronized playback
     * (video buffered and ready to play)
     */
    @SerializedName("isReady")
    val isReady: Boolean = false
) {
    companion object {
        /**
         * Create DeviceInfo for the current device.
         * 
         * @param deviceId Unique device identifier
         * @param deviceName Human-readable device name
         * @param ipAddress Device IP address
         * @return DeviceInfo instance
         */
        fun createForCurrentDevice(
            deviceId: String,
            deviceName: String,
            ipAddress: String
        ): DeviceInfo {
            return DeviceInfo(
                deviceId = deviceId,
                deviceName = deviceName,
                ipAddress = ipAddress,
                connectedAt = System.currentTimeMillis(),
                isReady = false
            )
        }
    }
    
    /**
     * Create a copy with updated ready state.
     */
    fun withReadyState(ready: Boolean): DeviceInfo {
        return copy(isReady = ready)
    }
    
    /**
     * Get connection duration in milliseconds.
     */
    fun getConnectionDuration(): Long {
        return System.currentTimeMillis() - connectedAt
    }
    
    override fun toString(): String {
        return "DeviceInfo(id=$deviceId, name=$deviceName, ip=$ipAddress, ready=$isReady)"
    }
}

