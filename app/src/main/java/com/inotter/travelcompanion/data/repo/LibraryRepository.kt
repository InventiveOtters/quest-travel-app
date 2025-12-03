package com.inotter.travelcompanion.data.repo

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.inotter.travelcompanion.data.db.LibraryFolder
import com.inotter.travelcompanion.data.db.VideoLibraryDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing library folders.
 * Provides operations to add, remove, and list folders that contain video files.
 *
 * @property context Android application context for SAF operations
 * @property db Video library database instance
 */
class LibraryRepository(
    private val context: Context,
    private val db: VideoLibraryDatabase,
) {
  private val foldersDao = db.libraryFolderDao()

  /**
   * Returns a Flow of all library folders, ordered by display name.
   *
   * @return Flow emitting list of library folders
   */
  fun listFolders(): Flow<List<LibraryFolder>> = foldersDao.getAll()

  /**
   * Adds a new library folder to the database.
   *
   * @param treeUri SAF document tree URI for the folder
   * @param displayName Optional custom display name (defaults to folder name from URI)
   * @param includeSubfolders Whether to include subfolders during indexing (default: true)
   * @return The ID of the newly inserted folder
   */
  suspend fun addFolder(treeUri: Uri, displayName: String? = null, includeSubfolders: Boolean = true): Long {
    val doc = DocumentFile.fromTreeUri(context, treeUri)
    val name = displayName ?: doc?.name ?: treeUri.toString()
    val now = System.currentTimeMillis()
    return foldersDao.insert(
        LibraryFolder(
            treeUri = treeUri.toString(),
            displayName = name,
            includeSubfolders = includeSubfolders,
            addedAt = now,
        )
    )
  }

  /**
   * Removes a library folder from the database.
   * Associated video items will be cascade-deleted.
   *
   * @param id The folder ID to remove
   */
  suspend fun removeFolder(id: Long) = foldersDao.deleteById(id)
}

