package com.inotter.onthegovr.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inotter.onthegovr.data.managers.PermissionManager.PermissionManagerImpl
import com.inotter.onthegovr.data.managers.PermissionManager.PermissionStatus
import com.inotter.onthegovr.data.models.ViewingMode
import com.inotter.onthegovr.ui.theme.QuestColors
import com.inotter.onthegovr.ui.theme.QuestDimensions
import com.inotter.onthegovr.ui.theme.QuestInfoCard
import com.inotter.onthegovr.ui.theme.QuestPrimaryButton
import com.inotter.onthegovr.ui.theme.QuestSecondaryButton
import com.inotter.onthegovr.ui.theme.QuestSelectableCard
import com.inotter.onthegovr.ui.theme.QuestTextButton
import com.inotter.onthegovr.ui.theme.QuestThemeExtras
import com.inotter.onthegovr.ui.theme.QuestTypography
import com.meta.spatial.uiset.theme.LocalColorScheme

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
        modifier = modifier
            .fillMaxSize()
            .background(brush = LocalColorScheme.current.panel),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(QuestDimensions.SectionSpacing.dp),
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

/**
 * Quest-styled welcome content with proper hit targets and VR typography.
 */
@Composable
private fun WelcomeContent(
    permissionStatus: PermissionStatus,
    errorMessage: String?,
    onFindVideos: () -> Unit,
    onUseSaf: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QuestDimensions.SectionSpacing.dp),
        modifier = Modifier.widthIn(max = 550.dp),
    ) {
        // Icon
        OnboardingIcon(emoji = "🎬")

        Text(
            text = "Welcome to OnTheGoVR",
            style = QuestTypography.headlineMedium,
            textAlign = TextAlign.Center,
            color = QuestThemeExtras.colors.primaryText,
        )

        Text(
            text = "Find and play your videos in immersive VR. " +
                   "Grant permission to automatically discover all videos on your device, " +
                   "or manually select folders.",
            style = QuestTypography.bodyLarge,
            textAlign = TextAlign.Center,
            color = QuestThemeExtras.colors.secondaryText,
        )

        // Permission explanation card
        QuestInfoCard {
            Text(
                text = "Auto-Scan Permission",
                style = QuestTypography.titleMedium,
                color = QuestThemeExtras.colors.primaryText,
            )
            Text(
                text = "Allows the app to find video files in Movies, Downloads, DCIM, and other folders. " +
                       "Your videos stay on your device - nothing is uploaded.",
                style = QuestTypography.bodyMedium,
                color = QuestThemeExtras.colors.secondaryText,
            )
        }

        errorMessage?.let {
            Text(
                text = it,
                color = QuestColors.error,
                style = QuestTypography.bodyMedium,
            )
        }

        // Primary action with proper hit target
        QuestPrimaryButton(
            text = "Find My Videos",
            onClick = onFindVideos,
            expanded = true,
        )

        // Secondary action
        QuestTextButton(
            text = "Select folders manually instead",
            onClick = onUseSaf,
        )
    }
}

/**
 * Quest-styled scanning progress content.
 */
@Composable
private fun ScanningContent(videosFound: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QuestDimensions.SectionSpacing.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(72.dp),
            strokeWidth = 6.dp,
            color = LocalColorScheme.current.primaryButton,
        )

        Text(
            text = "Scanning your videos...",
            style = QuestTypography.headlineSmall,
            color = QuestThemeExtras.colors.primaryText,
        )

        if (videosFound > 0) {
            Text(
                text = "Found $videosFound videos",
                style = QuestTypography.bodyLarge,
                color = LocalColorScheme.current.primaryButton,
            )
        }

        Text(
            text = "This may take a moment for large libraries",
            style = QuestTypography.bodyMedium,
            color = QuestThemeExtras.colors.secondaryText,
        )
    }
}

/**
 * Quest-styled partial access content.
 */
@Composable
private fun PartialAccessContent(
    onRequestFullAccess: () -> Unit,
    onContinueWithPartial: () -> Unit,
    onUseSaf: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QuestDimensions.SectionSpacing.dp),
        modifier = Modifier.widthIn(max = 550.dp),
    ) {
        OnboardingIcon(emoji = "⚠️")

        Text(
            text = "Limited Access Granted",
            style = QuestTypography.headlineMedium,
            textAlign = TextAlign.Center,
            color = QuestThemeExtras.colors.primaryText,
        )

        Text(
            text = "You've granted access to selected files only. " +
                   "For the best experience, we recommend allowing access to all videos.",
            style = QuestTypography.bodyLarge,
            textAlign = TextAlign.Center,
            color = QuestThemeExtras.colors.secondaryText,
        )

        QuestPrimaryButton(
            text = "Allow All Videos",
            onClick = onRequestFullAccess,
            expanded = true,
        )

        QuestSecondaryButton(
            text = "Continue with Selected Videos",
            onClick = onContinueWithPartial,
            expanded = true,
        )

        QuestTextButton(
            text = "Select folders manually instead",
            onClick = onUseSaf,
        )
    }
}

/**
 * Step 3: Quest-styled mode selection content.
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
        verticalArrangement = Arrangement.spacedBy(QuestDimensions.SectionSpacing.dp),
        modifier = Modifier.widthIn(max = 650.dp),
    ) {
        // Success indicator
        if (videosFound > 0) {
            OnboardingIcon(emoji = "✓", isSuccess = true)
            Text(
                text = "Found $videosFound videos!",
                style = QuestTypography.titleMedium,
                color = QuestColors.success,
            )
        }

        Text(
            text = "How would you like to watch?",
            style = QuestTypography.headlineMedium,
            textAlign = TextAlign.Center,
            color = QuestThemeExtras.colors.primaryText,
        )

        Text(
            text = "Choose your preferred viewing experience. You can change this later in Settings.",
            style = QuestTypography.bodyLarge,
            textAlign = TextAlign.Center,
            color = QuestThemeExtras.colors.secondaryText,
        )

        // Mode selection cards with hover support
        Column(
            verticalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp),
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

        // Continue button with proper hit target
        QuestPrimaryButton(
            text = "Continue",
            onClick = onContinue,
            expanded = true,
        )
    }
}

/**
 * Quest-styled selectable card for viewing mode options with hover support.
 */
@Composable
private fun ModeOptionCard(
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
        // Emoji icon
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) LocalColorScheme.current.primaryButton.copy(alpha = 0.2f)
                    else QuestThemeExtras.colors.secondary
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                style = QuestTypography.headlineMedium,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = QuestTypography.titleLarge,
                color = if (isSelected) {
                    QuestThemeExtras.colors.primaryText
                } else {
                    QuestThemeExtras.colors.secondaryText
                }
            )
            Text(
                text = description,
                style = QuestTypography.bodyMedium,
                color = if (isSelected) {
                    QuestThemeExtras.colors.primaryText.copy(alpha = 0.8f)
                } else {
                    QuestThemeExtras.colors.secondaryText.copy(alpha = 0.8f)
                }
            )
        }

        RadioButton(
            selected = isSelected,
            onClick = null, // handled by card
        )
    }
}

/**
 * Quest-styled icon container for onboarding screens.
 */
@Composable
private fun OnboardingIcon(
    emoji: String,
    isSuccess: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(88.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSuccess) QuestColors.success.copy(alpha = 0.15f)
                else QuestThemeExtras.colors.secondary
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            style = QuestTypography.displayMedium,
            color = if (isSuccess) QuestColors.success else QuestThemeExtras.colors.primaryText,
        )
    }
}
