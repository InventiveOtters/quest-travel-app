package com.inotter.travelcompanion.data.managers.TransferManager

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.StatFs
import android.provider.MediaStore
import com.inotter.travelcompanion.data.managers.TransferManager.models.IncompleteUpload
import com.inotter.travelcompanion.data.managers.TransferManager.models.OrphanedMediaStoreEntry
import com.inotter.travelcompanion.data.repositories.UploadSessionRepository.UploadSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [TransferManager] that provides WiFi transfer operations.
 *
 * @property context Android application context
 * @property contentResolver ContentResolver for MediaStore operations
 * @property uploadSessionRepository Repository for upload session data
 */
@Singleton
class TransferManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
    private val uploadSessionRepository: UploadSessionRepository
) : TransferManager {

    companion object {
        private const val TAG = "TransferManagerImpl"

        /** Subfolder within Movies where uploaded files are stored */
        const val SUBFOLDER_NAME = "TravelCompanion"

        /** Full relative path for MediaStore */
        const val RELATIVE_PATH = "Movies/$SUBFOLDER_NAME"

        /** Supported video file extensions (lowercase) */
        private val SUPPORTED_EXTENSIONS = setOf("mp4", "mkv")

        /** Valid MIME types for video files */
        private val VALID_MIME_TYPES = setOf(
            "video/mp4",
            "video/x-matroska",
            "video/webm",
            "application/octet-stream"
        )

        /** Minimum required free storage in bytes (500 MB buffer) */
        private const val MIN_STORAGE_BUFFER_BYTES = 500L * 1024 * 1024

        /** Magic bytes for MP4 files (ftyp box) */
        private val MP4_MAGIC = byteArrayOf(0x66, 0x74, 0x79, 0x70)

        /** Magic bytes for MKV/WebM files (EBML header) */
        private val MKV_MAGIC = byteArrayOf(0x1A, 0x45, (0xDF).toByte(), (0xA3).toByte())
    }

    // ============ Network Utilities ============

    override fun getWifiIpAddress(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiManager?.connectionInfo?.ipAddress?.let { ipInt ->
            if (ipInt != 0) {
                val ip = intToIpAddress(ipInt)
                if (ip != "0.0.0.0") return ip
            }
        }
        return getIpFromNetworkInterfaces()
    }

    override fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    override fun getWifiSsid(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ssid = wifiManager?.connectionInfo?.ssid
        return ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
    }

    private fun intToIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }

    private fun getIpFromNetworkInterfaces(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (networkInterface in interfaces.asSequence()) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val name = networkInterface.name.lowercase()
                if (!name.startsWith("wlan") && !name.startsWith("eth")) continue
                for (address in networkInterface.inetAddresses.asSequence()) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    // ============ File Validation ============

    override fun isValidVideoFile(filename: String, mimeType: String?): Boolean {
        val extension = filename.substringAfterLast('.', "").lowercase()
        if (extension !in SUPPORTED_EXTENSIONS) return false
        if (mimeType != null && mimeType.isNotBlank()) {
            val normalizedMime = mimeType.lowercase().trim()
            if (!VALID_MIME_TYPES.contains(normalizedMime) && !normalizedMime.startsWith("video/")) {
                return false
            }
        }
        return true
    }

    override fun isValidVideoContent(header: ByteArray, filename: String): Boolean {
        if (header.size < 12) return false
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4" -> isValidMp4Header(header)
            "mkv" -> isValidMkvHeader(header)
            else -> false
        }
    }

    private fun isValidMp4Header(header: ByteArray): Boolean {
        if (header.size < 8) return false
        return header[4] == MP4_MAGIC[0] && header[5] == MP4_MAGIC[1] &&
               header[6] == MP4_MAGIC[2] && header[7] == MP4_MAGIC[3]
    }

    private fun isValidMkvHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return header[0] == MKV_MAGIC[0] && header[1] == MKV_MAGIC[1] &&
               header[2] == MKV_MAGIC[2] && header[3] == MKV_MAGIC[3]
    }

    override fun hasEnoughStorage(fileSize: Long): Boolean {
        return getAvailableStorage() >= (fileSize + MIN_STORAGE_BUFFER_BYTES)
    }

    override fun getAvailableStorage(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) { 0L }
    }

    override fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    override fun getSupportedExtensionsDisplay(): String {
        return SUPPORTED_EXTENSIONS.joinToString(", ") { it.uppercase() }
    }

    // ============ Incomplete Upload Detection ============

    override suspend fun detectIncompleteUploads(): List<IncompleteUpload> {
        val sessions = uploadSessionRepository.getIncompleteSessions()
        return sessions.map { session ->
            val uri = Uri.parse(session.mediaStoreUri)
            val (exists, currentSize) = checkMediaStoreEntry(uri)
            IncompleteUpload(
                session = session,
                mediaStoreExists = exists,
                currentSize = currentSize
            )
        }
    }

    private fun checkMediaStoreEntry(uri: Uri): Pair<Boolean, Long> {
        return try {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.Video.Media.SIZE, MediaStore.Video.Media.IS_PENDING),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Pair(true, cursor.getLong(0))
                } else Pair(false, 0L)
            } ?: Pair(false, 0L)
        } catch (_: Exception) { Pair(false, 0L) }
    }

    override suspend fun detectOrphanedMediaStoreEntries(): List<OrphanedMediaStoreEntry> {
        val allPending = detectAllPendingMediaStoreEntries()
        if (allPending.isEmpty()) return emptyList()
        val knownUris = uploadSessionRepository.getIncompleteSessions()
            .map { it.mediaStoreUri }.toSet()
        return allPending.filter { it.contentUri.toString() !in knownUris }
    }

    private fun detectAllPendingMediaStoreEntries(): List<OrphanedMediaStoreEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val orphaned = mutableListOf<OrphanedMediaStoreEntry>()
        try {
            val selection = "(${MediaStore.Video.Media.RELATIVE_PATH} = ? OR ${MediaStore.Video.Media.RELATIVE_PATH} = ?) AND ${MediaStore.Video.Media.IS_PENDING} = ?"
            val selectionArgs = arrayOf(RELATIVE_PATH, "$RELATIVE_PATH/", "1")
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE),
                selection, selectionArgs, null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    orphaned.add(OrphanedMediaStoreEntry(
                        mediaStoreId = id,
                        contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        displayName = cursor.getString(nameColumn) ?: "unknown",
                        currentSize = cursor.getLong(sizeColumn)
                    ))
                }
            }
        } catch (_: Exception) { }
        return orphaned
    }

    override suspend fun cleanupUpload(upload: IncompleteUpload): Boolean {
        return try {
            if (upload.mediaStoreExists) {
                contentResolver.delete(Uri.parse(upload.session.mediaStoreUri), null, null)
            }
            uploadSessionRepository.markCancelled(upload.session.id)
            true
        } catch (_: Exception) { false }
    }

    override fun cleanupOrphanedEntry(entry: OrphanedMediaStoreEntry): Boolean {
        return try {
            contentResolver.delete(entry.contentUri, null, null) > 0
        } catch (_: Exception) { false }
    }

    override suspend fun cleanupAllIncompleteUploads(): Int {
        var count = 0
        detectIncompleteUploads().forEach { if (cleanupUpload(it)) count++ }
        detectOrphanedMediaStoreEntries().forEach { if (cleanupOrphanedEntry(it)) count++ }
        uploadSessionRepository.cleanupFinishedSessions()
        return count
    }

    override suspend fun getIncompleteCount(): Int {
        return uploadSessionRepository.getIncompleteSessions().size + detectOrphanedMediaStoreEntries().size
    }

    override suspend fun getIncompleteStorageUsed(): Long {
        val dbUploads = detectIncompleteUploads()
        val orphaned = detectOrphanedMediaStoreEntries()
        return dbUploads.sumOf { it.currentSize } + orphaned.sumOf { it.currentSize }
    }
}

