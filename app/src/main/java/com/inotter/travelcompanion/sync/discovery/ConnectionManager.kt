package com.inotter.travelcompanion.sync.discovery

import android.content.Context
import android.util.Log
import com.inotter.travelcompanion.playback.PlaybackCore
import com.inotter.travelcompanion.sync.client.ClientSyncCoordinator
import com.inotter.travelcompanion.sync.models.DeviceInfo
import com.inotter.travelcompanion.sync.models.SyncSession
import com.inotter.travelcompanion.sync.server.MasterSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.NetworkInterface
import java.util.UUID

/**
 * Connection state for sync sessions.
 */
sealed class ConnectionState {
    object Idle : ConnectionState()
    data class CreatingSession(val pinCode: String) : ConnectionState()
    data class MasterSession(val session: SyncSession) : ConnectionState()
    data class DiscoveringServices(val pinCode: String? = null) : ConnectionState()
    data class JoiningSession(val service: ResolvedService) : ConnectionState()
    data class ClientSession(val service: ResolvedService) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Manages sync session connections for both master and client devices.
 * 
 * Coordinates all components needed for session creation, discovery, and connection:
 * - Master: Creates session, starts servers, advertises via mDNS
 * - Client: Discovers services, connects via PIN or auto-discovery
 * 
 * Usage (Master):
 * ```
 * val manager = ConnectionManager(context)
 * val session = manager.createSession(
 *     videoPath = "/path/to/movie.mp4",
 *     movieId = "movie1",
 *     deviceName = "Quest 3"
 * )
 * // Session is now advertised and ready for clients
 * manager.closeSession()
 * ```
 * 
 * Usage (Client):
 * ```
 * val manager = ConnectionManager(context, playbackCore)
 * manager.startDiscovery()
 * // Wait for services to be discovered
 * val services = manager.getDiscoveredServices()
 * val service = services.find { it.pinCode == "123456" }
 * manager.connectToMaster(service)
 * // Now connected and synced
 * manager.leaveSession()
 * ```
 */
class ConnectionManager(
    private val context: Context,
    private val playbackCore: PlaybackCore? = null,
    private val deviceId: String = UUID.randomUUID().toString(),
    private val deviceName: String = "VR Travel Companion"
) {
    companion object {
        private const val TAG = "ConnectionManager"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    // Components
    private var masterCoordinator: MasterSyncCoordinator? = null
    private var clientCoordinator: ClientSyncCoordinator? = null
    private var serviceAdvertiser: ServiceAdvertiser? = null
    private var serviceDiscovery: ServiceDiscovery? = null
    
    // State
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _currentSession = MutableStateFlow<SyncSession?>(null)
    val currentSession: StateFlow<SyncSession?> = _currentSession.asStateFlow()
    
    private val _connectedDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val connectedDevices: StateFlow<List<DeviceInfo>> = _connectedDevices.asStateFlow()
    
    /**
     * Create a new sync session as master.
     * 
     * @param videoPath Local path to video file
     * @param movieId Unique movie identifier
     * @param deviceName Human-readable device name
     * @return Created session or null if failed
     */
    fun createSession(
        videoPath: String,
        movieId: String,
        deviceName: String = this.deviceName
    ): SyncSession? {
        if (_connectionState.value !is ConnectionState.Idle) {
            Log.w(TAG, "Cannot create session: not in idle state")
            return null
        }
        
        try {
            val videoFile = File(videoPath)
            if (!videoFile.exists()) {
                Log.e(TAG, "Video file not found: $videoPath")
                _connectionState.value = ConnectionState.Error("Video file not found")
                return null
            }
            
            // Generate PIN
            val pinCode = SyncSession.generatePinCode()
            _connectionState.value = ConnectionState.CreatingSession(pinCode)
            
            Log.i(TAG, "Creating session with PIN: $pinCode")
            
            // Get local IP address
            val ipAddress = getLocalIpAddress()
            if (ipAddress == null) {
                Log.e(TAG, "Failed to get local IP address")
                _connectionState.value = ConnectionState.Error("Failed to get IP address")
                return null
            }
            
            // Create master coordinator
            val coordinator = MasterSyncCoordinator(context, deviceId)
            val sessionInfo = coordinator.startSession(videoFile, "current")
            
            if (sessionInfo == null) {
                Log.e(TAG, "Failed to start master session")
                _connectionState.value = ConnectionState.Error("Failed to start servers")
                return null
            }
            
            masterCoordinator = coordinator

            // Create device info
            val masterDevice = DeviceInfo.createForCurrentDevice(
                deviceId = deviceId,
                deviceName = deviceName,
                ipAddress = ipAddress
            )

            // Create session
            val session = SyncSession.create(
                masterDevice = masterDevice,
                videoPath = videoPath,
                movieId = "current",
                httpPort = sessionInfo.httpPort,
                wsPort = sessionInfo.wsPort
            ).copy(pinCode = pinCode)

            _currentSession.value = session

            // Observe connected clients from sync server
            coordinator.getSyncServer()?.let { syncServer ->
                scope.launch {
                    syncServer.connectedClientIds.collect { clientIds ->
                        // Map client IDs to DeviceInfo objects
                        val devices = clientIds.map { clientId ->
                            DeviceInfo(
                                deviceId = clientId,
                                deviceName = "Client-${clientId.take(8)}",
                                ipAddress = "unknown", // We don't track client IPs currently
                                connectedAt = System.currentTimeMillis()
                            )
                        }
                        _connectedDevices.value = devices
                        Log.d(TAG, "Connected devices updated: ${devices.size} clients")
                    }
                }
            }

            // Start advertising
            val advertiser = ServiceAdvertiser(context)
            advertiser.advertiseService(
                pinCode = pinCode,
                httpPort = sessionInfo.httpPort,
                wsPort = sessionInfo.wsPort,
                deviceName = deviceName
            )
            serviceAdvertiser = advertiser

            _connectionState.value = ConnectionState.MasterSession(session)
            Log.i(TAG, "Session created successfully: $session")
            
            return session

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            _connectionState.value = ConnectionState.Error("Failed to create session: ${e.message}")
            closeSession()
            return null
        }
    }

    /**
     * Close the current master session.
     */
    fun closeSession() {
        try {
            Log.i(TAG, "Closing session")

            serviceAdvertiser?.stopAdvertising()
            serviceAdvertiser = null

            masterCoordinator?.stopSession()
            masterCoordinator = null

            _currentSession.value = null
            _connectedDevices.value = emptyList()
            _connectionState.value = ConnectionState.Idle

        } catch (e: Exception) {
            Log.e(TAG, "Error closing session", e)
        }
    }

    /**
     * Start discovering available sync sessions.
     *
     * @param pinCode Optional PIN code to filter by
     */
    fun startDiscovery(pinCode: String? = null) {
        if (_connectionState.value !is ConnectionState.Idle) {
            Log.w(TAG, "Cannot start discovery: not in idle state")
            return
        }

        try {
            Log.i(TAG, "Starting service discovery" + if (pinCode != null) " for PIN: $pinCode" else "")
            _connectionState.value = ConnectionState.DiscoveringServices(pinCode)

            val discovery = ServiceDiscovery(context)
            discovery.startDiscovery()
            serviceDiscovery = discovery

            // Observe discovery state
            scope.launch {
                discovery.discoveryState.collect { state ->
                    when (state) {
                        is DiscoveryState.ServicesFound -> {
                            Log.i(TAG, "Found ${state.services.size} services")
                        }
                        is DiscoveryState.Error -> {
                            Log.e(TAG, "Discovery error: ${state.message}")
                            _connectionState.value = ConnectionState.Error(state.message)
                        }
                        else -> {}
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            _connectionState.value = ConnectionState.Error("Failed to start discovery: ${e.message}")
        }
    }

    /**
     * Stop discovering services.
     */
    fun stopDiscovery() {
        serviceDiscovery?.stopDiscovery()
        serviceDiscovery = null

        if (_connectionState.value is ConnectionState.DiscoveringServices) {
            _connectionState.value = ConnectionState.Idle
        }
    }

    /**
     * Get list of discovered services.
     *
     * @param pinCode Optional PIN code to filter by
     * @return List of discovered services
     */
    fun getDiscoveredServices(pinCode: String? = null): List<ResolvedService> {
        val discovery = serviceDiscovery ?: return emptyList()

        return if (pinCode != null) {
            discovery.filterByPin(pinCode)
        } else {
            discovery.getResolvedServices()
        }
    }

    /**
     * Connect to a master device as a client.
     *
     * @param service Resolved service to connect to
     * @return true if connected successfully
     */
    fun connectToMaster(service: ResolvedService): Boolean {
        if (playbackCore == null) {
            Log.e(TAG, "Cannot connect: PlaybackCore not provided")
            _connectionState.value = ConnectionState.Error("PlaybackCore required for client mode")
            return false
        }

        if (_connectionState.value !is ConnectionState.DiscoveringServices &&
            _connectionState.value !is ConnectionState.Idle) {
            Log.w(TAG, "Cannot connect: not in discovery or idle state")
            return false
        }

        try {
            Log.i(TAG, "Connecting to master: $service")
            _connectionState.value = ConnectionState.JoiningSession(service)

            // Stop discovery
            stopDiscovery()

            // Create client coordinator
            val coordinator = ClientSyncCoordinator(playbackCore, deviceId)

            // Join session
            val httpUrl = service.getHttpUrl("current") // Use hardcoded movie ID
            val wsUrl = service.getWsUrl()

            if (!coordinator.joinSession(httpUrl, wsUrl)) {
                Log.e(TAG, "Failed to join session")
                _connectionState.value = ConnectionState.Error("Failed to join session")
                return false
            }

            clientCoordinator = coordinator
            _connectionState.value = ConnectionState.ClientSession(service)

            Log.i(TAG, "Connected to master successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to master", e)
            _connectionState.value = ConnectionState.Error("Failed to connect: ${e.message}")
            leaveSession()
            return false
        }
    }

    /**
     * Leave the current client session.
     */
    fun leaveSession() {
        try {
            Log.i(TAG, "Leaving session")

            clientCoordinator?.leaveSession()
            clientCoordinator = null

            _connectionState.value = ConnectionState.Idle

        } catch (e: Exception) {
            Log.e(TAG, "Error leaving session", e)
        }
    }

    /**
     * Get local IP address of the device.
     */
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    // Skip loopback and IPv6 addresses
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return null
    }

    /**
     * Check if currently in a master session.
     */
    fun isMasterSession(): Boolean {
        return _connectionState.value is ConnectionState.MasterSession
    }

    /**
     * Check if currently in a client session.
     */
    fun isClientSession(): Boolean {
        return _connectionState.value is ConnectionState.ClientSession
    }

    /**
     * Get master coordinator (only available in master mode).
     */
    fun getMasterCoordinator(): MasterSyncCoordinator? {
        return masterCoordinator
    }

    /**
     * Get client coordinator (only available in client mode).
     */
    fun getClientCoordinator(): ClientSyncCoordinator? {
        return clientCoordinator
    }
}

