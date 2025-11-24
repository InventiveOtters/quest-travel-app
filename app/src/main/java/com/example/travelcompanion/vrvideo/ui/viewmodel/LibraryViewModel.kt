package com.example.travelcompanion.vrvideo.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.travelcompanion.vrvideo.data.db.VideoItem
import com.example.travelcompanion.vrvideo.data.db.VideoLibraryDatabase
import com.example.travelcompanion.vrvideo.data.repo.LibraryRepository
import com.example.travelcompanion.vrvideo.data.repo.VideoRepository
import com.example.travelcompanion.vrvideo.domain.saf.SAFPermissionManager
import com.example.travelcompanion.vrvideo.domain.scan.IndexWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sort options for the video library
 */
enum class SortOption {
  TITLE,
  DURATION,
  RECENTLY_ADDED
}

/**
 * ViewModel for the library screen.
 * Observes the video database and exposes operations for adding folders and querying videos.
 * Maps to contract: GET /videos → VideoRepository.queryVideos()
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
  private val db = VideoLibraryDatabase.getInstance(application)
  private val libraryRepo = LibraryRepository(application, db)
  private val videoRepo = VideoRepository(db)
  private val workManager = WorkManager.getInstance(application)

  // Search and sort state
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery

  private val _sortOption = MutableStateFlow(SortOption.RECENTLY_ADDED)
  val sortOption: StateFlow<SortOption> = _sortOption

  /**
   * Flow of all library folders.
   * Maps to contract: GET /folders
   */
  val folders = libraryRepo.listFolders()
      .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = emptyList(),
      )

  /**
   * Flow of all videos in the library with search and sort applied.
   * Maps to contract: GET /videos
   */
  val videos: StateFlow<List<VideoItem>> =
      combine(
          videoRepo.queryVideos(),
          _searchQuery,
          _sortOption,
      ) { allVideos, query, sort ->
        // Apply search filter
        val filtered = if (query.isBlank()) {
          allVideos
        } else {
          allVideos.filter { it.title.contains(query, ignoreCase = true) }
        }

        // Apply sort
        when (sort) {
          SortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
          SortOption.DURATION -> filtered.sortedByDescending { it.durationMs }
          SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.createdAt }
        }
      }.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = emptyList(),
      )

  /**
   * Add a folder to the library using SAF tree URI.
   * Persists URI permissions and schedules indexing.
   * Maps to contract: POST /folders
   */
  fun addFolder(data: Intent?) {
    viewModelScope.launch {
      // Persist SAF permissions
      SAFPermissionManager.persistFromResult(getApplication(), data)

      val uri = data?.data ?: return@launch
      
      // Add folder to database
      val folderId = libraryRepo.addFolder(uri)

      // Schedule indexing worker
      val workRequest =
          OneTimeWorkRequestBuilder<IndexWorker>()
              .setInputData(IndexWorker.inputData(uri, folderId))
              .build()

      workManager.enqueueUniqueWork(
          "index_folder_$folderId",
          ExistingWorkPolicy.REPLACE,
          workRequest,
      )
    }
  }

  /**
   * Remove a video from the index (file is not deleted).
   * Maps to contract: DELETE /videos/{id}
   */
  fun removeVideo(id: Long) {
    viewModelScope.launch { videoRepo.deleteById(id) }
  }

  /**
   * Update the search query to filter videos by title
   */
  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  /**
   * Update the sort option for the video list
   */
  fun setSortOption(option: SortOption) {
    _sortOption.value = option
  }

  /**
   * Remove a folder from the library.
   * Maps to contract: DELETE /folders/{id}
   */
  fun removeFolder(id: Long) {
    viewModelScope.launch { libraryRepo.removeFolder(id) }
  }

  /**
   * Manually rescan a folder to update the index.
   * Maps to contract: POST /folders/{id}/rescan
   */
  fun rescanFolder(folderId: Long, treeUri: String) {
    viewModelScope.launch {
      val uri = Uri.parse(treeUri)
      val workRequest =
          OneTimeWorkRequestBuilder<IndexWorker>()
              .setInputData(IndexWorker.inputData(uri, folderId))
              .build()

      workManager.enqueueUniqueWork(
          "rescan_folder_$folderId",
          ExistingWorkPolicy.REPLACE,
          workRequest,
      )
    }
  }
}

