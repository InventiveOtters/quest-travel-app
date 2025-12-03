package com.inotter.travelcompanion.domain.transfer

import android.content.Context
import android.os.StatFs

/**
 * Validates uploaded files for the WiFi transfer feature.
 * Ensures only supported video formats are accepted and sufficient storage is available.
 */
object FileValidator {

    /** Supported video file extensions (lowercase) */
    private val SUPPORTED_EXTENSIONS = setOf("mp4", "mkv")

    /** Valid MIME types for video files */
    private val VALID_MIME_TYPES = setOf(
        "video/mp4",
        "video/x-matroska",
        "video/webm",  // Sometimes used for MKV
        "application/octet-stream"  // Generic binary, allow if extension matches
    )

    /** Minimum required free storage in bytes (500 MB buffer) */
    private const val MIN_STORAGE_BUFFER_BYTES = 500L * 1024 * 1024

    /** Magic bytes for MP4 files (ftyp box) */
    private val MP4_MAGIC = byteArrayOf(0x66, 0x74, 0x79, 0x70)  // "ftyp"

    /** Magic bytes for MKV/WebM files (EBML header) */
    private val MKV_MAGIC = byteArrayOf(0x1A, 0x45, (0xDF).toByte(), (0xA3).toByte())

    /**
     * Validates if a file is a supported video file based on extension and MIME type.
     *
     * @param filename The name of the file being uploaded
     * @param mimeType The MIME type reported by the client (may be null or unreliable)
     * @return true if the file appears to be a valid video file
     */
    fun isValidVideoFile(filename: String, mimeType: String?): Boolean {
        val extension = filename.substringAfterLast('.', "").lowercase()

        // Extension must be in our supported list
        if (extension !in SUPPORTED_EXTENSIONS) {
            return false
        }

        // If MIME type is provided, it should be valid (but we're lenient)
        if (mimeType != null && mimeType.isNotBlank()) {
            val normalizedMime = mimeType.lowercase().trim()
            if (!VALID_MIME_TYPES.contains(normalizedMime) && !normalizedMime.startsWith("video/")) {
                return false
            }
        }

        return true
    }

    /**
     * Validates file content by checking magic bytes.
     * Should be called after receiving the first few bytes of an upload.
     *
     * @param header The first 12+ bytes of the file
     * @param filename The filename (for extension checking)
     * @return true if the file content matches expected format
     */
    fun isValidVideoContent(header: ByteArray, filename: String): Boolean {
        if (header.size < 12) return false

        val extension = filename.substringAfterLast('.', "").lowercase()

        return when (extension) {
            "mp4" -> isValidMp4Header(header)
            "mkv" -> isValidMkvHeader(header)
            else -> false
        }
    }

    /**
     * Checks if there's enough storage available for a file of the given size.
     *
     * @param context Android application context
     * @param fileSize Size of the file to be uploaded in bytes
     * @return true if there's enough space (file size + 500MB buffer)
     */
    fun hasEnoughStorage(context: Context, fileSize: Long): Boolean {
        val available = getAvailableStorage(context)
        return available >= (fileSize + MIN_STORAGE_BUFFER_BYTES)
    }

    /**
     * Gets the available storage space on the device.
     *
     * @param context Android application context
     * @return Available storage in bytes
     */
    fun getAvailableStorage(context: Context): Long {
        return try {
            val path = context.filesDir
            val stat = StatFs(path.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Formats a byte count as a human-readable string.
     *
     * @param bytes Number of bytes
     * @return Formatted string (e.g., "2.5 GB", "150 MB")
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    /**
     * Validates MP4 file header.
     * MP4 files should have "ftyp" at byte offset 4.
     */
    private fun isValidMp4Header(header: ByteArray): Boolean {
        // Check for "ftyp" at offset 4
        if (header.size < 8) return false
        return header[4] == MP4_MAGIC[0] &&
               header[5] == MP4_MAGIC[1] &&
               header[6] == MP4_MAGIC[2] &&
               header[7] == MP4_MAGIC[3]
    }

    /**
     * Validates MKV/WebM file header.
     * MKV files start with EBML header (0x1A 0x45 0xDF 0xA3).
     */
    private fun isValidMkvHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return header[0] == MKV_MAGIC[0] &&
               header[1] == MKV_MAGIC[1] &&
               header[2] == MKV_MAGIC[2] &&
               header[3] == MKV_MAGIC[3]
    }

    /**
     * Gets the list of supported file extensions as a user-friendly string.
     */
    fun getSupportedExtensionsDisplay(): String {
        return SUPPORTED_EXTENSIONS.joinToString(", ") { it.uppercase() }
    }
}

