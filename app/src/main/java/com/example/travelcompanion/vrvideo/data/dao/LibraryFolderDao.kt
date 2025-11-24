package com.example.travelcompanion.vrvideo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.travelcompanion.vrvideo.data.db.LibraryFolder
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
}

