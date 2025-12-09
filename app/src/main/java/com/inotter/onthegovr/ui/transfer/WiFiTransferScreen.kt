package com.inotter.onthegovr.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inotter.onthegovr.ui.theme.QuestColors
import com.inotter.onthegovr.ui.theme.QuestDimensions
import com.inotter.onthegovr.ui.theme.QuestDivider
import com.inotter.onthegovr.ui.theme.QuestIconButton
import com.inotter.onthegovr.ui.theme.QuestInfoCard
import com.inotter.onthegovr.ui.theme.QuestPrimaryButton
import com.inotter.onthegovr.ui.theme.QuestSecondaryButton
import com.inotter.onthegovr.ui.theme.QuestThemeExtras
import com.inotter.onthegovr.ui.theme.QuestTypography
import com.meta.spatial.uiset.theme.LocalColorScheme

/**
 * WiFi Transfer screen for uploading videos from other devices.
 * Displays server status, IP address, and recent uploads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiTransferScreen(
    viewModel: TransferViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

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
            horizontalAlignment = Alignment.CenterHorizontally
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
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = QuestThemeExtras.colors.primaryText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "WiFi Transfer",
                        style = QuestTypography.headlineMedium,
                        color = QuestThemeExtras.colors.primaryText,
                    )
                }
            }

            QuestDivider(modifier = Modifier.padding(vertical = QuestDimensions.ItemSpacing.dp))

            // WiFi Status Warning
            if (!uiState.isWifiConnected) {
                Surface(
                    color = QuestColors.error.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = QuestDimensions.ItemSpacing.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(QuestDimensions.ContentPadding.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = QuestColors.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Not connected to WiFi. Please connect to a WiFi network.",
                            style = QuestTypography.bodyMedium,
                            color = QuestThemeExtras.colors.primaryText,
                        )
                    }
                }
            }

            // Error Display
            uiState.error?.let { error ->
                Surface(
                    color = QuestColors.error.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = QuestDimensions.ItemSpacing.dp)
                ) {
                    Text(
                        text = error,
                        style = QuestTypography.bodyMedium,
                        color = QuestColors.error,
                        modifier = Modifier.padding(QuestDimensions.ContentPadding.dp)
                    )
                }
            }

            // Server Status Card
            ServerStatusCard(
                isRunning = uiState.isServerRunning,
                isStarting = uiState.isStarting,
                ipAddress = uiState.ipAddress,
                port = uiState.port,
                onSpeakAddress = { viewModel.speakAddress() }
            )

            Spacer(modifier = Modifier.height(QuestDimensions.ItemSpacing.dp))

            // Info hints - Quest styled
            if (uiState.isServerRunning) {
                QuestInfoCard {
                    Text(
                        text = "Both devices must be on the same WiFi network",
                        style = QuestTypography.bodyMedium,
                        color = QuestThemeExtras.colors.primaryText,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Uploaded videos are saved to Movies/OnTheGoVR",
                        style = QuestTypography.bodyMedium,
                        color = QuestThemeExtras.colors.primaryText,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Videos appear in your library automatically",
                        style = QuestTypography.bodySmall,
                        color = QuestThemeExtras.colors.secondaryText,
                    )
                    Text(
                        text = "• Files remain even if the app is uninstalled",
                        style = QuestTypography.bodySmall,
                        color = QuestThemeExtras.colors.secondaryText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(QuestDimensions.ItemSpacing.dp))

            // PIN Protection Section
            PinProtectionCard(
                pinEnabled = uiState.pinEnabled,
                currentPin = uiState.currentPin,
                onTogglePin = { viewModel.togglePinProtection() }
            )

            Spacer(modifier = Modifier.height(QuestDimensions.SectionSpacing.dp))

            // Recent Uploads Section
            if (uiState.recentUploads.isNotEmpty()) {
                RecentUploadsSection(uploads = uiState.recentUploads)
            }

            Spacer(modifier = Modifier.height(QuestDimensions.SectionSpacing.dp))

            // Storage Info
            StorageInfoCard(
                availableStorage = uiState.availableStorage,
                isLow = viewModel.isStorageLow(),
                isCritical = viewModel.isStorageCritical()
            )

            Spacer(modifier = Modifier.height(QuestDimensions.ItemSpacing.dp))

            // Start/Stop Button - Quest styled
            if (uiState.isServerRunning) {
                QuestSecondaryButton(
                    text = if (uiState.isStarting) "Starting Server..." else "Stop Server",
                    onClick = { viewModel.toggleServer() },
                    enabled = uiState.isWifiConnected && !uiState.isStarting,
                    expanded = true,
                )
            } else {
                QuestPrimaryButton(
                    text = if (uiState.isStarting) "Starting Server..." else "Start Server",
                    onClick = { viewModel.toggleServer() },
                    enabled = uiState.isWifiConnected && !uiState.isStarting,
                    expanded = true,
                )
            }
        }
    }
}

/**
 * Quest-styled server status card.
 */
@Composable
private fun ServerStatusCard(
    isRunning: Boolean,
    isStarting: Boolean,
    ipAddress: String?,
    port: Int,
    onSpeakAddress: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = QuestThemeExtras.colors.secondary,
        shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
    ) {
        Column(
            modifier = Modifier.padding(QuestDimensions.SectionSpacing.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WiFi Transfer Server",
                style = QuestTypography.headlineSmall,
                color = QuestThemeExtras.colors.primaryText,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(QuestDimensions.ItemSpacing.dp))

            if (isRunning && ipAddress != null) {
                Text(
                    text = "On your phone or computer, open a browser and type this address:",
                    style = QuestTypography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = QuestThemeExtras.colors.secondaryText,
                )

                Spacer(modifier = Modifier.height(QuestDimensions.ItemSpacing.dp))

                // Full URL Display - Quest styled
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LocalColorScheme.current.primaryButton.copy(alpha = 0.1f))
                        .border(
                            2.dp,
                            LocalColorScheme.current.primaryButton,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(QuestDimensions.ContentPadding.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "http://$ipAddress:$port",
                        style = QuestTypography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = LocalColorScheme.current.primaryButton,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Important: HTTP notice
                Text(
                    text = "Use http:// (not https://)",
                    style = QuestTypography.bodySmall,
                    color = QuestColors.warning,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Read Aloud Button
                QuestSecondaryButton(
                    text = "Read Aloud",
                    onClick = onSpeakAddress,
                )
            } else if (isStarting) {
                CircularProgressIndicator(
                    color = LocalColorScheme.current.primaryButton,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Starting server...",
                    style = QuestTypography.bodyMedium,
                    color = QuestThemeExtras.colors.secondaryText,
                )
            } else {
                Text(
                    text = "Server is stopped",
                    style = QuestTypography.bodyLarge,
                    color = QuestThemeExtras.colors.secondaryText,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap 'Start Server' to begin accepting uploads",
                    style = QuestTypography.bodyMedium,
                    color = QuestThemeExtras.colors.secondaryText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Quest-styled recent uploads section.
 */
@Composable
private fun RecentUploadsSection(uploads: List<TransferViewModel.UploadInfo>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Recently Uploaded:",
            style = QuestTypography.titleMedium,
            color = QuestThemeExtras.colors.primaryText,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uploads) { upload ->
                Surface(
                    color = QuestThemeExtras.colors.secondary,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(QuestDimensions.ItemSpacing.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = upload.name,
                                style = QuestTypography.bodyMedium,
                                color = QuestThemeExtras.colors.primaryText,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${upload.sizeFormatted} - ${upload.timeAgo}",
                                style = QuestTypography.bodySmall,
                                color = QuestThemeExtras.colors.secondaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quest-styled storage info card.
 */
@Composable
private fun StorageInfoCard(
    availableStorage: String,
    isLow: Boolean,
    isCritical: Boolean
) {
    val backgroundColor = when {
        isCritical -> QuestColors.error.copy(alpha = 0.15f)
        isLow -> QuestColors.warning.copy(alpha = 0.15f)
        else -> QuestThemeExtras.colors.secondary
    }

    val statusColor = when {
        isCritical -> QuestColors.error
        isLow -> QuestColors.warning
        else -> QuestThemeExtras.colors.primaryText
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(QuestDimensions.ContentPadding.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isCritical -> "Storage Critical:"
                        isLow -> "Storage Low:"
                        else -> "Storage Available:"
                    },
                    style = QuestTypography.bodyMedium,
                    color = statusColor,
                )
                Text(
                    text = availableStorage,
                    style = QuestTypography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
            }

            // Show warning message for low/critical storage
            if (isCritical) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Storage is critically low. Uploads are disabled until you free up space.",
                    style = QuestTypography.bodySmall,
                    color = statusColor,
                )
            } else if (isLow) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Storage is running low. Consider freeing up space soon.",
                    style = QuestTypography.bodySmall,
                    color = statusColor,
                )
            }
        }
    }
}

/**
 * Quest-styled PIN protection card.
 */
@Composable
private fun PinProtectionCard(
    pinEnabled: Boolean,
    currentPin: String?,
    onTogglePin: () -> Unit
) {
    val backgroundColor = if (pinEnabled) {
        LocalColorScheme.current.primaryButton.copy(alpha = 0.15f)
    } else {
        QuestThemeExtras.colors.secondary
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(QuestDimensions.ContentPadding.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PIN Protection",
                        style = QuestTypography.titleMedium,
                        color = QuestThemeExtras.colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (pinEnabled) "Uploads require PIN" else "Anyone on the network can upload",
                        style = QuestTypography.bodySmall,
                        color = QuestThemeExtras.colors.secondaryText,
                    )
                }
                Box(
                    modifier = Modifier.heightIn(min = QuestDimensions.MinHitTarget.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Switch(
                        checked = pinEnabled,
                        onCheckedChange = { onTogglePin() }
                    )
                }
            }

            // Show PIN when enabled
            if (pinEnabled && currentPin != null) {
                Spacer(modifier = Modifier.height(QuestDimensions.ItemSpacing.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(QuestThemeExtras.colors.secondary)
                        .border(
                            width = 2.dp,
                            color = LocalColorScheme.current.primaryButton,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(QuestDimensions.ContentPadding.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PIN Code",
                            style = QuestTypography.labelMedium,
                            color = QuestThemeExtras.colors.secondaryText,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentPin,
                            style = QuestTypography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp),
                            color = LocalColorScheme.current.primaryButton,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Enter this PIN in the web browser",
                            style = QuestTypography.bodySmall,
                            color = QuestThemeExtras.colors.secondaryText,
                        )
                    }
                }
            }
        }
    }
}
