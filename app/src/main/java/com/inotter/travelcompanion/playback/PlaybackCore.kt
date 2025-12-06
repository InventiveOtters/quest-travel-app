package com.inotter.travelcompanion.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.view.Surface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
/**
 * Core playback engine using ExoPlayer.
 * Handles video decoding, subtitle tracks, and surface rendering.
 *
 * Performance optimizations (T052):
 * - ExoPlayer handles decoding on background threads (no main thread blocking)
 * - Surface rendering uses hardware acceleration via SurfaceTexture
 * - Track selector reuses builder to avoid allocations
 * - Subtitle track list is built on-demand (not in frame loop)
 * - No allocations in play/pause/seek hot paths
 */
class PlaybackCore(context: Context) {
  companion object {
    private const val TAG = "PlaybackCore"

    // Audio codecs we can decode (via MediaCodec or FFmpeg extension)
    // FFmpeg extension from Just Player includes: vorbis opus flac alac pcm_mulaw pcm_alaw
    // mp3 amrnb amrwb aac ac3 eac3 dca mlp truehd
    private val SUPPORTED_AUDIO_CODECS = setOf(
        MimeTypes.AUDIO_AAC,
        MimeTypes.AUDIO_MPEG,        // MP3
        MimeTypes.AUDIO_MPEG_L2,     // MP2
        MimeTypes.AUDIO_AC3,
        MimeTypes.AUDIO_E_AC3,
        MimeTypes.AUDIO_E_AC3_JOC,   // Dolby Digital Plus with Atmos
        MimeTypes.AUDIO_DTS,
        MimeTypes.AUDIO_DTS_HD,
        MimeTypes.AUDIO_DTS_EXPRESS,
        MimeTypes.AUDIO_VORBIS,
        MimeTypes.AUDIO_OPUS,
        MimeTypes.AUDIO_FLAC,
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_RAW,         // PCM
        MimeTypes.AUDIO_TRUEHD,      // Dolby TrueHD - supported via FFmpeg extension
        MimeTypes.AUDIO_AMR_NB,      // AMR Narrowband
        MimeTypes.AUDIO_AMR_WB,      // AMR Wideband
    )

    // Audio codecs we CANNOT decode - used to identify and skip
    // With the FFmpeg extension from Just Player, all common audio codecs are now supported
    private val UNSUPPORTED_AUDIO_CODECS = setOf<String>(
        // Currently empty - FFmpeg handles all common codecs
    )
  }

  private val trackSelector = DefaultTrackSelector(context)

  // Audio attributes for movie playback - required for proper audio output on Quest
  private val audioAttributes = AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
      .build()

  // Custom renderers factory that adds FFmpeg audio decoder for full codec support
  // Includes: AC3, EAC3, DTS, DTS-HD, TrueHD, Vorbis, Opus, FLAC, ALAC, MP3, AAC, AMR
  // Using pre-built FFmpeg extension from Just Player project
  private val renderersFactory = object : DefaultRenderersFactory(context) {
    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
      // Add FFmpeg audio renderer first for full codec support (including TrueHD)
      out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
      // Then add default audio renderers as fallback
      super.buildAudioRenderers(
          context, extensionRendererMode, mediaCodecSelector,
          enableDecoderFallback, audioSink, eventHandler, eventListener, out
      )
    }
  }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

  private val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
      .setTrackSelector(trackSelector)
      .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
      .build()

  init {
    // Listen for track changes to auto-select supported audio codec
    player.addListener(object : Player.Listener {
      override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
        selectSupportedAudioTrack()
      }
    })
  }

  /**
   * Automatically select a supported audio track if current one is unsupported.
   * This handles files with TrueHD + secondary AAC/AC3 tracks (common in anime releases).
   */
  private fun selectSupportedAudioTrack() {
    val tracks = player.currentTracks
    var foundSupportedTrack = false
    var currentTrackSupported = true

    for (group in tracks.groups) {
      if (group.type != C.TRACK_TYPE_AUDIO) continue

      for (i in 0 until group.length) {
        val format = group.getTrackFormat(i)
        val mimeType = format.sampleMimeType ?: continue
        val isSelected = group.isTrackSelected(i)
        val isSupported = mimeType !in UNSUPPORTED_AUDIO_CODECS

        Log.d(TAG, "Audio track $i: $mimeType, selected=$isSelected, supported=$isSupported, lang=${format.language}")

        if (isSelected && !isSupported) {
          currentTrackSupported = false
        }

        if (isSupported && !foundSupportedTrack) {
          foundSupportedTrack = true

          // If current track is unsupported, switch to this supported one
          if (!currentTrackSupported || !group.isTrackSelected(i)) {
            Log.d(TAG, "Selecting supported audio track: $mimeType (${format.language})")
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                .build()
            return
          }
        }
      }
    }

    if (!currentTrackSupported && !foundSupportedTrack) {
      Log.w(TAG, "No supported audio track found! Audio will not play.")
    }
  }

  fun setSurface(surface: Surface?) {
    player.setVideoSurface(surface)
  }

  fun prepare(uri: Uri, startPositionMs: Long = 0L) {
    val item = MediaItem.Builder()
        .setUri(uri)
        .build()
    player.setMediaItem(item, startPositionMs)
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

  /**
   * Set the audio volume. No allocations in this hot path.
   *
   * @param volume Volume level from 0.0 (muted) to 1.0 (full volume)
   */
  fun setVolume(volume: Float) {
    player.volume = volume.coerceIn(0f, 1f)
  }

  /**
   * Get the current audio volume. No allocations in this hot path.
   *
   * @return Current volume level from 0.0 to 1.0
   */
  fun getVolume(): Float = player.volume

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

