package com.inotter.travelcompanion.data.repositories.TransferRepository

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Repository interface for managing the WiFi transfer server state.
 * Acts as the bridge between the UI layer and the TransferService.
 */
interface TransferRepository {

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

    /** Current state of the transfer server */
    val serverState: StateFlow<ServerState>

    /** List of recently uploaded files */
    val recentUploads: StateFlow<List<UploadedFile>>

    /** Current PIN if PIN protection is enabled */
    val currentPin: StateFlow<String?>

    /** Whether PIN protection is enabled */
    val pinEnabled: StateFlow<Boolean>

    /**
     * Starts the WiFi transfer server.
     * Binds to the TransferService and starts it as a foreground service.
     *
     * @return Result containing the server URL on success, or error message on failure
     */
    fun startServer(): Result<String>

    /**
     * Stops the WiFi transfer server.
     */
    fun stopServer()

    /**
     * Returns the directory where uploaded files are saved.
     */
    fun getUploadDirectory(): File

    /**
     * Enables PIN protection and returns the generated PIN.
     * Can be called before or after server starts - PIN will be applied when service connects.
     */
    fun enablePinProtection(): String

    /**
     * Disables PIN protection.
     */
    fun disablePinProtection()

    /**
     * Refreshes the upload list from the service.
     */
    fun refreshUploads()

    /**
     * Returns the available storage space formatted as a string.
     */
    fun getAvailableStorageFormatted(): String

    /**
     * Returns the available storage in bytes.
     */
    fun getAvailableStorage(): Long
}

