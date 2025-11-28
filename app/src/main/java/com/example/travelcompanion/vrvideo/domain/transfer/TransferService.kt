package com.example.travelcompanion.vrvideo.domain.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.travelcompanion.MainActivity
import com.example.travelcompanion.vrvideo.data.db.LibraryFolder
import com.example.travelcompanion.vrvideo.data.db.VideoLibraryDatabase
import com.example.travelcompanion.vrvideo.domain.scan.LocalDirectoryWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Foreground service that manages the WiFi transfer HTTP server.
 * Keeps the server running even when the app is in the background.
 * Provides persistent notification with server status and stop action.
 */
class TransferService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "wifi_transfer_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.travelcompanion.STOP_TRANSFER_SERVICE"

        /** Auto-stop timeout in milliseconds (30 minutes) */
        const val AUTO_STOP_TIMEOUT_MS = 30 * 60 * 1000L

        /** Check interval for auto-stop (1 minute) */
        const val AUTO_STOP_CHECK_INTERVAL_MS = 60 * 1000L

        /** Special URI prefix for WiFi uploads folder */
        const val WIFI_UPLOADS_FOLDER_URI = "file:///wifi_uploads"

        /** Display name for WiFi uploads folder */
        const val WIFI_UPLOADS_FOLDER_NAME = "WiFi Uploads"

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

    private var autoStopJob: Job? = null

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
            return // Already running
        }

        val ipAddress = NetworkUtils.getWifiIpAddress(this)
        if (ipAddress == null) {
            _state.value = State.Error("Not connected to WiFi")
            updateNotification("Error: Not connected to WiFi")
            return
        }

        val uploadDir = getUploadDirectory()
        val port = UploadServer.DEFAULT_PORT

        try {
            server = UploadServer(
                context = applicationContext,
                port = port,
                uploadDir = uploadDir,
                onFileUploaded = { file -> onFileUploaded(file) }
            ).also {
                it.start()

                // Observe uploaded files
                serviceScope.launch {
                    it.uploadedFiles.collect { files ->
                        _uploadedFiles.value = files
                    }
                }
            }

            _state.value = State.Running(ipAddress, port)
            updateNotification("Server running at http://$ipAddress:$port")
            startAutoStopTimer()

        } catch (e: Exception) {
            _state.value = State.Error("Failed to start: ${e.message}")
            updateNotification("Error: ${e.message}")
        }
    }

    /** Stops the HTTP server */
    fun stopServer() {
        autoStopJob?.cancel()
        server?.stop()
        server = null
        _state.value = State.Stopped
    }

    /** Called when a file is successfully uploaded */
    private fun onFileUploaded(file: File) {
        // Reset auto-stop timer
        startAutoStopTimer()

        // Update notification
        val currentState = _state.value
        if (currentState is State.Running) {
            val count = server?.getUploadCount() ?: 0
            updateNotification("Server at ${currentState.ipAddress}:${currentState.port} • $count uploads")
        }

        // Trigger video indexing for the uploads folder
        triggerIndexing()
    }

    /** Gets or creates the upload directory */
    private fun getUploadDirectory(): File {
        val dir = File(filesDir, "wifi_uploads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /** Returns the upload directory path */
    fun getUploadDirectoryPath(): File = getUploadDirectory()

    /** Starts/resets the auto-stop timer */
    private fun startAutoStopTimer() {
        autoStopJob?.cancel()
        autoStopJob = serviceScope.launch {
            delay(AUTO_STOP_TIMEOUT_MS)
            // Check if there was recent activity
            val lastActivity = server?.lastActivityTime?.value ?: 0L
            val timeSinceActivity = System.currentTimeMillis() - lastActivity
            if (timeSinceActivity >= AUTO_STOP_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    stopServer()
                    stopSelf()
                }
            } else {
                // Reset timer for remaining time
                startAutoStopTimer()
            }
        }
    }

    /** Triggers video indexing for uploaded files */
    private fun triggerIndexing() {
        serviceScope.launch {
            try {
                val db = VideoLibraryDatabase.getInstance(applicationContext)
                val foldersDao = db.libraryFolderDao()

                // Get or create the WiFi uploads folder in the database
                var folder = foldersDao.getByTreeUri(WIFI_UPLOADS_FOLDER_URI)
                if (folder == null) {
                    // Create a new library folder entry for WiFi uploads
                    val folderId = foldersDao.insert(
                        LibraryFolder(
                            treeUri = WIFI_UPLOADS_FOLDER_URI,
                            displayName = WIFI_UPLOADS_FOLDER_NAME,
                            includeSubfolders = false,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                    folder = foldersDao.getById(folderId)
                }

                val folderId = folder?.id ?: return@launch
                val uploadDir = getUploadDirectory()

                // Schedule LocalDirectoryWorker to index the uploads folder
                val workRequest = OneTimeWorkRequestBuilder<LocalDirectoryWorker>()
                    .setInputData(LocalDirectoryWorker.inputData(uploadDir.absolutePath, folderId))
                    .build()

                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "index_wifi_uploads",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                android.util.Log.d("TransferService", "Scheduled indexing for WiFi uploads folder")
            } catch (e: Exception) {
                android.util.Log.e("TransferService", "Failed to trigger indexing", e)
            }
        }
    }

    /** Creates the notification channel (required for Android O+) */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "WiFi Transfer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when WiFi transfer server is running"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
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

