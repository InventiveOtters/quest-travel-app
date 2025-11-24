package com.example.travelcompanion.vrvideo.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelcompanion.vrvideo.data.db.StereoLayout
import com.example.travelcompanion.vrvideo.data.db.VideoItem
import com.example.travelcompanion.vrvideo.data.db.VideoLibraryDatabase
import com.example.travelcompanion.vrvideo.data.repo.VideoRepository
import com.example.travelcompanion.vrvideo.domain.layout.StereoLayoutDetector
import com.example.travelcompanion.vrvideo.playback.PlaybackCore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for the player screen.
 * Binds PlaybackCore and manages playback lifecycle.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
  private val playbackCore = PlaybackCore(application)
  private val db = VideoLibraryDatabase.getInstance(application)
  private val videoRepo = VideoRepository(db)
  private val settingsDao = db.playbackSettingsDao()

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val _currentPosition = MutableStateFlow(0L)
  val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

  private val _currentStereoLayout = MutableStateFlow(StereoLayout.TwoD)
  val currentStereoLayout: StateFlow<StereoLayout> = _currentStereoLayout.asStateFlow()

  private var currentVideoId: Long? = null
  private var progressUpdateJob: Job? = null

  /**
   * Attach a surface for video rendering.
   * This should be called when the SurfaceTexture is available.
   */
  fun setSurface(surface: Surface?) {
    playbackCore.setSurface(surface)
  }

  /**
   * Get the underlying PlaybackCore for direct access if needed.
   */
  fun getPlaybackCore(): PlaybackCore = playbackCore

  /**
   * Prepare and load a video for playback.
   * Applies stereo layout based on: override > detected > default setting.
   */
  fun loadVideo(uri: Uri, videoId: Long, video: VideoItem) {
    currentVideoId = videoId
    viewModelScope.launch {
      // Determine stereo layout: override > detected > default
      val layout = determineStereoLayout(video)
      _currentStereoLayout.value = layout
      playbackCore.setStereoLayout(layout)

      playbackCore.prepare(uri)
      startProgressTracking()
    }
  }

  /**
   * Determine the stereo layout for a video.
   * Priority: stereoLayoutOverride > stereoLayout (detected) > defaultViewMode (settings)
   */
  private suspend fun determineStereoLayout(video: VideoItem): StereoLayout {
    // 1. Check for user override
    video.stereoLayoutOverride?.let { return it }

    // 2. Use detected layout if not Unknown
    if (video.stereoLayout != StereoLayout.Unknown) {
      return video.stereoLayout
    }

    // 3. Fallback to default from settings
    val settings = settingsDao.get()
    return settings?.defaultViewMode ?: StereoLayout.TwoD
  }

  /**
   * Set stereo layout override for the current video.
   */
  fun setStereoLayoutOverride(layout: StereoLayout) {
    viewModelScope.launch {
      currentVideoId?.let { id ->
        videoRepo.setStereoLayoutOverride(id, layout)
        _currentStereoLayout.value = layout
        playbackCore.setStereoLayout(layout)
      }
    }
  }

  /**
   * Start tracking playback progress and periodically save to database.
   */
  private fun startProgressTracking() {
    progressUpdateJob?.cancel()
    progressUpdateJob = viewModelScope.launch {
      while (isActive) {
        delay(5000) // Update every 5 seconds
        val position = playbackCore.getCurrentPosition()
        _currentPosition.value = position

        // Save progress to database
        currentVideoId?.let { id ->
          videoRepo.updatePlaybackProgress(
              id = id,
              lastPlayedAt = System.currentTimeMillis(),
              lastPositionMs = position
          )
        }
      }
    }
  }

  /**
   * Start playback.
   */
  fun play() {
    playbackCore.play()
    _isPlaying.value = true
  }

  /**
   * Pause playback.
   */
  fun pause() {
    playbackCore.pause()
    _isPlaying.value = false
  }

  /**
   * Seek to a specific position in milliseconds.
   */
  fun seekTo(positionMs: Long) {
    playbackCore.seekTo(positionMs)
    _currentPosition.value = positionMs
  }

  /**
   * Toggle play/pause state.
   */
  fun togglePlayPause() {
    if (_isPlaying.value) {
      pause()
    } else {
      play()
    }
  }

  /**
   * Skip forward by the configured skip interval.
   */
  fun skipForward() {
    viewModelScope.launch {
      val settings = settingsDao.get()
      val skipMs = settings?.skipIntervalMs?.toLong() ?: 10_000L
      val newPosition = (playbackCore.getCurrentPosition() + skipMs).coerceAtLeast(0L)
      seekTo(newPosition)
    }
  }

  /**
   * Skip backward by the configured skip interval.
   */
  fun skipBackward() {
    viewModelScope.launch {
      val settings = settingsDao.get()
      val skipMs = settings?.skipIntervalMs?.toLong() ?: 10_000L
      val newPosition = (playbackCore.getCurrentPosition() - skipMs).coerceAtLeast(0L)
      seekTo(newPosition)
    }
  }

  override fun onCleared() {
    super.onCleared()

    // Save final progress before cleanup
    viewModelScope.launch {
      currentVideoId?.let { id ->
        val position = playbackCore.getCurrentPosition()
        videoRepo.updatePlaybackProgress(
            id = id,
            lastPlayedAt = System.currentTimeMillis(),
            lastPositionMs = position
        )
      }
    }

    progressUpdateJob?.cancel()
    playbackCore.release()
  }
}

