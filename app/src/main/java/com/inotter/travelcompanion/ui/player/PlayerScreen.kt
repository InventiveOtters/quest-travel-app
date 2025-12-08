package com.inotter.travelcompanion.ui.player

import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.inotter.travelcompanion.data.datasources.videolibrary.models.VideoItem
import kotlinx.coroutines.delay

/**
 * Player screen with VR surface and playback controls.
 * Displays video in immersive VR environment with play/pause/seek controls.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    video: VideoItem,
    onBack: () -> Unit,
    onHostSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val isPlaying by viewModel.isPlaying.collectAsState()
  val currentPosition by viewModel.currentPosition.collectAsState()
  val duration by viewModel.duration.collectAsState()
  val volume by viewModel.volume.collectAsState()

  var showControls by remember { mutableStateOf(true) }
  var showVolumeSlider by remember { mutableStateOf(false) }
  var volumeInteractionKey by remember { mutableIntStateOf(0) }

  // Auto-hide controls after 3 seconds of inactivity when playing
  LaunchedEffect(showControls, isPlaying, showVolumeSlider) {
    if (showControls && isPlaying && !showVolumeSlider) {
      delay(3000)
      showControls = false
    }
  }

  // Auto-hide volume slider after 2 seconds of inactivity
  // Resets when volumeInteractionKey changes (user interacts with slider)
  LaunchedEffect(showVolumeSlider, volumeInteractionKey) {
    if (showVolumeSlider) {
      delay(2000)
      showVolumeSlider = false
    }
  }

  // Load video and automatically resume from last position if available
  LaunchedEffect(video.id) {
    val resumePosition = video.lastPositionMs ?: 0L
    viewModel.loadVideo(Uri.parse(video.fileUri), video.id, video, resumePosition)
  }

  // Handler for back button - stops playback before navigating back
  val handleBack: () -> Unit = {
    viewModel.pause()
    onBack()
  }

  Surface(
      modifier = modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
  ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
              // Tap anywhere to toggle play/pause and show controls
              viewModel.togglePlayPause()
              showControls = true
            }
    ) {
      // Video surface using AndroidView with SurfaceView
      // ExoPlayer renders video frames to this surface
      AndroidView(
          factory = { context ->
            SurfaceView(context).apply {
              holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                  viewModel.setSurface(holder.surface)
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                  // Surface dimensions changed, ExoPlayer handles this automatically
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                  viewModel.setSurface(null)
                }
              })
            }
          },
          modifier = Modifier
              .fillMaxSize()
              .background(Color.Black),
      )

      // Back button at top left corner
      AnimatedVisibility(
          visible = showControls,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier.align(Alignment.TopStart)
      ) {
        IconButton(
            onClick = handleBack,
            modifier = Modifier.padding(16.dp)
        ) {
          Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = Color.White
          )
        }
      }

      // Playback controls overlay with fade animation
      AnimatedVisibility(
          visible = showControls,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier.align(Alignment.BottomCenter)
      ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // Video title - white text for readability on dark background
          Text(
              text = video.title,
              style = MaterialTheme.typography.titleLarge,
              color = Color.White,
              modifier = Modifier.padding(bottom = 16.dp),
          )

          // Seek bar / progress bar (uses duration from ExoPlayer, not video metadata)
          if (duration > 0) {
            // Track whether user is currently dragging the slider
            var isSeeking by remember { mutableStateOf(false) }
            var seekPosition by remember { mutableFloatStateOf(0f) }

            // Use seek position while dragging, otherwise use actual playback position
            val displayPosition = if (isSeeking) seekPosition else currentPosition.toFloat()

            Slider(
                value = displayPosition,
                onValueChange = { newValue ->
                  isSeeking = true
                  seekPosition = newValue
                  showControls = true // Keep controls visible while seeking
                },
                onValueChangeFinished = {
                  viewModel.seekTo(seekPosition.toLong())
                  isSeeking = false
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                  text = formatTime(if (isSeeking) seekPosition.toLong() else currentPosition),
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White,
              )
              Text(
                  text = formatTime(duration),
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White,
              )
            }
          }

          // Control buttons - play/pause centered with volume on the right
          Row(
              modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            // Left spacer to balance the volume button on the right
            Spacer(modifier = Modifier.weight(1f))

            // Center controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              // Skip backward
              IconButton(onClick = { viewModel.skipBackward() }) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Skip Backward",
                    tint = Color.White
                )
              }

              // Play/Pause
              IconButton(
                  onClick = { viewModel.togglePlayPause() },
                  modifier = Modifier.size(64.dp),
              ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
              }

              // Skip forward
              IconButton(onClick = { viewModel.skipForward() }) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Skip Forward",
                    tint = Color.White
                )
              }
	            	      // Host multi-device sync session
	            	      IconButton(onClick = { onHostSession() }) {
	            	        Icon(
	            	            imageVector = Icons.Default.Sync,
	            	            contentDescription = "Host Sync Session",
	            	            tint = Color.White
	            	        )
	            	      }
            }

            // Right side with volume button
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
            ) {
              Spacer(modifier = Modifier.width(16.dp))

              // Volume button with popup overlay
              Box {
                IconButton(
                    onClick = {
                      showVolumeSlider = !showVolumeSlider
                      showControls = true
                    }
                ) {
                  Icon(
                      imageVector = if (volume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                      contentDescription = "Volume",
                      tint = Color.White
                  )
                }

                // Volume slider popup - renders in overlay layer, doesn't affect layout
                if (showVolumeSlider) {
                  Popup(
                      alignment = Alignment.BottomCenter,
                      offset = IntOffset(0, -48),
                      onDismissRequest = { showVolumeSlider = false },
                      properties = PopupProperties(focusable = false)
                  ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                    ) {
                      Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                      ) {
                        // Vertical slider using rotated horizontal slider
                        Slider(
                            value = volume,
                            onValueChange = { newVolume ->
                              viewModel.setVolume(newVolume)
                              volumeInteractionKey++ // Reset auto-hide timer
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .graphicsLayer {
                                  rotationZ = -90f
                                  transformOrigin = TransformOrigin(0.5f, 0.5f)
                                }
                                .layout { measurable, constraints ->
                                  val placeable = measurable.measure(
                                      Constraints(
                                          minWidth = 120.dp.roundToPx(),
                                          maxWidth = 120.dp.roundToPx(),
                                          minHeight = constraints.minHeight,
                                          maxHeight = constraints.maxHeight
                                      )
                                  )
                                  layout(placeable.height, placeable.width) {
                                    placeable.place(
                                        x = -(placeable.width - placeable.height) / 2,
                                        y = (placeable.width - placeable.height) / 2
                                    )
                                  }
                                },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun formatTime(ms: Long): String {
  val seconds = ms / 1000
  val minutes = seconds / 60
  val hours = minutes / 60
  return if (hours > 0) {
    "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
  } else {
    "%d:%02d".format(minutes, seconds % 60)
  }
}

