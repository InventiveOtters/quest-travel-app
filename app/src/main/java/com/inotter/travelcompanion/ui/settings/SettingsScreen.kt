package com.inotter.travelcompanion.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionStatus
import com.inotter.travelcompanion.data.models.ViewingMode
import com.inotter.travelcompanion.ui.theme.QuestColors
import com.inotter.travelcompanion.ui.theme.QuestDimensions
import com.inotter.travelcompanion.ui.theme.QuestDivider
import com.inotter.travelcompanion.ui.theme.QuestIconButton
import com.inotter.travelcompanion.ui.theme.QuestSecondaryButton
import com.inotter.travelcompanion.ui.theme.QuestSelectableCard
import com.inotter.travelcompanion.ui.theme.QuestThemeExtras
import com.inotter.travelcompanion.ui.theme.QuestTypography
import com.meta.spatial.uiset.theme.LocalColorScheme

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

  Surface(
      modifier = modifier
          .fillMaxSize()
          .background(brush = LocalColorScheme.current.panel),
      color = androidx.compose.ui.graphics.Color.Transparent,
  ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(QuestDimensions.ContentPadding.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(QuestDimensions.SectionSpacing.dp)
    ) {
      // Quest-styled header
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          QuestIconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = QuestThemeExtras.colors.primaryText,
                modifier = Modifier.size(24.dp)
            )
          }
          Text(
              text = "Settings",
              style = QuestTypography.headlineMedium,
              color = QuestThemeExtras.colors.primaryText,
          )
        }
      }

      QuestDivider()

      // Viewing Mode Section
      Text(
          text = "Viewing Mode",
          style = QuestTypography.titleLarge,
          color = QuestThemeExtras.colors.primaryText,
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
          style = QuestTypography.titleLarge,
          color = QuestThemeExtras.colors.primaryText,
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
          style = QuestTypography.titleLarge,
          color = QuestThemeExtras.colors.primaryText,
      )

      settings?.let { currentSettings ->
        // Skip Interval - Quest styled
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = QuestThemeExtras.colors.secondary,
            shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
        ) {
          Column(modifier = Modifier.padding(QuestDimensions.ContentPadding.dp)) {
            Text(
                text = "Skip Interval",
                style = QuestTypography.titleMedium,
                color = QuestThemeExtras.colors.primaryText,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Seconds to skip forward/backward",
                style = QuestTypography.bodySmall,
                color = QuestThemeExtras.colors.secondaryText,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
              val skipIntervals = listOf(5, 10, 15, 30)
              skipIntervals.forEach { seconds ->
                FilterChip(
                    selected = currentSettings.skipIntervalMs == seconds * 1000,
                    onClick = { viewModel.updateSkipInterval(seconds * 1000) },
                    label = {
                      Text(
                          "${seconds}s",
                          style = QuestTypography.labelMedium
                      )
                    },
                    modifier = Modifier.heightIn(min = QuestDimensions.SmallButtonHeight.dp)
                )
              }
            }
          }
        }

        // Resume Enabled - Quest styled
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = QuestThemeExtras.colors.secondary,
            shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
        ) {
          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(QuestDimensions.ContentPadding.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                  text = "Resume Playback",
                  style = QuestTypography.titleMedium,
                  color = QuestThemeExtras.colors.primaryText,
              )
              Text(
                  text = "Ask to resume from last position",
                  style = QuestTypography.bodySmall,
                  color = QuestThemeExtras.colors.secondaryText,
                  modifier = Modifier.padding(top = 4.dp)
              )
            }
            Box(
                modifier = Modifier.heightIn(min = QuestDimensions.MinHitTarget.dp),
                contentAlignment = Alignment.Center
            ) {
              Switch(
                  checked = currentSettings.resumeEnabled,
                  onCheckedChange = { viewModel.updateResumeEnabled(it) }
              )
            }
          }
        }
      } ?: run {
        // Loading state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
              color = LocalColorScheme.current.primaryButton,
              strokeWidth = 6.dp,
              modifier = Modifier.size(64.dp)
          )
        }
      }
    }
  }
}

/**
 * Quest-styled permission status card.
 */
@Composable
private fun PermissionCard(
    permissionStatus: PermissionStatus,
    onManagePermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val backgroundColor = when (permissionStatus) {
    PermissionStatus.GRANTED -> QuestColors.success.copy(alpha = 0.15f)
    PermissionStatus.PARTIAL -> QuestColors.warning.copy(alpha = 0.15f)
    PermissionStatus.DENIED -> QuestColors.error.copy(alpha = 0.15f)
  }

  val statusColor = when (permissionStatus) {
    PermissionStatus.GRANTED -> QuestColors.success
    PermissionStatus.PARTIAL -> QuestColors.warning
    PermissionStatus.DENIED -> QuestColors.error
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
              text = "Video Access Permission",
              style = QuestTypography.titleMedium,
              color = QuestThemeExtras.colors.primaryText,
          )
          Text(
              text = when (permissionStatus) {
                PermissionStatus.GRANTED -> "Full access to all videos on device"
                PermissionStatus.PARTIAL -> "Access to selected videos only"
                PermissionStatus.DENIED -> "Not granted - auto-scan disabled"
              },
              style = QuestTypography.bodyMedium,
              color = statusColor,
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
            style = QuestTypography.bodySmall,
            color = QuestThemeExtras.colors.secondaryText,
        )
      }

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
      ) {
        QuestSecondaryButton(
            text = "Manage in Settings",
            onClick = onManagePermissions,
        )
      }
    }
  }
}

/**
 * Quest-styled viewing mode selection card.
 */
@Composable
private fun ViewingModeCard(
    currentMode: ViewingMode,
    onModeSelected: (ViewingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp)
  ) {
    Text(
        text = "Choose how you want to watch videos",
        style = QuestTypography.bodyMedium,
        color = QuestThemeExtras.colors.secondaryText,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
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

/**
 * Quest-styled viewing mode option with hover support.
 */
@Composable
private fun ViewingModeOption(
    emoji: String,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
  QuestSelectableCard(
      selected = isSelected,
      onClick = onClick,
  ) {
    // Emoji icon in styled container
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) LocalColorScheme.current.primaryButton.copy(alpha = 0.2f)
                else QuestThemeExtras.colors.secondary
            ),
        contentAlignment = Alignment.Center
    ) {
      Text(
          text = emoji,
          style = QuestTypography.titleLarge,
      )
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
          text = title,
          style = QuestTypography.titleSmall,
          color = if (isSelected) {
            QuestThemeExtras.colors.primaryText
          } else {
            QuestThemeExtras.colors.secondaryText
          }
      )
      Text(
          text = description,
          style = QuestTypography.bodySmall,
          color = if (isSelected) {
            QuestThemeExtras.colors.primaryText.copy(alpha = 0.8f)
          } else {
            QuestThemeExtras.colors.secondaryText.copy(alpha = 0.8f)
          }
      )
    }

    RadioButton(
        selected = isSelected,
        onClick = null,
    )
  }
}

