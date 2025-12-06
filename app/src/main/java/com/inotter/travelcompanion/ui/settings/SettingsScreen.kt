package com.inotter.travelcompanion.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionStatus
import com.inotter.travelcompanion.data.models.ViewingMode

/**
 * Settings screen for playback preferences, permission management, and viewing mode.
 * Allows configuration of skipInterval, resumeEnabled, viewing mode, and shows permission status.
 *
 * @param onSwitchToImmersive Callback to switch to immersive mode
 * @param onSwitchToPanel Callback to switch to panel mode (not typically needed from 2D settings)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onSwitchToImmersive: () -> Unit = {},
    onSwitchToPanel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
  val settings by viewModel.settings.collectAsState()
  val permissionStatus by viewModel.permissionStatus.collectAsState()
  val viewingMode by viewModel.viewingMode.collectAsState()
  val context = LocalContext.current

  // State for confirmation dialog
  var showModeConfirmDialog by remember { mutableStateOf(false) }
  var pendingMode by remember { mutableStateOf<ViewingMode?>(null) }

  // Mode switch confirmation dialog
  if (showModeConfirmDialog && pendingMode != null) {
    AlertDialog(
        onDismissRequest = {
          showModeConfirmDialog = false
          pendingMode = null
        },
        title = { Text("Switch Viewing Mode?") },
        text = {
          Text(
              when (pendingMode) {
                ViewingMode.IMMERSIVE -> "Switch to Immersive VR Mode? The app will restart in the VR theatre."
                ViewingMode.PANEL_2D -> "Switch to 2D Panel Mode? The app will restart in panel mode."
                else -> ""
              }
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                pendingMode?.let { mode ->
                  viewModel.updateViewingMode(mode)
                  when (mode) {
                    ViewingMode.IMMERSIVE -> onSwitchToImmersive()
                    ViewingMode.PANEL_2D -> onSwitchToPanel()
                  }
                }
                showModeConfirmDialog = false
                pendingMode = null
              }
          ) {
            Text("Switch")
          }
        },
        dismissButton = {
          TextButton(
              onClick = {
                showModeConfirmDialog = false
                pendingMode = null
              }
          ) {
            Text("Cancel")
          }
        }
    )
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
              }
            }
        )
      },
      modifier = modifier
  ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
      // Viewing Mode Section
      Text(
          text = "Viewing Mode",
          style = MaterialTheme.typography.titleLarge,
      )

      ViewingModeCard(
          currentMode = viewingMode,
          onModeSelected = { mode ->
            if (mode != viewingMode) {
              pendingMode = mode
              showModeConfirmDialog = true
            }
          }
      )

      // Permissions Section
      Text(
          text = "Permissions",
          style = MaterialTheme.typography.titleLarge,
      )

      PermissionCard(
          permissionStatus = permissionStatus,
          onManagePermissions = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
              data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
          }
      )

      // Playback Settings Section
      Text(
          text = "Playback",
          style = MaterialTheme.typography.titleLarge,
      )

      settings?.let { currentSettings ->
        // Skip Interval
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Skip Interval",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Seconds to skip forward/backward",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
              val skipIntervals = listOf(5, 10, 15, 30)
              skipIntervals.forEach { seconds ->
                FilterChip(
                    selected = currentSettings.skipIntervalMs == seconds * 1000,
                    onClick = { viewModel.updateSkipInterval(seconds * 1000) },
                    label = { Text("${seconds}s") }
                )
              }
            }
          }
        }

        // Resume Enabled
        Card(modifier = Modifier.fillMaxWidth()) {
          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                  text = "Resume Playback",
                  style = MaterialTheme.typography.titleMedium
              )
              Text(
                  text = "Ask to resume from last position",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(top = 4.dp)
              )
            }
            Switch(
                checked = currentSettings.resumeEnabled,
                onCheckedChange = { viewModel.updateResumeEnabled(it) }
            )
          }
        }
      } ?: run {
        // Loading state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }
      }
    }
  }
}

@Composable
private fun PermissionCard(
    permissionStatus: PermissionStatus,
    onManagePermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
          containerColor = when (permissionStatus) {
            PermissionStatus.GRANTED -> MaterialTheme.colorScheme.primaryContainer
            PermissionStatus.PARTIAL -> MaterialTheme.colorScheme.tertiaryContainer
            PermissionStatus.DENIED -> MaterialTheme.colorScheme.errorContainer
          }
      )
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
              text = "Video Access Permission",
              style = MaterialTheme.typography.titleMedium,
          )
          Text(
              text = when (permissionStatus) {
                PermissionStatus.GRANTED -> "✅ Full access to all videos on device"
                PermissionStatus.PARTIAL -> "⚠️ Access to selected videos only"
                PermissionStatus.DENIED -> "❌ Not granted - auto-scan disabled"
              },
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(top = 4.dp),
          )
        }
      }

      if (permissionStatus != PermissionStatus.GRANTED) {
        Text(
            text = when (permissionStatus) {
              PermissionStatus.PARTIAL -> "For the best experience, grant full video access in system settings."
              PermissionStatus.DENIED -> "Grant video permission to enable automatic library scanning."
              else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      OutlinedButton(
          onClick = onManagePermissions,
          modifier = Modifier.align(Alignment.End),
      ) {
        Text("Manage in Settings")
      }
    }
  }
}

/**
 * Card for selecting viewing mode preference.
 */
@Composable
private fun ViewingModeCard(
    currentMode: ViewingMode,
    onModeSelected: (ViewingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
  Card(modifier = modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
          text = "Choose how you want to watch videos",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Column(
          modifier = Modifier.selectableGroup(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        ViewingModeOption(
            emoji = "📱",
            title = "2D Panel Mode",
            description = "Watch in a floating panel. Great for multitasking.",
            isSelected = currentMode == ViewingMode.PANEL_2D,
            onClick = { onModeSelected(ViewingMode.PANEL_2D) }
        )

        ViewingModeOption(
            emoji = "🥽",
            title = "Immersive VR Mode",
            description = "Full immersive experience. Feel like you're there.",
            isSelected = currentMode == ViewingMode.IMMERSIVE,
            onClick = { onModeSelected(ViewingMode.IMMERSIVE) }
        )
      }
    }
  }
}

/**
 * A single viewing mode option row.
 */
@Composable
private fun ViewingModeOption(
    emoji: String,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
  Surface(
      modifier = Modifier
          .fillMaxWidth()
          .selectable(
              selected = isSelected,
              onClick = onClick,
              role = Role.RadioButton
          ),
      shape = MaterialTheme.shapes.medium,
      color = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      }
  ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
          text = emoji,
          style = MaterialTheme.typography.titleLarge,
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isSelected) {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) {
              MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            }
        )
      }

      RadioButton(
          selected = isSelected,
          onClick = null,
      )
    }
  }
}

