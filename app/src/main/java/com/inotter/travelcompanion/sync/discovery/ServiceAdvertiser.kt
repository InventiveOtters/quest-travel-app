package com.inotter.travelcompanion.sync.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State of service advertisement.
 */
sealed class AdvertisingState {
    /**
     * Not advertising.
     */
    object Idle : AdvertisingState()

    /**
     * Registering service with mDNS.
     */
    object Registering : AdvertisingState()

    /**
     * Successfully advertising service.
     */
    data class Advertising(
        val serviceName: String,
        val pinCode: String,
        val httpPort: Int,
        val wsPort: Int
    ) : AdvertisingState()

    /**
     * Error during advertisement.
     */
    data class Error(val message: String) : AdvertisingState()
}

/**
 * Advertises sync session via mDNS for automatic discovery by clients.
 * 
 * Uses Android's NsdManager to broadcast service information on the local network.
 * Clients can discover the service and connect using the PIN code.
 * 
 * Usage:
 * ```
 * val advertiser = ServiceAdvertiser(context)
 * advertiser.advertiseService(
 *     pinCode = "123456",
 *     httpPort = 8080,
 *     wsPort = 8081,
 *     deviceName = "Quest 3 - Living Room"
 * )
 * // ... session running
 * advertiser.stopAdvertising()
 * ```
 */
class ServiceAdvertiser(
    private val context: Context
) {
    companion object {
        private const val TAG = "ServiceAdvertiser"
        private const val SERVICE_TYPE = "_vrtravel._tcp"
        private const val SERVICE_NAME_PREFIX = "VRTravel"
        
        // TXT record keys
        private const val TXT_KEY_PIN = "pin"
        private const val TXT_KEY_WS_PORT = "ws_port"
        private const val TXT_KEY_DEVICE_NAME = "device_name"
        private const val TXT_KEY_VERSION = "version"
        
        private const val PROTOCOL_VERSION = "1.0"
    }
    
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var currentServiceInfo: NsdServiceInfo? = null
    
    private val _advertisingState = MutableStateFlow<AdvertisingState>(AdvertisingState.Idle)
    val advertisingState: StateFlow<AdvertisingState> = _advertisingState.asStateFlow()
    
    /**
     * Start advertising the sync service via mDNS.
     * 
     * @param pinCode 6-digit PIN code for the session
     * @param httpPort HTTP server port (default: 8080)
     * @param wsPort WebSocket server port (default: 8081)
     * @param deviceName Human-readable device name
     */
    fun advertiseService(
        pinCode: String,
        httpPort: Int = 8080,
        wsPort: Int = 8081,
        deviceName: String = "VR Travel Companion"
    ) {
        if (_advertisingState.value is AdvertisingState.Advertising) {
            Log.w(TAG, "Already advertising, stopping first")
            stopAdvertising()
        }
        
        try {
            Log.i(TAG, "Starting service advertisement with PIN: $pinCode")
            _advertisingState.value = AdvertisingState.Registering
            
            // Create service info
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "$SERVICE_NAME_PREFIX-$pinCode"
                serviceType = SERVICE_TYPE
                port = httpPort
                
                // Add TXT records with session information
                setAttribute(TXT_KEY_PIN, pinCode)
                setAttribute(TXT_KEY_WS_PORT, wsPort.toString())
                setAttribute(TXT_KEY_DEVICE_NAME, deviceName)
                setAttribute(TXT_KEY_VERSION, PROTOCOL_VERSION)
            }
            
            // Create registration listener
            val listener = object : NsdManager.RegistrationListener {
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e(TAG, "Service registration failed: errorCode=$errorCode")
                    _advertisingState.value = AdvertisingState.Error("Registration failed: $errorCode")
                    registrationListener = null
                }
                
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e(TAG, "Service unregistration failed: errorCode=$errorCode")
                    registrationListener = null
                }
                
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                    val registeredName = serviceInfo?.serviceName ?: "unknown"
                    Log.i(TAG, "Service registered: $registeredName")
                    currentServiceInfo = serviceInfo
                    _advertisingState.value = AdvertisingState.Advertising(
                        serviceName = registeredName,
                        pinCode = pinCode,
                        httpPort = httpPort,
                        wsPort = wsPort
                    )
                }
                
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                    Log.i(TAG, "Service unregistered: ${serviceInfo?.serviceName}")
                    currentServiceInfo = null
                    _advertisingState.value = AdvertisingState.Idle
                    registrationListener = null
                }
            }
            
            registrationListener = listener
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to advertise service", e)
            _advertisingState.value = AdvertisingState.Error("Failed to advertise: ${e.message}")
            registrationListener = null
        }
    }
    
    /**
     * Stop advertising the service.
     */
    fun stopAdvertising() {
        registrationListener?.let { listener ->
            try {
                Log.i(TAG, "Stopping service advertisement")
                nsdManager.unregisterService(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping advertisement", e)
            }
        }
        registrationListener = null
        currentServiceInfo = null
        _advertisingState.value = AdvertisingState.Idle
    }
}

