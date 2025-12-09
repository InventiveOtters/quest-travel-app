package com.inotter.onthegovr.ui.transfer

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inotter.onthegovr.data.managers.TransferManager.NetworkUtils
import com.inotter.onthegovr.data.repositories.TransferRepository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the WiFi Transfer screen.
 * Manages UI state for the transfer server and provides TTS functionality.
 */
@HiltViewModel
class TransferViewModel @Inject constructor(
    application: Application,
    private val repository: TransferRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TransferViewModel"
        private const val REFRESH_INTERVAL_MS = 2000L
    }

    /**
     * UI state for the WiFi Transfer screen.
     */
    data class TransferUiState(
        val isServerRunning: Boolean = false,
        val isStarting: Boolean = false,
        val ipAddress: String? = null,
        val port: Int = 8080,
        val recentUploads: List<UploadInfo> = emptyList(),
        val availableStorage: String = "...",
        val availableStorageBytes: Long = 0L,
        val isWifiConnected: Boolean = true,
        val error: String? = null,
        val pinEnabled: Boolean = false,
        val currentPin: String? = null
    ) {
        /** Returns the full server URL with http:// prefix */
        val serverUrl: String? get() = if (isServerRunning && ipAddress != null) {
            "http://$ipAddress:$port"
        } else null
    }

    /**
     * Information about an uploaded file for display.
     */
    data class UploadInfo(
        val name: String,
        val sizeFormatted: String,
        val timeAgo: String
    )

    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var refreshJob: Job? = null

    init {
        initializeTts()
        updateConnectivityState()
        updateStorageInfo()
        observeServiceState()
    }

    private fun initializeTts() {
        tts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            repository.serverState.collect { state ->
                when (state) {
                    is TransferRepository.ServerState.Stopped -> {
                        _uiState.value = _uiState.value.copy(
                            isServerRunning = false,
                            isStarting = false,
                            ipAddress = null,
                            error = null
                        )
                        stopRefreshLoop()
                    }
                    is TransferRepository.ServerState.Starting -> {
                        _uiState.value = _uiState.value.copy(
                            isStarting = true,
                            error = null
                        )
                    }
                    is TransferRepository.ServerState.Running -> {
                        _uiState.value = _uiState.value.copy(
                            isServerRunning = true,
                            isStarting = false,
                            ipAddress = state.ipAddress,
                            port = state.port,
                            error = null
                        )
                        startRefreshLoop()
                    }
                    is TransferRepository.ServerState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isServerRunning = false,
                            isStarting = false,
                            error = state.message
                        )
                        stopRefreshLoop()
                    }
                }
            }
        }
    }

    /**
     * Toggles the server state (start if stopped, stop if running).
     */
    fun toggleServer() {
        val currentState = _uiState.value
        if (currentState.isServerRunning || currentState.isStarting) {
            repository.stopServer()
        } else {
            // Check for critical storage before starting
            if (isStorageCritical()) {
                _uiState.value = _uiState.value.copy(
                    error = "Cannot start server: Storage is critically low (< 500 MB). Please free up space first."
                )
                return
            }
            repository.startServer()
        }
    }

    /**
     * Toggles PIN protection on/off.
     */
    fun togglePinProtection() {
        val currentState = _uiState.value
        if (currentState.pinEnabled) {
            repository.disablePinProtection()
            _uiState.value = _uiState.value.copy(
                pinEnabled = false,
                currentPin = null
            )
        } else {
            val pin = repository.enablePinProtection()
            _uiState.value = _uiState.value.copy(
                pinEnabled = true,
                currentPin = pin
            )
        }
    }

    /**
     * Speaks the server address aloud using TTS.
     */
    fun speakAddress() {
        val state = _uiState.value
        if (!state.isServerRunning || state.ipAddress == null) {
            return
        }

        if (!ttsReady) {
            Log.w(TAG, "TTS not ready")
            return
        }

        // Format IP address for speech (e.g., "192 dot 168 dot 1 dot 45")
        val ipForSpeech = state.ipAddress.replace(".", " dot ")
        // Important: Emphasize http (not https)
        val text = "Open your browser and type h t t p colon slash slash $ipForSpeech colon ${state.port}. Remember, use h t t p, not h t t p s."

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "address_readout")
    }

    /**
     * Refreshes the upload list and storage info.
     */
    fun refreshUploads() {
        repository.refreshUploads()
        updateStorageInfo()
        updateRecentUploads()
        updatePinState()
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                refreshUploads()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun stopRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun updateConnectivityState() {
        val isConnected = NetworkUtils.isWifiConnected(getApplication())
        _uiState.value = _uiState.value.copy(isWifiConnected = isConnected)
    }

    private fun updateStorageInfo() {
        val available = repository.getAvailableStorage()
        val formatted = repository.getAvailableStorageFormatted()
        _uiState.value = _uiState.value.copy(
            availableStorage = formatted,
            availableStorageBytes = available
        )
    }

    private fun updateRecentUploads() {
        val uploads = repository.recentUploads.value.map { file ->
            UploadInfo(
                name = file.name,
                sizeFormatted = file.sizeFormatted,
                timeAgo = formatTimeAgo(file.uploadedAt)
            )
        }
        _uiState.value = _uiState.value.copy(recentUploads = uploads)
    }

    private fun updatePinState() {
        _uiState.value = _uiState.value.copy(
            pinEnabled = repository.pinEnabled.value,
            currentPin = repository.currentPin.value
        )
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000} min ago"
            diff < 86400_000 -> "${diff / 3600_000} hr ago"
            else -> "${diff / 86400_000} days ago"
        }
    }

    /**
     * Returns the full server URL for display.
     */
    fun getServerUrl(): String? {
        val state = _uiState.value
        return if (state.isServerRunning && state.ipAddress != null) {
            "http://${state.ipAddress}:${state.port}"
        } else null
    }

    /**
     * Checks if storage is running low (less than 2GB).
     */
    fun isStorageLow(): Boolean {
        return _uiState.value.availableStorageBytes < 2_000_000_000L
    }

    /**
     * Checks if storage is critical (less than 500MB).
     */
    fun isStorageCritical(): Boolean {
        return _uiState.value.availableStorageBytes < 500_000_000L
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

