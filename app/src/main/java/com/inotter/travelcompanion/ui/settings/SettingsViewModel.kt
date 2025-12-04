package com.inotter.travelcompanion.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inotter.travelcompanion.data.datasources.videolibrary.VideoLibraryDataSource
import com.inotter.travelcompanion.data.datasources.videolibrary.models.PlaybackSettings
import com.inotter.travelcompanion.data.datasources.videolibrary.models.StereoLayout
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionManager
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the settings screen.
 * Manages playback settings (defaultViewMode, skipInterval, resumeEnabled) and permission status.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val dataSource: VideoLibraryDataSource,
    private val permissionManager: PermissionManager,
) : AndroidViewModel(application) {

  /**
   * Current permission status for video access.
   */
  private val _permissionStatus = MutableStateFlow(
      permissionManager.getPermissionStatus()
  )
  val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

  /**
   * Flow of current playback settings.
   */
  val settings: StateFlow<PlaybackSettings?> = dataSource.getPlaybackSettingsFlow()
      .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = null
      )

  init {
    // Ensure default settings exist
    viewModelScope.launch {
      if (dataSource.getPlaybackSettings() == null) {
        dataSource.upsertPlaybackSettings(PlaybackSettings())
      }
    }
  }

  /**
   * Update the default view mode.
   */
  fun updateDefaultViewMode(mode: StereoLayout) {
    viewModelScope.launch {
      val current = dataSource.getPlaybackSettings() ?: PlaybackSettings()
      dataSource.upsertPlaybackSettings(current.copy(defaultViewMode = mode))
    }
  }

  /**
   * Update the skip interval in milliseconds.
   */
  fun updateSkipInterval(intervalMs: Int) {
    viewModelScope.launch {
      val current = dataSource.getPlaybackSettings() ?: PlaybackSettings()
      dataSource.upsertPlaybackSettings(current.copy(skipIntervalMs = intervalMs))
    }
  }

  /**
   * Update whether resume is enabled.
   */
  fun updateResumeEnabled(enabled: Boolean) {
    viewModelScope.launch {
      val current = dataSource.getPlaybackSettings() ?: PlaybackSettings()
      dataSource.upsertPlaybackSettings(current.copy(resumeEnabled = enabled))
    }
  }
}

