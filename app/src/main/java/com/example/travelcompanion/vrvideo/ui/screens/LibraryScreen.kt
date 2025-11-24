package com.example.travelcompanion.vrvideo.ui.screens

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
import com.example.travelcompanion.vrvideo.data.db.VideoItem
import com.example.travelcompanion.vrvideo.ui.viewmodel.LibraryViewModel
import android.graphics.BitmapFactory
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
            text = "VR Video Library",
            style = MaterialTheme.typography.headlineMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onSettings) { Text("Settings") }
          OutlinedButton(onClick = onManageSources) { Text("Manage Sources") }
          Button(onClick = onAddFolder) { Text("Add Folder") }
        }
      }

      HorizontalDivider()

      // Video grid
      if (videos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No videos found",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Button(onClick = onAddFolder) { Text("Add Your First Folder") }
          }
        }
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

        // Show unavailable badge
        if (video.unavailable) {
          Surface(
              modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
              color = MaterialTheme.colorScheme.error,
              shape = MaterialTheme.shapes.small,
          ) {
            Text(
                text = "Unavailable",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError,
            )
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

        Text(
            text = formatDuration(video.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

