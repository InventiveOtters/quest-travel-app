package com.example.travelcompanion.vrvideo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.travelcompanion.vrvideo.data.db.PlaybackSettings
import com.example.travelcompanion.vrvideo.data.db.StereoLayout
import com.example.travelcompanion.vrvideo.ui.viewmodel.SettingsViewModel

/**
 * Settings screen for playback preferences.
 * Allows configuration of defaultViewMode, skipInterval, and resumeEnabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val settings by viewModel.settings.collectAsState()

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Playback Settings") },
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
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

