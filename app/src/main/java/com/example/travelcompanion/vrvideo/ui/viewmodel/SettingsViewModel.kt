package com.example.travelcompanion.vrvideo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelcompanion.vrvideo.data.db.PlaybackSettings
import com.example.travelcompanion.vrvideo.data.db.StereoLayout
import com.example.travelcompanion.vrvideo.data.db.VideoLibraryDatabase
import com.example.travelcompanion.vrvideo.domain.permission.PermissionStatus
import com.example.travelcompanion.vrvideo.domain.permission.VideoPermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the settings screen.
 * Manages playback settings (defaultViewMode, skipInterval, resumeEnabled) and permission status.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
  private val db = VideoLibraryDatabase.getInstance(application)
  private val settingsDao = db.playbackSettingsDao()

  /**
   * Current permission status for video access.
   */
  private val _permissionStatus = MutableStateFlow(
      VideoPermissionManager.getPermissionStatus(application)
  )
  val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

  /**
   * Flow of current playback settings.
   */
  val settings: StateFlow<PlaybackSettings?> = settingsDao.getFlow()
      .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = null
      )

  init {
    // Ensure default settings exist
    viewModelScope.launch {
      if (settingsDao.get() == null) {
        settingsDao.upsert(PlaybackSettings())
      }
    }
  }

  /**
   * Update the default view mode.
   */
  fun updateDefaultViewMode(mode: StereoLayout) {
    viewModelScope.launch {
      val current = settingsDao.get() ?: PlaybackSettings()
      settingsDao.upsert(current.copy(defaultViewMode = mode))
    }
  }

  /**
   * Update the skip interval in milliseconds.
   */
  fun updateSkipInterval(intervalMs: Int) {
    viewModelScope.launch {
      val current = settingsDao.get() ?: PlaybackSettings()
      settingsDao.upsert(current.copy(skipIntervalMs = intervalMs))
    }
  }

  /**
   * Update whether resume is enabled.
   */
  fun updateResumeEnabled(enabled: Boolean) {
    viewModelScope.launch {
      val current = settingsDao.get() ?: PlaybackSettings()
      settingsDao.upsert(current.copy(resumeEnabled = enabled))
    }
  }
}

