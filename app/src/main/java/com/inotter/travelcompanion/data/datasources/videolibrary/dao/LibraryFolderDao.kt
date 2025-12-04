package com.inotter.travelcompanion.data.datasources.videolibrary.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.inotter.travelcompanion.data.datasources.videolibrary.models.LibraryFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryFolderDao {
  @Insert
  suspend fun insert(folder: LibraryFolder): Long

  @Query("SELECT * FROM library_folders ORDER BY displayName ASC")
  fun getAll(): Flow<List<LibraryFolder>>

  @Query("DELETE FROM library_folders WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("SELECT * FROM library_folders WHERE id = :id")
  suspend fun getById(id: Long): LibraryFolder?

  @Query("SELECT * FROM library_folders WHERE treeUri = :treeUri")
  suspend fun getByTreeUri(treeUri: String): LibraryFolder?
}

