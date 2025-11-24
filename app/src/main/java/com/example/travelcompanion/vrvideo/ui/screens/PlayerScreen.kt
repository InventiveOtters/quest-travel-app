package com.example.travelcompanion.vrvideo.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.travelcompanion.vrvideo.data.db.StereoLayout
import com.example.travelcompanion.vrvideo.data.db.VideoItem
import com.example.travelcompanion.vrvideo.ui.viewmodel.PlayerViewModel

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
  val currentStereoLayout by viewModel.currentStereoLayout.collectAsState()

  var showResumeDialog by remember { mutableStateOf(false) }
  var showLayoutDialog by remember { mutableStateOf(false) }
  var hasAskedResume by remember { mutableStateOf(false) }

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

  Surface(
      modifier = modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // VR surface placeholder
      // TODO: In a full VR implementation, this would use SurfaceTexture:
      // 1. Create SurfaceTexture in VR scene (via Spatial SDK)
      // 2. Get Surface from SurfaceTexture
      // 3. Call viewModel.setSurface(surface)
      // 4. Render texture to VR quad in immersive space
      // For now, this is a 2D panel placeholder
      Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = "VR Video Surface\n(SurfaceTexture integration pending)",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Playback controls overlay
      Column(
          modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Video title
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Progress bar
        if (video.durationMs > 0) {
          Slider(
              value = currentPosition.toFloat(),
              onValueChange = { viewModel.seekTo(it.toLong()) },
              valueRange = 0f..video.durationMs.toFloat(),
              modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
          )

          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = formatTime(video.durationMs),
                style = MaterialTheme.typography.bodySmall,
            )
          }
        }

        // Control buttons
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(onClick = onBack) { Text("Back") }

          // Skip backward
          IconButton(onClick = { viewModel.skipBackward() }) {
            Icon(
                imageVector = Icons.Default.FastRewind,
                contentDescription = "Skip Backward"
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
            )
          }

          // Skip forward
          IconButton(onClick = { viewModel.skipForward() }) {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = "Skip Forward"
            )
          }

          // Stereo layout button
          IconButton(
              onClick = { showLayoutDialog = true }
          ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Stereo Layout"
            )
          }
          Text(
              text = currentStereoLayout.name,
              style = MaterialTheme.typography.bodySmall
          )
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

