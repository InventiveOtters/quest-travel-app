package com.inotter.travelcompanion.data.managers.TransferManager.models

import android.net.Uri
import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSession

/**
 * Data class representing an incomplete upload with validation status.
 */
data class IncompleteUpload(
    val session: UploadSession,
    val mediaStoreExists: Boolean,
    val currentSize: Long
) {
    /** Whether this upload can be resumed (MediaStore entry still exists) */
    val canResume: Boolean get() = mediaStoreExists && session.bytesReceived > 0

    /** Formatted progress string */
    val progressText: String get() = "${session.progressPercent}%"

    /** Formatted size string */
    val sizeText: String get() = formatBytes(session.expectedSize)

    /** Formatted received bytes string */
    val receivedText: String get() = formatBytes(currentSize)

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}

/**
 * Data class representing an orphaned MediaStore entry without a database record.
 * These are pending files in Movies/TravelCompanion that we created but lost track of.
 */
data class OrphanedMediaStoreEntry(
    val mediaStoreId: Long,
    val contentUri: Uri,
    val displayName: String,
    val currentSize: Long
) {
    /** Formatted size string */
    val sizeText: String get() = formatBytes(currentSize)

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}

/**
 * Represents an incomplete upload that can be resumed.
 * Used by the UploadServer to expose resumable uploads.
 */
data class ResumableUpload(
    val sessionId: Long,
    val filename: String,
    val expectedSize: Long,
    val bytesReceived: Long,
    val mediaStoreUri: String,
    val progressPercent: Int
)

