package com.inotter.travelcompanion.ui.player

import android.app.Application
import android.net.Uri
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inotter.travelcompanion.data.datasources.videolibrary.VideoLibraryDataSource
import com.inotter.travelcompanion.data.datasources.videolibrary.models.StereoLayout
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

  private val _currentStereoLayout = MutableStateFlow(StereoLayout.TwoD)
  val currentStereoLayout: StateFlow<StereoLayout> = _currentStereoLayout.asStateFlow()

  private var currentVideoId: Long? = null
  private var progressUpdateJob: Job? = null
  private var uiPositionUpdateJob: Job? = null

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
   *
   * @param uri The URI of the video file
   * @param videoId The database ID of the video
   * @param video The video item metadata
   * @param startPositionMs Optional starting position in milliseconds (for resume)
   */
  fun loadVideo(uri: Uri, videoId: Long, video: VideoItem, startPositionMs: Long = 0L) {
    currentVideoId = videoId
    viewModelScope.launch {
      // Determine stereo layout: override > detected > default
      val layout = determineStereoLayout(video)
      _currentStereoLayout.value = layout
      playbackCore.setStereoLayout(layout)

      playbackCore.prepare(uri, startPositionMs)
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
    val settings = dataSource.getPlaybackSettings()
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

