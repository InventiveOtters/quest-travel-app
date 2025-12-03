package com.inotter.travelcompanion.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inotter.travelcompanion.data.db.SourceType
import com.inotter.travelcompanion.data.db.VideoItem
import com.inotter.travelcompanion.ui.viewmodel.LibraryViewModel
import java.io.File

/**
 * Library screen displaying a grid of videos with thumbnails.
 * Supports basic title sorting and video selection.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onVideoSelected: (VideoItem) -> Unit,
    onAddFolder: () -> Unit,
    onManageSources: () -> Unit = {},
    onSettings: () -> Unit = {},
    onWifiTransfer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
  val videos by viewModel.videos.collectAsState()

  Surface(
      modifier = modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Header
      Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = "Travel Companion",
            style = MaterialTheme.typography.headlineMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onSettings) { Text("Settings") }
          OutlinedButton(onClick = onManageSources) { Text("Manage Sources") }
          OutlinedButton(onClick = onWifiTransfer) { Text("📶 WiFi Transfer") }
          Button(onClick = onAddFolder) { Text("Add Folder") }
        }
      }

      HorizontalDivider()

      // Video grid
      if (videos.isEmpty()) {
        EmptyLibraryContent(
            onAddFolder = onAddFolder,
            onManageSources = onManageSources,
        )
      } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          items(videos.sortedBy { it.title }) { video -> VideoCard(video = video, onClick = { onVideoSelected(video) }) }
        }
      }
    }
  }
}

@Composable
private fun EmptyLibraryContent(
    onAddFolder: () -> Unit,
    onManageSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp).widthIn(max = 400.dp),
    ) {
      Text(
          text = "🎬",
          style = MaterialTheme.typography.displayLarge,
      )

      Text(
          text = "No Videos Found",
          style = MaterialTheme.typography.headlineSmall,
      )

      Text(
          text = "Add videos to your library by enabling auto-scan or selecting folders manually.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp),
      )

      Button(
          onClick = onManageSources,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Enable Auto-Scan")
      }

      OutlinedButton(
          onClick = onAddFolder,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Add Folder Manually")
      }
    }
  }
}

@Composable
private fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth().height(180.dp).clickable(onClick = onClick),
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // Thumbnail
      Box(
          modifier = Modifier.fillMaxWidth().weight(1f),
          contentAlignment = Alignment.Center,
      ) {
        if (video.thumbnailPath != null && File(video.thumbnailPath).exists()) {
          val bitmap = BitmapFactory.decodeFile(video.thumbnailPath)
          if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Video thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
          } else {
            Text(
                text = "📹",
                style = MaterialTheme.typography.displayMedium,
            )
          }
        } else {
          Text(
              text = "📹",
              style = MaterialTheme.typography.displayMedium,
          )
        }

        // Show status badges
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          // Source type badge
          if (video.sourceType == SourceType.MEDIASTORE) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
              Text(
                  text = "Auto",
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }

          // Unavailable badge
          if (video.unavailable) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                shape = MaterialTheme.shapes.small,
            ) {
              Text(
                  text = "Unavailable",
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onError,
              )
            }
          }
        }
      }

      // Video info
      Column {
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = formatDuration(video.durationMs),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

private fun formatDuration(ms: Long): String {
  val seconds = ms / 1000
  val minutes = seconds / 60
  val hours = minutes / 60
  return if (hours > 0) {
    "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
  } else {
    "%d:%02d".format(minutes, seconds % 60)
  }
}

