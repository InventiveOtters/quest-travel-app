package com.inotter.travelcompanion.data.managers.TransferManager

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Utility object for network-related operations.
 * Provides WiFi IP detection and connectivity checks for the WiFi transfer feature.
 */
object NetworkUtils {

    /**
     * Gets the device's WiFi IP address.
     * Tries multiple methods to ensure reliability across different Android versions.
     *
     * @param context Android application context
     * @return The WiFi IP address as a string (e.g., "192.168.1.45"), or null if not available
     */
    fun getWifiIpAddress(context: Context): String? {
        // First try: Use WifiManager (most reliable on Quest)
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager?.connectionInfo?.ipAddress?.let { ipInt ->
            if (ipInt != 0) {
                val ip = intToIpAddress(ipInt)
                if (ip != "0.0.0.0") return ip
            }
        }

        // Fallback: Enumerate network interfaces
        return getIpFromNetworkInterfaces()
    }

    /**
     * Checks if the device is connected to a WiFi network.
     *
     * @param context Android application context
     * @return true if connected to WiFi, false otherwise
     */
    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Gets the network name (SSID) if available.
     * Note: Requires ACCESS_FINE_LOCATION permission on newer Android versions.
     *
     * @param context Android application context
     * @return The WiFi SSID or null if not available
     */
    fun getWifiSsid(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ssid = wifiManager?.connectionInfo?.ssid
        // SSID is wrapped in quotes, remove them
        return ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
    }

    /**
     * Converts an integer IP address to a dotted string format.
     * Android's WifiManager returns IP as a little-endian integer.
     */
    private fun intToIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }

    /**
     * Fallback method to get IP address by enumerating network interfaces.
     * Useful when WifiManager doesn't return a valid IP.
     */
    private fun getIpFromNetworkInterfaces(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (networkInterface in interfaces.asSequence()) {
                // Skip loopback and down interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                // Look for wlan interfaces (WiFi)
                val name = networkInterface.name.lowercase()
                if (!name.startsWith("wlan") && !name.startsWith("eth")) continue

                for (address in networkInterface.inetAddresses.asSequence()) {
                    // Only IPv4 addresses
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors and return null
        }
        return null
    }
}

