package com.example.travelcompanion.vrvideo.domain.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.travelcompanion.MainActivity
import com.example.travelcompanion.vrvideo.domain.scan.MediaStoreScanWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that manages the WiFi transfer HTTP server.
 * Keeps the server running even when the app is in the background.
 * Provides persistent notification with server status and stop action.
 *
 * Files are uploaded directly to MediaStore (Movies/TravelCompanion/) so they:
 * - Survive app uninstall
 * - Are visible to other apps (file managers, galleries)
 * - Are discovered by MediaStoreScanWorker automatically
 */
class TransferService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "wifi_transfer_channel"
        const val NOTIFICATION_CHANNEL_UPLOAD_ID = "wifi_transfer_upload_channel"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_UPLOAD_ID = 1002
        const val NOTIFICATION_UPLOAD_COMPLETE_ID = 1003
        const val ACTION_STOP = "com.example.travelcompanion.STOP_TRANSFER_SERVICE"

        private var _instance: TransferService? = null
        val instance: TransferService? get() = _instance

        /** Returns true if the service is currently running */
        fun isRunning(): Boolean = _instance?.server?.isAlive == true
    }

    /** Service state */
    sealed class State {
        object Stopped : State()
        data class Running(val ipAddress: String, val port: Int) : State()
        data class Error(val message: String) : State()
    }

    inner class LocalBinder : Binder() {
        fun getService(): TransferService = this@TransferService
    }

    private val binder = LocalBinder()
    private var server: UploadServer? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _uploadedFiles = MutableStateFlow<List<UploadServer.UploadedFile>>(emptyList())
    val uploadedFiles: StateFlow<List<UploadServer.UploadedFile>> = _uploadedFiles.asStateFlow()

    /** Current PIN for authentication, null if PIN protection is disabled */
    private val _currentPin = MutableStateFlow<String?>(null)
    val currentPin: StateFlow<String?> = _currentPin.asStateFlow()

    /** Whether PIN protection is enabled */
    private val _pinEnabled = MutableStateFlow(false)
    val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        _instance = this
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification("Starting server..."))

        // Start the server
        startServer()

        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        serviceScope.cancel()
        _instance = null
        super.onDestroy()
    }

    /** Starts the HTTP server */
    private fun startServer() {
        if (server?.isAlive == true) {
            // Server already running - update state to reflect current state
            val currentIp = NetworkUtils.getWifiIpAddress(this)
            if (currentIp != null) {
                val runningState = _state.value
                if (runningState is State.Running) {
                    // Already in running state, nothing to do
                    return
                }
            }
            return
        }

        val ipAddress = NetworkUtils.getWifiIpAddress(this)
        if (ipAddress == null) {
            _state.value = State.Error("Not connected to WiFi")
            updateNotification("Error: Not connected to WiFi")
            return
        }

        // Check if WiFi is actually connected (not just has IP)
        if (!NetworkUtils.isWifiConnected(this)) {
            _state.value = State.Error("WiFi network not available")
            updateNotification("Error: WiFi network not available")
            return
        }

        try {
            // Use fallback ports mechanism - files go directly to MediaStore
            val (newServer, actualPort) = UploadServer.createWithFallbackPorts(
                context = applicationContext,
                contentResolver = contentResolver,
                onFileUploaded = { uri -> onFileUploaded(uri) },
                onUploadProgress = { filename, progress -> onUploadProgress(filename, progress) },
                onUploadStarted = { filename, size -> onUploadStarted(filename, size) },
                onUploadCompleted = { filename, success -> onUploadCompleted(filename, success) },
                pinVerifier = { pin -> verifyPin(pin) },
                isPinEnabled = { _pinEnabled.value }
            )

            server = newServer

            // Observe uploaded files
            serviceScope.launch {
                newServer.uploadedFiles.collect { files ->
                    _uploadedFiles.value = files
                }
            }

            _state.value = State.Running(ipAddress, actualPort)
            updateNotification("Server running at http://$ipAddress:$actualPort")

        } catch (e: java.net.BindException) {
            _state.value = State.Error("All server ports are in use. Please try again later.")
            updateNotification("Error: All ports in use")
            android.util.Log.e("TransferService", "Failed to bind to any port", e)
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("permission", ignoreCase = true) == true ->
                    "Permission denied. Please check app permissions."
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Network error. Please check your connection."
                else -> "Failed to start: ${e.message}"
            }
            _state.value = State.Error(errorMsg)
            updateNotification("Error: $errorMsg")
            android.util.Log.e("TransferService", "Failed to start server", e)
        }
    }

    /** Stops the HTTP server */
    fun stopServer() {
        server?.stop()
        server = null
        _state.value = State.Stopped
    }

    /** Called when a file is successfully uploaded to MediaStore */
    private fun onFileUploaded(contentUri: Uri) {
        // Update notification
        val currentState = _state.value
        if (currentState is State.Running) {
            val count = server?.getUploadCount() ?: 0
            updateNotification("Server at ${currentState.ipAddress}:${currentState.port} • $count uploads")
        }

        // Trigger video indexing for the uploads folder
        triggerIndexing()
    }

    /** Called when an upload starts */
    private fun onUploadStarted(filename: String, size: Long) {
        showUploadProgressNotification(filename, 0, FileValidator.formatBytes(size))
    }

    /** Called when upload progress updates */
    private fun onUploadProgress(filename: String, progress: Int) {
        showUploadProgressNotification(filename, progress, null)
    }

    /** Called when an upload completes */
    private fun onUploadCompleted(filename: String, success: Boolean) {
        // Cancel the progress notification
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_UPLOAD_ID)

        if (success) {
            showUploadCompleteNotification(filename)
        }
    }

    /** Shows upload progress in a separate notification */
    private fun showUploadProgressNotification(filename: String, progress: Int, fileSize: String?) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_UPLOAD_ID)
            .setContentTitle("Uploading: $filename")
            .setContentText(if (fileSize != null) "Size: $fileSize" else "$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        notificationManager.notify(NOTIFICATION_UPLOAD_ID, builder.build())
    }

    /** Shows upload complete notification */
    private fun showUploadCompleteNotification(filename: String) {
        val uploadCount = server?.getUploadCount() ?: 1

        // Intent to open the app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(NotificationManager::class.java)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_UPLOAD_ID)
            .setContentTitle("Upload Complete")
            .setContentText("$filename uploaded successfully")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$filename uploaded successfully.\nTotal uploads this session: $uploadCount"))

        notificationManager.notify(NOTIFICATION_UPLOAD_COMPLETE_ID, builder.build())
    }

    /** Enables PIN protection and generates a new 4-digit PIN */
    fun enablePinProtection(): String {
        val pin = generatePin()
        _currentPin.value = pin
        _pinEnabled.value = true
        android.util.Log.i("TransferService", "PIN protection enabled")
        return pin
    }

    /** Sets a specific PIN (used when PIN was pre-configured before server started) */
    fun setPin(pin: String) {
        _currentPin.value = pin
        _pinEnabled.value = true
        android.util.Log.i("TransferService", "PIN set from repository")
    }

    /** Disables PIN protection */
    fun disablePinProtection() {
        _currentPin.value = null
        _pinEnabled.value = false
        android.util.Log.i("TransferService", "PIN protection disabled")
    }

    /** Generates a new 4-digit PIN */
    private fun generatePin(): String {
        return (1000..9999).random().toString()
    }

    /** Verifies if the provided PIN matches the current PIN */
    fun verifyPin(pin: String): Boolean {
        val currentPin = _currentPin.value ?: return true // No PIN required if not enabled
        return pin == currentPin
    }

    /**
     * Triggers MediaStore scan to discover uploaded files.
     * Files are already in MediaStore (Movies/TravelCompanion/), just need to scan.
     */
    private fun triggerIndexing() {
        try {
            // Schedule MediaStoreScanWorker to discover the uploaded file
            val workRequest = OneTimeWorkRequestBuilder<MediaStoreScanWorker>().build()

            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                MediaStoreScanWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            android.util.Log.d("TransferService", "Scheduled MediaStore scan for uploaded file")
        } catch (e: Exception) {
            android.util.Log.e("TransferService", "Failed to trigger MediaStore scan", e)
        }
    }

    /** Creates the notification channels (required for Android O+) */
    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Main server status channel
        val serverChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "WiFi Transfer Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when WiFi transfer server is running"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(serverChannel)

        // Upload progress channel
        val uploadChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_UPLOAD_ID,
            "WiFi Transfer Uploads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows upload progress and completion notifications"
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(uploadChannel)
    }

    /** Creates the foreground notification */
    private fun createNotification(text: String): Notification {
        // Intent to open the app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop the service
        val stopIntent = Intent(this, TransferService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WiFi Transfer Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_upload)  // TODO: Replace with custom icon
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    /** Updates the notification text */
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /** Returns the current server URL if running */
    fun getServerUrl(): String? {
        val currentState = _state.value
        return if (currentState is State.Running) {
            "http://${currentState.ipAddress}:${currentState.port}"
        } else null
    }

    /** Returns the IP address if server is running */
    fun getIpAddress(): String? {
        val currentState = _state.value
        return if (currentState is State.Running) {
            currentState.ipAddress
        } else null
    }

    /** Returns the port if server is running */
    fun getPort(): Int? {
        val currentState = _state.value
        return if (currentState is State.Running) {
            currentState.port
        } else null
    }
}

