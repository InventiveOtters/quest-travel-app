package com.inotter.onthegovr.data.managers.TransferManager

import com.inotter.onthegovr.data.managers.TransferManager.models.IncompleteUpload
import com.inotter.onthegovr.data.managers.TransferManager.models.OrphanedMediaStoreEntry

/**
 * Manager interface for WiFi transfer operations.
 * Provides high-level transfer functionality including network utilities,
 * file validation, upload server management, and incomplete upload detection.
 */
interface TransferManager {

    // ============ Network Utilities ============

    /**
     * Gets the device's WiFi IP address.
     *
     * @return The WiFi IP address as a string (e.g., "192.168.1.45"), or null if not available
     */
    fun getWifiIpAddress(): String?

    /**
     * Checks if the device is connected to a WiFi network.
     *
     * @return true if connected to WiFi, false otherwise
     */
    fun isWifiConnected(): Boolean

    /**
     * Gets the network name (SSID) if available.
     *
     * @return The WiFi SSID or null if not available
     */
    fun getWifiSsid(): String?

    // ============ File Validation ============

    /**
     * Validates if a file is a supported video file based on extension and MIME type.
     *
     * @param filename The name of the file being uploaded
     * @param mimeType The MIME type reported by the client (may be null or unreliable)
     * @return true if the file appears to be a valid video file
     */
    fun isValidVideoFile(filename: String, mimeType: String?): Boolean

    /**
     * Validates file content by checking magic bytes.
     *
     * @param header The first 12+ bytes of the file
     * @param filename The filename (for extension checking)
     * @return true if the file content matches expected format
     */
    fun isValidVideoContent(header: ByteArray, filename: String): Boolean

    /**
     * Checks if there's enough storage available for a file of the given size.
     *
     * @param fileSize Size of the file to be uploaded in bytes
     * @return true if there's enough space (file size + 500MB buffer)
     */
    fun hasEnoughStorage(fileSize: Long): Boolean

    /**
     * Gets the available storage space on the device.
     *
     * @return Available storage in bytes
     */
    fun getAvailableStorage(): Long

    /**
     * Formats a byte count as a human-readable string.
     *
     * @param bytes Number of bytes
     * @return Formatted string (e.g., "2.5 GB", "150 MB")
     */
    fun formatBytes(bytes: Long): String

    /**
     * Gets the list of supported file extensions as a user-friendly string.
     */
    fun getSupportedExtensionsDisplay(): String

    // ============ Incomplete Upload Detection ============

    /**
     * Detects all incomplete uploads by checking both the database and MediaStore.
     *
     * @return List of incomplete uploads with their validation status
     */
    suspend fun detectIncompleteUploads(): List<IncompleteUpload>

    /**
     * Detects orphaned MediaStore entries that have no corresponding database session.
     *
     * @return List of truly orphaned entries (in MediaStore but not in DB)
     */
    suspend fun detectOrphanedMediaStoreEntries(): List<OrphanedMediaStoreEntry>

    /**
     * Cleans up a single incomplete upload by deleting both the MediaStore entry
     * and the database session record.
     *
     * @param upload The incomplete upload to clean up
     * @return true if cleanup succeeded, false otherwise
     */
    suspend fun cleanupUpload(upload: IncompleteUpload): Boolean

    /**
     * Cleans up an orphaned MediaStore entry (one without a database record).
     *
     * @param entry The orphaned entry to clean up
     * @return true if cleanup succeeded, false otherwise
     */
    fun cleanupOrphanedEntry(entry: OrphanedMediaStoreEntry): Boolean

    /**
     * Cleans up all incomplete uploads and orphaned MediaStore entries.
     *
     * @return Number of uploads successfully cleaned up (including orphaned entries)
     */
    suspend fun cleanupAllIncompleteUploads(): Int

    /**
     * Gets the total count of incomplete uploads (including orphaned MediaStore entries).
     */
    suspend fun getIncompleteCount(): Int

    /**
     * Calculates total storage used by incomplete uploads (including orphaned entries).
     */
    suspend fun getIncompleteStorageUsed(): Long
}

