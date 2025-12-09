package com.inotter.onthegovr.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inotter.onthegovr.data.datasources.videolibrary.models.LibraryFolder
import com.inotter.onthegovr.data.managers.PermissionManager.PermissionManagerImpl
import com.inotter.onthegovr.data.managers.PermissionManager.PermissionStatus
import com.inotter.onthegovr.ui.theme.QuestDimensions
import com.inotter.onthegovr.ui.theme.QuestDivider
import com.inotter.onthegovr.ui.theme.QuestPrimaryButton
import com.inotter.onthegovr.ui.theme.QuestSecondaryButton
import com.inotter.onthegovr.ui.theme.QuestTextButton
import com.inotter.onthegovr.ui.theme.QuestThemeExtras
import com.inotter.onthegovr.ui.theme.QuestTypography
import com.meta.spatial.uiset.theme.LocalColorScheme
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
      modifier = modifier
          .fillMaxSize()
          .background(brush = LocalColorScheme.current.panel),
      color = androidx.compose.ui.graphics.Color.Transparent,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Quest-styled header
      Row(
          modifier = Modifier
              .fillMaxWidth()
              .padding(QuestDimensions.ContentPadding.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = "Manage Library Sources",
            style = QuestTypography.headlineMedium,
            color = QuestThemeExtras.colors.primaryText,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          QuestPrimaryButton(text = "Add Folder", onClick = onAddFolder)
          QuestSecondaryButton(text = "Back", onClick = onBack)
        }
      }

      QuestDivider()

      LazyColumn(
          contentPadding = PaddingValues(QuestDimensions.ContentPadding.dp),
          verticalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp),
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
                style = QuestTypography.titleMedium,
                color = QuestThemeExtras.colors.primaryText,
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = QuestThemeExtras.colors.secondary,
                shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
            ) {
              Column(
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(QuestDimensions.SectionSpacing.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                    text = "No manual folders configured",
                    style = QuestTypography.bodyMedium,
                    color = QuestThemeExtras.colors.secondaryText,
                )
                Spacer(modifier = Modifier.height(12.dp))
                QuestSecondaryButton(
                    text = "Add Folder",
                    onClick = onAddFolder,
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Quest-styled auto-scan card with proper hit targets.
 */
@Composable
private fun AutoScanCard(
    isEnabled: Boolean,
    permissionStatus: PermissionStatus,
    lastScanTime: Long,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val backgroundColor = if (isEnabled) {
    LocalColorScheme.current.primaryButton.copy(alpha = 0.15f)
  } else {
    QuestThemeExtras.colors.secondary
  }

  Surface(
      modifier = modifier.fillMaxWidth(),
      color = backgroundColor,
      shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
  ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(QuestDimensions.ContentPadding.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = "Auto-Scan Device Videos",
              style = QuestTypography.titleMedium,
              color = QuestThemeExtras.colors.primaryText,
          )
          Text(
              text = when (permissionStatus) {
                PermissionStatus.GRANTED -> "Full access to all videos"
                PermissionStatus.PARTIAL -> "Access to selected videos only"
                PermissionStatus.DENIED -> "Permission required"
              },
              style = QuestTypography.bodySmall,
              color = QuestThemeExtras.colors.secondaryText,
          )
        }
        // Switch with larger touch target
        Box(
            modifier = Modifier
                .heightIn(min = QuestDimensions.MinHitTarget.dp)
                .padding(start = 16.dp),
            contentAlignment = Alignment.Center
        ) {
          Switch(
              checked = isEnabled,
              onCheckedChange = onToggle,
          )
        }
      }

      if (isEnabled) {
        QuestDivider()

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
              style = QuestTypography.bodySmall,
              color = QuestThemeExtras.colors.secondaryText,
          )
          QuestTextButton(
              text = "Refresh Now",
              onClick = onRefresh,
          )
        }
      }
    }
  }
}

/**
 * Quest-styled folder card with hover support.
 */
@Composable
private fun FolderCard(
    folder: LibraryFolder,
    onRemove: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier = modifier.fillMaxWidth(),
      color = QuestThemeExtras.colors.secondary,
      shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
  ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(QuestDimensions.ContentPadding.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Folder icon and name
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LocalColorScheme.current.primaryButton.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
          Text(text = "📁", style = QuestTypography.titleMedium)
        }
        Text(
            text = folder.displayName,
            style = QuestTypography.titleMedium,
            color = QuestThemeExtras.colors.primaryText,
        )
      }

      // Folder details
      Text(
          text = "Added: ${formatTimestamp(folder.addedAt)}",
          style = QuestTypography.bodySmall,
          color = QuestThemeExtras.colors.secondaryText,
      )

      if (folder.lastScanTime != null) {
        Text(
            text = "Last scanned: ${formatTimestamp(folder.lastScanTime)}",
            style = QuestTypography.bodySmall,
            color = QuestThemeExtras.colors.secondaryText,
        )
      }

      Text(
          text = if (folder.includeSubfolders) "Includes subfolders" else "Root folder only",
          style = QuestTypography.bodySmall,
          color = QuestThemeExtras.colors.secondaryText,
      )

      Spacer(modifier = Modifier.height(4.dp))

      // Actions with proper hit targets
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        QuestSecondaryButton(text = "Rescan", onClick = onRescan)
        QuestSecondaryButton(text = "Remove", onClick = onRemove)
      }
    }
  }
}

private fun formatTimestamp(timestamp: Long): String {
  val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
  return sdf.format(Date(timestamp))
}

