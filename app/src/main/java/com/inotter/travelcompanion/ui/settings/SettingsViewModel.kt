package com.inotter.travelcompanion.ui.settings

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inotter.travelcompanion.data.datasources.videolibrary.VideoLibraryDataSource
import com.inotter.travelcompanion.data.datasources.videolibrary.models.PlaybackSettings
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionManager
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionStatus
import com.inotter.travelcompanion.data.models.ViewingMode
import com.inotter.travelcompanion.ui.onboarding.OnboardingViewModel
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
 * Manages playback settings (skipInterval, resumeEnabled), permission status, and viewing mode.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val dataSource: VideoLibraryDataSource,
    private val permissionManager: PermissionManager,
) : AndroidViewModel(application) {

  private val prefs: SharedPreferences = application.getSharedPreferences(
      OnboardingViewModel.PREFS_NAME,
      android.content.Context.MODE_PRIVATE
  )

  /**
   * Current permission status for video access.
   */
  private val _permissionStatus = MutableStateFlow(
      permissionManager.getPermissionStatus()
  )
  val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

  /**
   * Current viewing mode preference.
   */
  private val _viewingMode = MutableStateFlow(getStoredViewingMode())
  val viewingMode: StateFlow<ViewingMode> = _viewingMode.asStateFlow()

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
   * Get the stored viewing mode from SharedPreferences.
   */
  private fun getStoredViewingMode(): ViewingMode {
    val modeString = prefs.getString(OnboardingViewModel.KEY_VIEWING_MODE, null)
    return ViewingMode.fromString(modeString)
  }

  /**
   * Update the viewing mode preference.
   * Returns the new mode so the caller can trigger mode transition.
   */
  fun updateViewingMode(mode: ViewingMode): ViewingMode {
    prefs.edit().putString(OnboardingViewModel.KEY_VIEWING_MODE, mode.name).apply()
    _viewingMode.value = mode
    return mode
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

