package com.inotter.travelcompanion.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an upload session for resumable uploads.
 * Tracks the state of uploads that may be interrupted (e.g., by power loss).
 *
 * When an upload starts, a session is created with the expected file details.
 * On interruption, this allows the app to detect incomplete uploads and offer
 * the user options to resume or clean up.
 *
 * @property id Auto-generated primary key
 * @property filename Original filename being uploaded
 * @property expectedSize Total expected file size in bytes
 * @property bytesReceived Number of bytes successfully written to MediaStore
 * @property mediaStoreUri Content URI of the pending MediaStore entry
 * @property mimeType MIME type of the file (e.g., "video/mp4")
 * @property createdAt Timestamp when the upload session was created (epoch millis)
 * @property lastUpdatedAt Timestamp of last progress update (epoch millis)
 * @property status Current status of the upload session
 */
@Entity(
    tableName = "upload_sessions",
    indices = [Index(value = ["mediaStoreUri"], unique = true)]
)
data class UploadSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filename: String,
    val expectedSize: Long,
    val bytesReceived: Long = 0,
    val mediaStoreUri: String,
    val mimeType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val status: UploadSessionStatus = UploadSessionStatus.IN_PROGRESS
) {
    /**
     * Returns the upload progress as a percentage (0-100).
     */
    val progressPercent: Int
        get() = if (expectedSize > 0) {
            ((bytesReceived * 100) / expectedSize).toInt().coerceIn(0, 100)
        } else 0

    /**
     * Returns true if this upload session appears to be orphaned
     * (started more than the specified duration ago without completion).
     */
    fun isOrphaned(maxAgeMillis: Long = 24 * 60 * 60 * 1000): Boolean {
        return status == UploadSessionStatus.IN_PROGRESS &&
               System.currentTimeMillis() - lastUpdatedAt > maxAgeMillis
    }
}

/**
 * Status of an upload session.
 */
enum class UploadSessionStatus {
    /** Upload is currently in progress */
    IN_PROGRESS,
    /** Upload completed successfully */
    COMPLETED,
    /** Upload failed and was cleaned up */
    FAILED,
    /** Upload was cancelled by user */
    CANCELLED
}

