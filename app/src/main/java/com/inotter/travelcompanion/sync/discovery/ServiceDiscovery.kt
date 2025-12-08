package com.inotter.travelcompanion.sync.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Information about a discovered sync service.
 */
data class ResolvedService(
    val serviceName: String,
    val ipAddress: String,
    val httpPort: Int,
    val wsPort: Int,
    val pinCode: String,
    val deviceName: String,
    val version: String
) {
    /**
     * Get HTTP URL for video streaming.
     */
    fun getHttpUrl(movieId: String): String {
        return "http://$ipAddress:$httpPort/video/$movieId"
    }
    
    /**
     * Get WebSocket URL for sync commands.
     */
    fun getWsUrl(): String {
        return "ws://$ipAddress:$wsPort/sync"
    }
    
    override fun toString(): String {
        return "ResolvedService(name=$serviceName, pin=$pinCode, ip=$ipAddress)"
    }
}

/**
 * State of service discovery.
 */
sealed class DiscoveryState {
    object Idle : DiscoveryState()
    object Discovering : DiscoveryState()
    data class ServicesFound(val services: List<ResolvedService>) : DiscoveryState()
    data class Error(val message: String) : DiscoveryState()
}

/**
 * Discovers sync sessions via mDNS for automatic connection.
 * 
 * Uses Android's NsdManager to discover advertised services on the local network.
 * Resolves service information and provides filtering by PIN code.
 * 
 * Usage:
 * ```
 * val discovery = ServiceDiscovery(context)
 * discovery.discoveryState.collect { state ->
 *     when (state) {
 *         is DiscoveryState.ServicesFound -> {
 *             val services = state.services.filter { it.pinCode == "123456" }
 *             // Connect to service
 *         }
 *     }
 * }
 * discovery.startDiscovery()
 * // ... discovery running
 * discovery.stopDiscovery()
 * ```
 */
class ServiceDiscovery(
    private val context: Context
) {
    companion object {
        private const val TAG = "ServiceDiscovery"
        private const val SERVICE_TYPE = "_vrtravel._tcp"
        
        // TXT record keys
        private const val TXT_KEY_PIN = "pin"
        private const val TXT_KEY_WS_PORT = "ws_port"
        private const val TXT_KEY_DEVICE_NAME = "device_name"
        private const val TXT_KEY_VERSION = "version"
    }
    
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val discoveredServices = mutableMapOf<String, NsdServiceInfo>()
    private val resolvedServices = mutableListOf<ResolvedService>()
    
    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()
    
    /**
     * Start discovering sync services on the local network.
     */
    fun startDiscovery() {
        if (_discoveryState.value is DiscoveryState.Discovering) {
            Log.w(TAG, "Already discovering")
            return
        }
        
        try {
            Log.i(TAG, "Starting service discovery")
            _discoveryState.value = DiscoveryState.Discovering
            discoveredServices.clear()
            resolvedServices.clear()
            
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String?) {
                    Log.i(TAG, "Discovery started for: $serviceType")
                }
                
                override fun onDiscoveryStopped(serviceType: String?) {
                    Log.i(TAG, "Discovery stopped for: $serviceType")
                    _discoveryState.value = DiscoveryState.Idle
                }
                
                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    serviceInfo?.let { info ->
                        Log.i(TAG, "Service found: ${info.serviceName}")
                        discoveredServices[info.serviceName] = info
                        resolveService(info)
                    }
                }
                
                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                    serviceInfo?.let { info ->
                        Log.i(TAG, "Service lost: ${info.serviceName}")
                        discoveredServices.remove(info.serviceName)
                        resolvedServices.removeAll { it.serviceName == info.serviceName }
                        updateDiscoveryState()
                    }
                }
                
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.e(TAG, "Discovery start failed: errorCode=$errorCode")
                    _discoveryState.value = DiscoveryState.Error("Discovery failed: $errorCode")
                    discoveryListener = null
                }
                
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    Log.e(TAG, "Discovery stop failed: errorCode=$errorCode")
                    discoveryListener = null
                }
            }
            
            discoveryListener = listener
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            _discoveryState.value = DiscoveryState.Error("Failed to start: ${e.message}")
            discoveryListener = null
        }
    }

    /**
     * Stop discovering services.
     */
    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            try {
                Log.i(TAG, "Stopping service discovery")
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery", e)
            }
        }
        discoveryListener = null
        discoveredServices.clear()
        resolvedServices.clear()
        _discoveryState.value = DiscoveryState.Idle
    }

    /**
     * Resolve a discovered service to get IP address and TXT records.
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${serviceInfo?.serviceName}: errorCode=$errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    try {
                        val ipAddress = info.host?.hostAddress ?: return
                        val httpPort = info.port

                        // Extract TXT records
                        val pinCode = info.attributes[TXT_KEY_PIN]?.decodeToString() ?: ""
                        val wsPortStr = info.attributes[TXT_KEY_WS_PORT]?.decodeToString() ?: "8081"
                        val deviceName = info.attributes[TXT_KEY_DEVICE_NAME]?.decodeToString() ?: "Unknown"
                        val version = info.attributes[TXT_KEY_VERSION]?.decodeToString() ?: "1.0"

                        val wsPort = wsPortStr.toIntOrNull() ?: 8081

                        val resolved = ResolvedService(
                            serviceName = info.serviceName,
                            ipAddress = ipAddress,
                            httpPort = httpPort,
                            wsPort = wsPort,
                            pinCode = pinCode,
                            deviceName = deviceName,
                            version = version
                        )

                        Log.i(TAG, "Service resolved: $resolved")

                        // Add to resolved services
                        resolvedServices.removeAll { it.serviceName == resolved.serviceName }
                        resolvedServices.add(resolved)
                        updateDiscoveryState()

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing resolved service", e)
                    }
                }
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving service: ${serviceInfo.serviceName}", e)
        }
    }

    /**
     * Update discovery state with current resolved services.
     */
    private fun updateDiscoveryState() {
        if (_discoveryState.value is DiscoveryState.Discovering) {
            _discoveryState.value = DiscoveryState.ServicesFound(resolvedServices.toList())
        }
    }

    /**
     * Filter resolved services by PIN code.
     */
    fun filterByPin(pin: String): List<ResolvedService> {
        return resolvedServices.filter { it.pinCode == pin }
    }

    /**
     * Get all currently resolved services.
     */
    fun getResolvedServices(): List<ResolvedService> {
        return resolvedServices.toList()
    }
}

