package com.inotter.travelcompanion.data.repositories.TransferRepository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.inotter.travelcompanion.TransferService
import com.inotter.travelcompanion.data.managers.TransferManager.FileValidator
import com.inotter.travelcompanion.data.managers.TransferManager.JettyUploadServer
import com.inotter.travelcompanion.data.managers.TransferManager.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [TransferRepository] for managing the WiFi transfer server state.
 *
 * @property context Android application context
 */
@Singleton
class TransferRepositoryImpl @Inject constructor(
    private val context: Context
) : TransferRepository {

    private val _serverState = MutableStateFlow<TransferRepository.ServerState>(TransferRepository.ServerState.Stopped)
    override val serverState: StateFlow<TransferRepository.ServerState> = _serverState.asStateFlow()

    private val _recentUploads = MutableStateFlow<List<TransferRepository.UploadedFile>>(emptyList())
    override val recentUploads: StateFlow<List<TransferRepository.UploadedFile>> = _recentUploads.asStateFlow()

    private val _currentPin = MutableStateFlow<String?>(null)
    override val currentPin: StateFlow<String?> = _currentPin.asStateFlow()

    private val _pinEnabled = MutableStateFlow(false)
    override val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    private var serviceBound = false
    private var transferService: TransferService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? TransferService.LocalBinder
            transferService = localBinder?.getService()
            serviceBound = true
            applyPinStateToService()
            updateStateFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            transferService = null
            serviceBound = false
            _serverState.value = TransferRepository.ServerState.Stopped
        }
    }

    override fun startServer(): Result<String> {
        if (!NetworkUtils.isWifiConnected(context)) {
            _serverState.value = TransferRepository.ServerState.Error("Not connected to WiFi")
            return Result.failure(IllegalStateException("Not connected to WiFi"))
        }

        _serverState.value = TransferRepository.ServerState.Starting

        val intent = Intent(context, TransferService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        return Result.success("Server starting...")
    }

    override fun stopServer() {
        transferService?.stopServer()

        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }

        val intent = Intent(context, TransferService::class.java)
        context.stopService(intent)

        transferService = null
        _serverState.value = TransferRepository.ServerState.Stopped
    }

    override fun getUploadDirectory(): File {
        val dir = File(context.filesDir, "wifi_uploads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    override fun enablePinProtection(): String {
        val pin = generatePin()
        _currentPin.value = pin
        _pinEnabled.value = true

        transferService?.let { service ->
            service.enablePinProtection()
            service.setPin(pin)
        }

        return pin
    }

    override fun disablePinProtection() {
        _currentPin.value = null
        _pinEnabled.value = false
        transferService?.disablePinProtection()
    }

    private fun generatePin(): String {
        return (1000..9999).random().toString()
    }

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

    private fun updateStateFromService() {
        val service = transferService ?: return

        when (val serviceState = service.state.value) {
            is TransferService.State.Stopped -> {
                _serverState.value = TransferRepository.ServerState.Stopped
            }
            is TransferService.State.Running -> {
                _serverState.value = TransferRepository.ServerState.Running(
                    ipAddress = serviceState.ipAddress,
                    port = serviceState.port
                )
            }
            is TransferService.State.Error -> {
                _serverState.value = TransferRepository.ServerState.Error(serviceState.message)
            }
        }

        val uploads = service.uploadedFiles.value.map { file: JettyUploadServer.UploadedFile ->
            TransferRepository.UploadedFile(
                name = file.name,
                size = file.size,
                sizeFormatted = FileValidator.formatBytes(file.size),
                uploadedAt = file.uploadedAt
            )
        }
        _recentUploads.value = uploads
    }

    override fun refreshUploads() {
        updateStateFromService()
    }

    override fun getAvailableStorageFormatted(): String {
        return FileValidator.formatBytes(FileValidator.getAvailableStorage(context))
    }

    override fun getAvailableStorage(): Long {
        return FileValidator.getAvailableStorage(context)
    }
}

