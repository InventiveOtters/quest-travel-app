package com.inotter.travelcompanion.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inotter.travelcompanion.data.datasources.videolibrary.models.LibraryFolder
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionManagerImpl
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for managing library folder sources.
 * Allows users to toggle auto-scan, add/remove folders, and manually trigger rescans.
 */
@Composable
fun ManageSourcesScreen(
    viewModel: LibraryViewModel,
    onAddFolder: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val folders by viewModel.folders.collectAsState()
  val scanSettings by viewModel.scanSettings.collectAsState()
  val permissionStatus by viewModel.permissionStatus.collectAsState()

  // Permission launcher for enabling auto-scan
  val permissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val anyGranted = permissions.values.any { it }
    if (anyGranted) {
      viewModel.refreshPermissionStatus()
      viewModel.setAutoScanEnabled(true)
    }
  }

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

      LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Auto-scan section
        item {
          AutoScanCard(
              isEnabled = scanSettings?.autoScanEnabled == true,
              permissionStatus = permissionStatus,
              lastScanTime = scanSettings?.lastMediaStoreScan ?: 0L,
              onToggle = { enabled ->
                if (enabled && permissionStatus == PermissionStatus.DENIED) {
                  permissionLauncher.launch(PermissionManagerImpl(context).getPermissionsToRequest())
                } else {
                  viewModel.setAutoScanEnabled(enabled)
                }
              },
              onRefresh = { viewModel.triggerMediaStoreScan() },
          )
        }

        // Section header for SAF folders
        if (folders.isNotEmpty()) {
          item {
            Text(
                text = "Manual Folders",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
          }
        }

        // Folder list
        items(folders) { folder ->
          FolderCard(
              folder = folder,
              onRemove = { viewModel.removeFolder(folder.id) },
              onRescan = { viewModel.rescanFolder(folder.id, folder.treeUri) },
          )
        }

        // Empty state for folders only
        if (folders.isEmpty()) {
          item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
              Column(
                  modifier = Modifier.fillMaxWidth().padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                    text = "No manual folders configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onAddFolder) {
                  Text("Add Folder")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AutoScanCard(
    isEnabled: Boolean,
    permissionStatus: PermissionStatus,
    lastScanTime: Long,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
          containerColor = if (isEnabled)
              MaterialTheme.colorScheme.primaryContainer
          else
              MaterialTheme.colorScheme.surfaceVariant,
      ),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = "Auto-Scan Device Videos",
              style = MaterialTheme.typography.titleMedium,
          )
          Text(
              text = when (permissionStatus) {
                PermissionStatus.GRANTED -> "Full access to all videos"
                PermissionStatus.PARTIAL -> "Access to selected videos only"
                PermissionStatus.DENIED -> "Permission required"
              },
              style = MaterialTheme.typography.bodySmall,
              color = if (isEnabled)
                  MaterialTheme.colorScheme.onPrimaryContainer
              else
                  MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
        )
      }

      if (isEnabled) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              text = if (lastScanTime > 0)
                  "Last scan: ${formatTimestamp(lastScanTime)}"
              else
                  "Not scanned yet",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          TextButton(onClick = onRefresh) {
            Text("Refresh Now")
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

