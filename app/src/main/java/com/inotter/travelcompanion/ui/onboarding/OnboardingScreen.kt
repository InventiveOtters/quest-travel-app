package com.inotter.travelcompanion.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionManagerImpl
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionStatus
import com.inotter.travelcompanion.data.models.ViewingMode

/**
 * Onboarding screen for first-time users.
 * Step 1-2: Welcome and video access (permission or SAF).
 * Step 3: Viewing mode selection (2D Panel or Immersive VR).
 *
 * @param onComplete Called when onboarding finishes with 2D mode selected
 * @param onCompleteImmersive Called when onboarding finishes with Immersive mode selected
 * @param onUseSaf Called when user chooses to select folders manually via SAF
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onCompleteImmersive: () -> Unit,
    onUseSaf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        val anyGranted = permissions.values.any { it }

        if (allGranted || anyGranted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // Proceed to mode selection when scan is done
    LaunchedEffect(uiState.scanComplete) {
        if (uiState.scanComplete) {
            viewModel.proceedToModeSelection()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState.currentStep) {
                OnboardingStep.VIDEO_ACCESS -> {
                    when {
                        uiState.isScanning -> ScanningContent(videosFound = uiState.videosFound)
                        uiState.permissionStatus == PermissionStatus.PARTIAL -> PartialAccessContent(
                            onRequestFullAccess = {
                                permissionLauncher.launch(PermissionManagerImpl(context).getPermissionsToRequest())
                            },
                            onContinueWithPartial = {
                                viewModel.onPermissionGranted()
                            },
                            onUseSaf = {
                                viewModel.markHasSafFolders()
                                onUseSaf()
                            }
                        )
                        else -> WelcomeContent(
                            permissionStatus = uiState.permissionStatus,
                            errorMessage = uiState.errorMessage,
                            onFindVideos = {
                                permissionLauncher.launch(PermissionManagerImpl(context).getPermissionsToRequest())
                            },
                            onUseSaf = {
                                viewModel.markHasSafFolders()
                                onUseSaf()
                            }
                        )
                    }
                }
                OnboardingStep.MODE_SELECTION -> {
                    ModeSelectionContent(
                        selectedMode = uiState.selectedViewingMode,
                        videosFound = uiState.videosFound,
                        onModeSelected = { viewModel.selectViewingMode(it) },
                        onContinue = {
                            viewModel.saveViewingModeAndComplete()
                            when (uiState.selectedViewingMode) {
                                ViewingMode.PANEL_2D -> onComplete()
                                ViewingMode.IMMERSIVE -> onCompleteImmersive()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeContent(
    permissionStatus: PermissionStatus,
    errorMessage: String?,
    onFindVideos: () -> Unit,
    onUseSaf: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.widthIn(max = 500.dp),
    ) {
        Text(
            text = "🎬",
            style = MaterialTheme.typography.displayLarge,
        )

        Text(
            text = "Welcome to Travel Companion",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Find and play your videos in immersive VR. " +
                   "Grant permission to automatically discover all videos on your device, " +
                   "or manually select folders.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Permission explanation card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "📁 Auto-Scan Permission",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Allows the app to find video files in Movies, Downloads, DCIM, and other folders. " +
                           "Your videos stay on your device - nothing is uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Primary action
        Button(
            onClick = onFindVideos,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Find My Videos", style = MaterialTheme.typography.titleMedium)
        }

        // Secondary action
        TextButton(onClick = onUseSaf) {
            Text("Select folders manually instead")
        }
    }
}

@Composable
private fun ScanningContent(videosFound: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 6.dp,
        )

        Text(
            text = "Scanning your videos...",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (videosFound > 0) {
            Text(
                text = "Found $videosFound videos",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "This may take a moment for large libraries",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PartialAccessContent(
    onRequestFullAccess: () -> Unit,
    onContinueWithPartial: () -> Unit,
    onUseSaf: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.widthIn(max = 500.dp),
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayLarge,
        )

        Text(
            text = "Limited Access Granted",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "You've granted access to selected files only. " +
                   "For the best experience, we recommend allowing access to all videos.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onRequestFullAccess,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Allow All Videos", style = MaterialTheme.typography.titleMedium)
        }

        OutlinedButton(
            onClick = onContinueWithPartial,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue with Selected Videos")
        }

        TextButton(onClick = onUseSaf) {
            Text("Select folders manually instead")
        }
    }
}

/**
 * Step 3: Mode selection content.
 * User chooses between 2D Panel Mode and Immersive VR Mode.
 */
@Composable
private fun ModeSelectionContent(
    selectedMode: ViewingMode,
    videosFound: Int,
    onModeSelected: (ViewingMode) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.widthIn(max = 600.dp),
    ) {
        // Success indicator
        if (videosFound > 0) {
            Text(
                text = "✅",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = "Found $videosFound videos!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "How would you like to watch?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Choose your preferred viewing experience. You can change this later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Mode selection cards
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModeOptionCard(
                emoji = "📱",
                title = "2D Panel Mode",
                description = "Watch in a floating panel. Great for multitasking.",
                isSelected = selectedMode == ViewingMode.PANEL_2D,
                onClick = { onModeSelected(ViewingMode.PANEL_2D) }
            )

            ModeOptionCard(
                emoji = "🥽",
                title = "Immersive VR Mode",
                description = "Full immersive experience. Feel like you're there.",
                isSelected = selectedMode == ViewingMode.IMMERSIVE,
                onClick = { onModeSelected(ViewingMode.IMMERSIVE) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Continue button
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * A selectable card for viewing mode options.
 */
@Composable
private fun ModeOptionCard(
    emoji: String,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineLarge,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    }
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = null, // handled by card
            )
        }
    }
}
