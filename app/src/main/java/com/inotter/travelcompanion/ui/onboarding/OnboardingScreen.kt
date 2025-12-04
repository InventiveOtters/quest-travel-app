package com.inotter.travelcompanion.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionManagerImpl
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionStatus

/**
 * Onboarding screen for first-time users.
 * Explains the READ_MEDIA_VIDEO permission and offers auto-scan or SAF fallback.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
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

    // Auto-complete when scan is done
    LaunchedEffect(uiState.scanComplete) {
        if (uiState.scanComplete) {
            viewModel.completeOnboarding()
            onComplete()
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

