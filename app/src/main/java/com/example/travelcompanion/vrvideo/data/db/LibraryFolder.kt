package com.example.travelcompanion.vrvideo.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a library folder containing video files.
 * Each folder is identified by a unique SAF tree URI.
 *
 * @property id Auto-generated primary key
 * @property treeUri SAF document tree URI (unique)
 * @property displayName User-friendly folder name
 * @property includeSubfolders Whether to scan subfolders during indexing
 * @property addedAt Timestamp when the folder was added (epoch millis)
 * @property lastScanTime Timestamp of the last successful scan (epoch millis)
 */
@Entity(
    tableName = "library_folders",
    indices = [Index(value = ["treeUri"], unique = true)],
)
data class LibraryFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val includeSubfolders: Boolean = true,
    val addedAt: Long,
    val lastScanTime: Long? = null,
)

