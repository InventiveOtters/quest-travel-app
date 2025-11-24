package com.example.travelcompanion.vrvideo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.travelcompanion.vrvideo.data.db.LibraryFolder
import com.example.travelcompanion.vrvideo.ui.viewmodel.LibraryViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for managing library folder sources.
 * Allows users to add/remove folders and manually trigger rescans.
 */
@Composable
fun ManageSourcesScreen(
    viewModel: LibraryViewModel,
    onAddFolder: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val folders by viewModel.folders.collectAsState()

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
            text = "Manage Library Sources",
            style = MaterialTheme.typography.headlineMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = onAddFolder) { Text("Add Folder") }
          OutlinedButton(onClick = onBack) { Text("Back") }
        }
      }

      HorizontalDivider()

      // Folder list
      if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No library folders configured",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Button(onClick = onAddFolder) { Text("Add Your First Folder") }
          }
        }
      } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(folders) { folder ->
            FolderCard(
                folder = folder,
                onRemove = { viewModel.removeFolder(folder.id) },
                onRescan = { viewModel.rescanFolder(folder.id, folder.treeUri) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FolderCard(
    folder: LibraryFolder,
    onRemove: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Folder name
      Text(
          text = folder.displayName,
          style = MaterialTheme.typography.titleMedium,
      )

      // Folder details
      Text(
          text = "Added: ${formatTimestamp(folder.addedAt)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (folder.lastScanTime != null) {
        Text(
            text = "Last scanned: ${formatTimestamp(folder.lastScanTime)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
          text = if (folder.includeSubfolders) "Includes subfolders" else "Root folder only",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Actions
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(onClick = onRescan) { Text("Rescan") }
        OutlinedButton(
            onClick = onRemove,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
          Text("Remove")
        }
      }
    }
  }
}

private fun formatTimestamp(timestamp: Long): String {
  val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
  return sdf.format(Date(timestamp))
}

