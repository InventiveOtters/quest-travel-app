package com.inotter.travelcompanion.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inotter.travelcompanion.data.datasources.videolibrary.models.StereoLayout
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionStatus

/**
 * Settings screen for playback preferences and permission management.
 * Allows configuration of defaultViewMode, skipInterval, resumeEnabled, and shows permission status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val settings by viewModel.settings.collectAsState()
  val permissionStatus by viewModel.permissionStatus.collectAsState()
  val context = LocalContext.current

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
        // Default View Mode
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Default View Mode",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Default stereo layout for videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            val viewModes = listOf(StereoLayout.TwoD, StereoLayout.SBS, StereoLayout.TAB)
            viewModes.forEach { mode ->
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(
                    selected = currentSettings.defaultViewMode == mode,
                    onClick = { viewModel.updateDefaultViewMode(mode) }
                )
                Text(
                    text = mode.name,
                    modifier = Modifier.padding(start = 8.dp)
                )
              }
            }
          }
        }

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

