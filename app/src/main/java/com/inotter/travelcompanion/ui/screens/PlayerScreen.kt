package com.inotter.travelcompanion.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.inotter.travelcompanion.data.db.StereoLayout
import com.inotter.travelcompanion.data.db.VideoItem
import com.inotter.travelcompanion.ui.viewmodel.PlayerViewModel
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
    modifier: Modifier = Modifier,
) {
  val isPlaying by viewModel.isPlaying.collectAsState()
  val currentPosition by viewModel.currentPosition.collectAsState()
  val duration by viewModel.duration.collectAsState()
  val currentStereoLayout by viewModel.currentStereoLayout.collectAsState()

  var showResumeDialog by remember { mutableStateOf(false) }
  var showLayoutDialog by remember { mutableStateOf(false) }
  var hasAskedResume by remember { mutableStateOf(false) }
  var showControls by remember { mutableStateOf(true) }

  // Auto-hide controls after 3 seconds of inactivity when playing
  LaunchedEffect(showControls, isPlaying) {
    if (showControls && isPlaying) {
      delay(3000)
      showControls = false
    }
  }

  // Check if we should show resume dialog
  LaunchedEffect(video.id) {
    if (!hasAskedResume && video.lastPositionMs != null && video.lastPositionMs > 0) {
      showResumeDialog = true
      hasAskedResume = true
    } else {
      viewModel.loadVideo(Uri.parse(video.fileUri), video.id, video)
    }
  }

  // Resume dialog
  if (showResumeDialog) {
    AlertDialog(
        onDismissRequest = {
          showResumeDialog = false
          viewModel.loadVideo(Uri.parse(video.fileUri), video.id, video)
        },
        title = { Text("Resume Playback") },
        text = {
          Text("Resume from ${formatTime(video.lastPositionMs ?: 0L)}?")
        },
        confirmButton = {
          Button(
              onClick = {
                showResumeDialog = false
                viewModel.loadVideo(Uri.parse(video.fileUri), video.id, video)
                video.lastPositionMs?.let { viewModel.seekTo(it) }
              }
          ) {
            Text("Resume")
          }
        },
        dismissButton = {
          TextButton(
              onClick = {
                showResumeDialog = false
                viewModel.loadVideo(Uri.parse(video.fileUri), video.id, video)
              }
          ) {
            Text("Start from beginning")
          }
        }
    )
  }

  // Stereo layout selection dialog
  if (showLayoutDialog) {
    AlertDialog(
        onDismissRequest = { showLayoutDialog = false },
        title = { Text("Stereo Layout") },
        text = {
          Column {
            Text(
                text = "Select viewing mode for this video",
                modifier = Modifier.padding(bottom = 16.dp)
            )
            val layouts = listOf(
                StereoLayout.TwoD to "2D (Flat)",
                StereoLayout.SBS to "Side-by-Side",
                StereoLayout.TAB to "Top-and-Bottom",
                StereoLayout.HSBS to "Half SBS",
                StereoLayout.HTAB to "Half TAB"
            )
            layouts.forEach { (layout, label) ->
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(
                    selected = currentStereoLayout == layout,
                    onClick = {
                      viewModel.setStereoLayoutOverride(layout)
                      showLayoutDialog = false
                    }
                )
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 8.dp)
                )
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showLayoutDialog = false }) {
            Text("Close")
          }
        }
    )
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

          // Control buttons
          Row(
              modifier = Modifier.padding(top = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            // Back button - using IconButton for consistent styling with other controls
            IconButton(onClick = handleBack) {
              Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Back",
                  tint = Color.White
              )
            }

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

            // Stereo layout button
            IconButton(
                onClick = { showLayoutDialog = true }
            ) {
              Icon(
                  imageVector = Icons.Default.Settings,
                  contentDescription = "Stereo Layout",
                  tint = Color.White
              )
            }
            Text(
                text = currentStereoLayout.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
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

