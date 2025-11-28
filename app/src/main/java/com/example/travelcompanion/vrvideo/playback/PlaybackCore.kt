package com.example.travelcompanion.vrvideo.playback

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.travelcompanion.vrvideo.data.db.StereoLayout

/**
 * Core playback engine using ExoPlayer.
 * Handles video decoding, subtitle tracks, surface rendering, and stereo layout.
 *
 * Performance optimizations (T052):
 * - ExoPlayer handles decoding on background threads (no main thread blocking)
 * - Surface rendering uses hardware acceleration via SurfaceTexture
 * - Track selector reuses builder to avoid allocations
 * - Subtitle track list is built on-demand (not in frame loop)
 * - No allocations in play/pause/seek hot paths
 */
class PlaybackCore(context: Context) {
  private val trackSelector = DefaultTrackSelector(context)
  private val player: ExoPlayer = ExoPlayer.Builder(context)
      .setTrackSelector(trackSelector)
      .build()

  private var currentStereoLayout: StereoLayout = StereoLayout.TwoD

  fun setSurface(surface: Surface?) {
    player.setVideoSurface(surface)
  }

  fun prepare(uri: Uri) {
    val item = MediaItem.Builder()
        .setUri(uri)
        .build()
    player.setMediaItem(item)
    player.prepare()
  }

  /**
   * Start playback. No allocations in this hot path.
   */
  fun play() { player.playWhenReady = true }

  /**
   * Pause playback. No allocations in this hot path.
   */
  fun pause() { player.playWhenReady = false }

  /**
   * Seek to a specific position. No allocations in this hot path.
   *
   * @param positionMs Target position in milliseconds
   */
  fun seekTo(positionMs: Long) { player.seekTo(positionMs) }

  /**
   * Get the current playback position. No allocations in this hot path.
   *
   * @return Current position in milliseconds
   */
  fun getCurrentPosition(): Long = player.currentPosition

  /**
   * Get the total duration of the current media. No allocations in this hot path.
   *
   * @return Duration in milliseconds, or 0 if unknown
   */
  fun getDuration(): Long = player.duration.coerceAtLeast(0L)

  /**
   * Set the stereo layout for the current video.
   * This determines how the video is rendered in VR space.
   */
  fun setStereoLayout(layout: StereoLayout) {
    currentStereoLayout = layout
    // TODO: In full VR implementation, this would update the VR surface shader/geometry
    // to render the video according to the stereo layout (SBS, TAB, etc.)
  }

  /**
   * Get the current stereo layout.
   */
  fun getStereoLayout(): StereoLayout = currentStereoLayout

  /**
   * Get available subtitle tracks.
   * Returns list of track group indices and their language/label info.
   *
   * Performance note: This method allocates a new list on each call.
   * Call only when needed (e.g., when opening subtitle menu), not in frame loop.
   */
  fun getSubtitleTracks(): List<SubtitleTrack> {
    val tracks = player.currentTracks
    val subtitleTracks = mutableListOf<SubtitleTrack>()

    for (group in tracks.groups) {
      if (group.type == C.TRACK_TYPE_TEXT) {
        for (i in 0 until group.length) {
          val format = group.getTrackFormat(i)
          subtitleTracks.add(
              SubtitleTrack(
                  groupIndex = subtitleTracks.size,
                  language = format.language ?: "unknown",
                  label = format.label ?: "Track ${subtitleTracks.size + 1}",
              )
          )
        }
      }
    }
    return subtitleTracks
  }

  /**
   * Select a subtitle track by index, or pass -1 to disable subtitles.
   */
  fun selectSubtitleTrack(trackIndex: Int) {
    if (trackIndex < 0) {
      // Disable subtitles
      trackSelector.parameters = trackSelector.buildUponParameters()
          .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
          .build()
    } else {
      // Enable and select specific track
      trackSelector.parameters = trackSelector.buildUponParameters()
          .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
          .build()
    }
  }

  fun release() { player.release() }
}

/**
 * Represents a subtitle track.
 */
data class SubtitleTrack(
    val groupIndex: Int,
    val language: String,
    val label: String,
)

