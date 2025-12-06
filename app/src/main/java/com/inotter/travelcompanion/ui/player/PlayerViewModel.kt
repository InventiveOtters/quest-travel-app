package com.inotter.travelcompanion.ui.player

import android.app.Application
import android.net.Uri
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inotter.travelcompanion.data.datasources.videolibrary.VideoLibraryDataSource
import com.inotter.travelcompanion.data.datasources.videolibrary.models.PlaybackSettings
import com.inotter.travelcompanion.data.datasources.videolibrary.models.VideoItem
import com.inotter.travelcompanion.data.repositories.VideoRepository.VideoRepository
import com.inotter.travelcompanion.playback.PlaybackCore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the player screen.
 * Binds PlaybackCore and manages playback lifecycle.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val videoRepo: VideoRepository,
    private val dataSource: VideoLibraryDataSource,
) : AndroidViewModel(application) {
  private val playbackCore = PlaybackCore(application)

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val _currentPosition = MutableStateFlow(0L)
  val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

  private val _duration = MutableStateFlow(0L)
  val duration: StateFlow<Long> = _duration.asStateFlow()

  private val _volume = MutableStateFlow(0.5f)
  val volume: StateFlow<Float> = _volume.asStateFlow()

  private var currentVideoId: Long? = null
  private var progressUpdateJob: Job? = null
  private var uiPositionUpdateJob: Job? = null

  init {
    // Load saved volume from settings
    viewModelScope.launch {
      val settings = dataSource.getPlaybackSettings()
      val savedVolume = settings?.volume ?: 0.5f
      _volume.value = savedVolume
      playbackCore.setVolume(savedVolume)
    }
  }

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
   *
   * @param uri The URI of the video file
   * @param videoId The database ID of the video
   * @param video The video item metadata
   * @param startPositionMs Optional starting position in milliseconds (for resume)
   */
  fun loadVideo(uri: Uri, videoId: Long, video: VideoItem, startPositionMs: Long = 0L) {
    currentVideoId = videoId
    viewModelScope.launch {
      playbackCore.prepare(uri, startPositionMs)
      startProgressTracking()
    }
  }

  /**
   * Start tracking playback progress and periodically save to database.
   * Also starts a faster UI update loop for smooth seek bar progress.
   */
  private fun startProgressTracking() {
    progressUpdateJob?.cancel()
    uiPositionUpdateJob?.cancel()

    // Fast UI position updates (every 250ms for smooth seek bar)
    uiPositionUpdateJob = viewModelScope.launch {
      while (isActive) {
        delay(250)
        _currentPosition.value = playbackCore.getCurrentPosition()
        // Update duration (it may not be available immediately after prepare)
        val dur = playbackCore.getDuration()
        if (dur > 0) {
          _duration.value = dur
        }
      }
    }

    // Slower database save (every 5 seconds)
    progressUpdateJob = viewModelScope.launch {
      while (isActive) {
        delay(5000)
        val position = playbackCore.getCurrentPosition()

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
      val settings = dataSource.getPlaybackSettings()
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
      val settings = dataSource.getPlaybackSettings()
      val skipMs = settings?.skipIntervalMs?.toLong() ?: 10_000L
      val newPosition = (playbackCore.getCurrentPosition() - skipMs).coerceAtLeast(0L)
      seekTo(newPosition)
    }
  }

  /**
   * Set the audio volume and persist to settings.
   * Volume is shared across all movies.
   *
   * @param volume Volume level from 0.0 (muted) to 1.0 (full volume)
   */
  fun setVolume(volume: Float) {
    val clampedVolume = volume.coerceIn(0f, 1f)
    playbackCore.setVolume(clampedVolume)
    _volume.value = clampedVolume

    // Persist volume to settings
    viewModelScope.launch {
      val current = dataSource.getPlaybackSettings() ?: PlaybackSettings()
      dataSource.upsertPlaybackSettings(current.copy(volume = clampedVolume))
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
    uiPositionUpdateJob?.cancel()
    playbackCore.release()
  }
}

