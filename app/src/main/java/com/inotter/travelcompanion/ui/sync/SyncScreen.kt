package com.inotter.travelcompanion.ui.sync

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inotter.travelcompanion.data.datasources.videolibrary.models.VideoItem
import com.inotter.travelcompanion.playback.PlaybackCore
import com.inotter.travelcompanion.spatial.sync.SyncViewModel
import com.inotter.travelcompanion.ui.theme.QuestDimensions
import com.inotter.travelcompanion.ui.theme.QuestDivider
import com.inotter.travelcompanion.ui.theme.QuestIconButton
import com.inotter.travelcompanion.ui.theme.QuestLoadingContent
import com.inotter.travelcompanion.ui.theme.QuestPrimaryButton
import com.inotter.travelcompanion.ui.theme.QuestThemeExtras
import com.inotter.travelcompanion.ui.theme.QuestTypography
import com.meta.spatial.uiset.theme.LocalColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 2D panel screen for multi-device sync controls, reachable from the library top bar.
 */
@Composable
fun SyncScreen(
	    syncViewModel: SyncViewModel,
	    onBack: () -> Unit,
	    currentVideo: VideoItem? = null,
		    autoCreateOnEnter: Boolean = false,
	    onJoinedSession: () -> Unit = {},
	    onCreatedSession: () -> Unit = {},
	    modifier: Modifier = Modifier,
) {
    val currentSession by syncViewModel.currentSession.collectAsState()
    val connectedDevices by syncViewModel.connectedDevices.collectAsState()
    val syncMode by syncViewModel.syncMode.collectAsState()
    val isLoading by syncViewModel.isLoading.collectAsState()

	    val scope = rememberCoroutineScope()

    // Navigate to player when client joins (automatic)
    // For master, navigation happens manually via "Start watching together" button
    LaunchedEffect(syncMode) {
        when (syncMode) {
            SyncViewModel.SyncMode.CLIENT -> onJoinedSession()
            // MASTER mode: Don't auto-navigate, wait for user to press "Start watching together"
            else -> {}
        }
    }

	    // Optionally auto-create a session for the current video when entering this screen
	    val hasAutoHosted = remember { androidx.compose.runtime.mutableStateOf(false) }
	    LaunchedEffect(currentVideo?.id, autoCreateOnEnter) {
	        if (autoCreateOnEnter && currentVideo != null && !hasAutoHosted.value && !syncViewModel.isInSyncMode()) {
	            hasAutoHosted.value = true
	            syncViewModel.createSession(
	                videoUri = currentVideo.fileUri,
	                movieId = currentVideo.id.toString()
	            )
	        }
	    }

    // Note: We don't call syncViewModel.onCleared() in DisposableEffect here because:
    // 1. When client joins, this screen navigates to player screen and gets disposed
    // 2. Calling onCleared() would leave the session prematurely
    // 3. The ViewModel should manage its own lifecycle independently of screen composition

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(brush = LocalColorScheme.current.panel),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(QuestDimensions.ContentPadding.dp),
            verticalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp)
        ) {
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
                            modifier = Modifier.height(24.dp),
                        )
                    }
                    Text(
                        text = "Multi-Device Sync",
                        style = QuestTypography.headlineMedium,
                        color = QuestThemeExtras.colors.primaryText,
                    )
                }
	            }

	            QuestDivider()

	            val isInSyncMode = syncViewModel.isInSyncMode()
	            val isMaster = syncViewModel.isMaster()
	            val pinCode = currentSession?.pinCode
	            val connectedCount = connectedDevices.size

	            // Show loading indicator when configuring session in auto-create mode
	            if (autoCreateOnEnter && isLoading && !isInSyncMode) {
	                QuestLoadingContent(
	                    message = "Configuring session...",
	                    modifier = Modifier.fillMaxWidth()
	                )
	            } else if (autoCreateOnEnter && isInSyncMode && isMaster) {
	                // Auto-create mode: Show session info and "Start watching together" button
	                SyncStatusSection(
	                    state = SyncUiState(
	                        isInSyncMode = isInSyncMode,
	                        isSyncMaster = isMaster,
	                        syncPinCode = pinCode,
	                        connectedDeviceCount = connectedCount,
	                    ),
	                    onLeaveSession = {
	                        syncViewModel.closeSession()
	                    }
	                )

	                Spacer(modifier = Modifier.height(QuestDimensions.ItemSpacing.dp))

	                // "Start watching together" button - enabled only when at least 1 device is connected
	                QuestPrimaryButton(
	                    text = "Start watching together",
	                    onClick = {
	                        // Navigate to master player screen
	                        onCreatedSession()
	                    },
	                    enabled = connectedCount > 0,
	                    expanded = true
	                )

	                if (connectedCount == 0) {
	                    Spacer(modifier = Modifier.height(8.dp))
	                    Text(
	                        text = "Waiting for devices to connect...",
	                        style = QuestTypography.bodyMedium,
	                        color = QuestThemeExtras.colors.secondaryText,
	                        textAlign = TextAlign.Center,
	                        modifier = Modifier.fillMaxWidth()
	                    )
	                }
	            } else {
	                // Normal mode: Show status and controls
	                SyncStatusSection(
	                    state = SyncUiState(
	                        isInSyncMode = isInSyncMode,
	                        isSyncMaster = isMaster,
	                        syncPinCode = pinCode,
	                        connectedDeviceCount = connectedCount,
	                    ),
	                    onLeaveSession = {
	                        if (syncViewModel.isMaster()) {
	                            syncViewModel.closeSession()
	                        } else {
	                            syncViewModel.leaveSession()
	                        }
	                    }
	                )

	                Spacer(modifier = Modifier.height(QuestDimensions.ItemSpacing.dp))

	                // If hosting, show "Start watching together" button
	                if (isMaster) {
	                    QuestPrimaryButton(
	                        text = "Start watching together",
	                        onClick = {
	                            // Navigate to master player screen
	                            onCreatedSession()
	                        },
	                        enabled = connectedCount > 0,
	                        expanded = true
	                    )

	                    if (connectedCount == 0) {
	                        Spacer(modifier = Modifier.height(8.dp))
	                        Text(
	                            text = "Waiting for devices to connect...",
	                            style = QuestTypography.bodyMedium,
	                            color = QuestThemeExtras.colors.secondaryText,
	                            textAlign = TextAlign.Center,
	                            modifier = Modifier.fillMaxWidth()
	                        )
	                    }
	                } else {
	                    // Not hosting: Show join controls only (no create button when coming from library)
	                    Text(
	                        text = "Join a session hosted from your phone or another headset using the 6-digit PIN.",
	                        style = QuestTypography.bodyMedium,
	                        color = QuestThemeExtras.colors.secondaryText,
	                    )

	                    SyncControlsSection(
	                        showCreateButton = false,  // Never show create button from library - only join
	                        onCreateSession = {},
	                        onJoinSession = { pinCodeJoin ->
	                            syncViewModel.joinSessionByPin(pinCodeJoin)
	                        },
	                        isLoading = isLoading
	                    )
	                }
	            }
        }
    }
}

@Composable
fun rememberSyncViewModel(context: Context): SyncViewModel {
    return remember(context) {
        val playbackCore = PlaybackCore(context)
        SyncViewModel(context = context, playbackCore = playbackCore)
    }
}
