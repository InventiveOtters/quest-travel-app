package com.inotter.onthegovr.ui.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.inotter.onthegovr.data.datasources.videolibrary.models.ScanSettings
import com.inotter.onthegovr.data.datasources.videolibrary.models.VideoItem
import com.inotter.onthegovr.data.managers.PermissionManager.PermissionManager
import com.inotter.onthegovr.data.managers.PermissionManager.PermissionStatus
import com.inotter.onthegovr.data.managers.SAFManager.SAFManager
import com.inotter.onthegovr.data.repositories.LibraryRepository.LibraryRepository
import com.inotter.onthegovr.data.repositories.ScanSettingsRepository.ScanSettingsRepository
import com.inotter.onthegovr.data.repositories.VideoRepository.VideoRepository
import com.inotter.onthegovr.workers.IndexWorker
import com.inotter.onthegovr.workers.MediaStoreScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val libraryRepo: LibraryRepository,
    private val videoRepo: VideoRepository,
    private val scanSettingsRepo: ScanSettingsRepository,
    private val permissionManager: PermissionManager,
    private val safManager: SAFManager,
) : AndroidViewModel(application) {
  private val workManager = WorkManager.getInstance(application)

  // Search and sort state
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery

  private val _sortOption = MutableStateFlow(SortOption.RECENTLY_ADDED)
  val sortOption: StateFlow<SortOption> = _sortOption

  // Permission status
  private val _permissionStatus = MutableStateFlow(
      permissionManager.getPermissionStatus()
  )
  val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

  /**
   * Flow of scan settings for MediaStore auto-scan.
   */
  val scanSettings: StateFlow<ScanSettings?> = scanSettingsRepo.getSettings()
      .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = null,
      )

  init {
    // Ensure scan settings are initialized
    viewModelScope.launch {
      scanSettingsRepo.ensureInitialized()

      // Auto-trigger MediaStore scan on launch if enabled and has permission
      val settings = scanSettingsRepo.getSettingsSync()
      if (settings.autoScanEnabled && permissionManager.hasAnyVideoAccess()) {
        triggerMediaStoreScan()
      }
    }
  }

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
      safManager.persistFromResult(data)

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

  /**
   * Refreshes the current permission status.
   * Call this after permission request results.
   */
  fun refreshPermissionStatus() {
    _permissionStatus.value = permissionManager.getPermissionStatus()
  }

  /**
   * Enables or disables MediaStore auto-scan.
   */
  fun setAutoScanEnabled(enabled: Boolean) {
    viewModelScope.launch {
      scanSettingsRepo.setAutoScanEnabled(enabled)
      if (enabled && permissionManager.hasAnyVideoAccess()) {
        triggerMediaStoreScan()
      }
    }
  }

  /**
   * Triggers a MediaStore scan in the background.
   */
  fun triggerMediaStoreScan() {
    val workRequest = OneTimeWorkRequestBuilder<MediaStoreScanWorker>().build()
    workManager.enqueueUniqueWork(
        MediaStoreScanWorker.WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        workRequest,
    )
  }
}

