package com.inotter.travelcompanion.domain.transfer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Repository for managing the WiFi transfer server state.
 * Acts as the bridge between the UI layer and the TransferService.
 *
 * @property context Android application context
 */
class TransferRepository(private val context: Context) {

    /**
     * Represents the current state of the transfer server.
     */
    sealed class ServerState {
        /** Server is stopped and not accepting connections */
        object Stopped : ServerState()
        
        /** Server is starting up */
        object Starting : ServerState()
        
        /** Server is running and accepting connections */
        data class Running(
            val ipAddress: String,
            val port: Int,
            val uploadCount: Int = 0
        ) : ServerState()
        
        /** Server encountered an error */
        data class Error(val message: String) : ServerState()
    }

    /**
     * Represents a recently uploaded file.
     */
    data class UploadedFile(
        val name: String,
        val size: Long,
        val sizeFormatted: String,
        val uploadedAt: Long
    )

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _recentUploads = MutableStateFlow<List<UploadedFile>>(emptyList())
    val recentUploads: StateFlow<List<UploadedFile>> = _recentUploads.asStateFlow()

    private val _currentPin = MutableStateFlow<String?>(null)
    val currentPin: StateFlow<String?> = _currentPin.asStateFlow()

    private val _pinEnabled = MutableStateFlow(false)
    val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    private var serviceBound = false
    private var transferService: TransferService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? TransferService.LocalBinder
            transferService = localBinder?.getService()
            serviceBound = true

            // Apply pre-configured PIN state to the service
            applyPinStateToService()

            updateStateFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            transferService = null
            serviceBound = false
            _serverState.value = ServerState.Stopped
        }
    }

    /**
     * Starts the WiFi transfer server.
     * Binds to the TransferService and starts it as a foreground service.
     *
     * @return Result containing the server URL on success, or error message on failure
     */
    fun startServer(): Result<String> {
        // Check WiFi connectivity first
        if (!NetworkUtils.isWifiConnected(context)) {
            _serverState.value = ServerState.Error("Not connected to WiFi")
            return Result.failure(IllegalStateException("Not connected to WiFi"))
        }

        _serverState.value = ServerState.Starting

        // Start the foreground service
        val intent = Intent(context, TransferService::class.java)
        context.startForegroundService(intent)

        // Bind to the service to receive updates
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        return Result.success("Server starting...")
    }

    /**
     * Stops the WiFi transfer server.
     */
    fun stopServer() {
        transferService?.stopServer()
        
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }

        // Stop the service
        val intent = Intent(context, TransferService::class.java)
        context.stopService(intent)

        transferService = null
        _serverState.value = ServerState.Stopped
    }

    /**
     * Returns the directory where uploaded files are saved.
     */
    fun getUploadDirectory(): File {
        val dir = File(context.filesDir, "wifi_uploads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Enables PIN protection and returns the generated PIN.
     * Can be called before or after server starts - PIN will be applied when service connects.
     */
    fun enablePinProtection(): String {
        val pin = generatePin()
        _currentPin.value = pin
        _pinEnabled.value = true

        // If service is already running, apply immediately
        transferService?.let { service ->
            service.enablePinProtection()
            // Sync the PIN we generated
            service.setPin(pin)
        }

        return pin
    }

    /**
     * Disables PIN protection.
     */
    fun disablePinProtection() {
        _currentPin.value = null
        _pinEnabled.value = false
        transferService?.disablePinProtection()
    }

    /**
     * Generates a 4-digit PIN.
     */
    private fun generatePin(): String {
        return (1000..9999).random().toString()
    }

    /**
     * Applies the current PIN state to the service when it connects.
     */
    private fun applyPinStateToService() {
        val service = transferService ?: return
        val pin = _currentPin.value

        if (_pinEnabled.value && pin != null) {
            service.enablePinProtection()
            service.setPin(pin)
        } else {
            service.disablePinProtection()
        }
    }

    /**
     * Updates the internal state from the bound service.
     * Called when the service connection is established.
     */
    private fun updateStateFromService() {
        val service = transferService ?: return

        when (val serviceState = service.state.value) {
            is TransferService.State.Stopped -> {
                _serverState.value = ServerState.Stopped
            }
            is TransferService.State.Running -> {
                _serverState.value = ServerState.Running(
                    ipAddress = serviceState.ipAddress,
                    port = serviceState.port
                )
            }
            is TransferService.State.Error -> {
                _serverState.value = ServerState.Error(serviceState.message)
            }
        }

        // Update recent uploads
        val uploads = service.uploadedFiles.value.map { file ->
            UploadedFile(
                name = file.name,
                size = file.size,
                sizeFormatted = FileValidator.formatBytes(file.size),
                uploadedAt = file.uploadedAt
            )
        }
        _recentUploads.value = uploads

        // Note: PIN state is managed locally in the repository and applied to the service,
        // so we don't overwrite it from service state here
    }

    /**
     * Refreshes the upload list from the service.
     */
    fun refreshUploads() {
        updateStateFromService()
    }

    /**
     * Returns the available storage space formatted as a string.
     */
    fun getAvailableStorageFormatted(): String {
        return FileValidator.formatBytes(FileValidator.getAvailableStorage(context))
    }

    /**
     * Returns the available storage in bytes.
     */
    fun getAvailableStorage(): Long {
        return FileValidator.getAvailableStorage(context)
    }
}

