package com.inotter.travelcompanion.data.repositories.LibraryRepository

import android.net.Uri
import com.inotter.travelcompanion.data.datasources.videolibrary.models.LibraryFolder
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing library folders.
 * Provides operations to add, remove, and list folders that contain video files.
 */
interface LibraryRepository {
    /**
     * Returns a Flow of all library folders, ordered by display name.
     *
     * @return Flow emitting list of library folders
     */
    fun listFolders(): Flow<List<LibraryFolder>>

    /**
     * Adds a new library folder to the database.
     *
     * @param treeUri SAF document tree URI for the folder
     * @param displayName Optional custom display name (defaults to folder name from URI)
     * @param includeSubfolders Whether to include subfolders during indexing (default: true)
     * @return The ID of the newly inserted folder
     */
    suspend fun addFolder(treeUri: Uri, displayName: String? = null, includeSubfolders: Boolean = true): Long

    /**
     * Removes a library folder from the database.
     * Associated video items will be cascade-deleted.
     *
     * @param id The folder ID to remove
     */
    suspend fun removeFolder(id: Long)
}

