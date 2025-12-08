package com.inotter.travelcompanion.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inotter.travelcompanion.ui.theme.QuestColors
import com.inotter.travelcompanion.ui.theme.QuestThemeExtras
import com.inotter.travelcompanion.ui.theme.QuestTypography
import com.meta.spatial.uiset.theme.LocalColorScheme

/**
 * Simple UI state for sync-related visuals that can be shared between 2D and spatial UIs.
 */
data class SyncUiState(
    val isInSyncMode: Boolean,
    val isSyncMaster: Boolean,
    val syncPinCode: String?,
    val connectedDeviceCount: Int,
)

/**
 * Sync status card showing role, PIN and connected devices.
 */
@Composable
fun SyncStatusSection(
    state: SyncUiState,
    onLeaveSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isInSyncMode) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Sync Session",
            style = QuestTypography.titleMedium,
            color = QuestThemeExtras.colors.primaryText
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = QuestThemeExtras.colors.secondary.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (state.isSyncMaster) "Role: Master" else "Role: Client",
                        style = QuestTypography.bodyMedium,
                        color = QuestThemeExtras.colors.primaryText
                    )

                    if (state.isSyncMaster) {
                        Text(
                            text = "${state.connectedDeviceCount} connected",
                            style = QuestTypography.bodySmall,
                            color = QuestThemeExtras.colors.secondaryText
                        )
                    }
                }

                if (state.isSyncMaster && state.syncPinCode != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PIN Code:",
                            style = QuestTypography.bodyMedium,
                            color = QuestThemeExtras.colors.primaryText
                        )
                        Text(
                            text = state.syncPinCode,
                            style = QuestTypography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            ),
                            color = LocalColorScheme.current.primaryButton
                        )
                    }
                }

                Button(
                    onClick = onLeaveSession,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuestColors.error
                    )
                ) {
                    Text("Leave Session")
                }
            }
        }
    }
}

/**
 * Shared controls for creating/joining sync sessions.
 * The caller decides whether the "Create session" button is shown.
 */
@Composable
fun SyncControlsSection(
    showCreateButton: Boolean,
    onCreateSession: () -> Unit,
    onJoinSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showJoinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Multi-Device Sync",
            style = QuestTypography.titleMedium,
            color = QuestThemeExtras.colors.primaryText
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showCreateButton) {
                Button(
                    onClick = onCreateSession,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalColorScheme.current.primaryButton
                    )
                ) {
                    Text("Create Session")
                }
            }

            Button(
                onClick = { showJoinDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuestThemeExtras.colors.secondary
                )
            ) {
                Text("Join Session")
            }
        }
    }

    if (showJoinDialog) {
        JoinSessionDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { pinCode ->
                onJoinSession(pinCode)
                showJoinDialog = false
            }
        )
    }
}

@Composable
private fun JoinSessionDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit,
) {
    var pinCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Join Sync Session",
                style = QuestTypography.titleLarge,
                color = QuestThemeExtras.colors.primaryText
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter the 6-digit PIN code from the master device:",
                    style = QuestTypography.bodyMedium,
                    color = QuestThemeExtras.colors.secondaryText
                )

                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("000000") },
                    singleLine = true,
                    textStyle = QuestTypography.headlineMedium.copy(
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (pinCode.length == 6) onJoin(pinCode) },
                enabled = pinCode.length == 6,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalColorScheme.current.primaryButton
                )
            ) {
                Text("Join")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuestThemeExtras.colors.secondary
                )
            ) {
                Text("Cancel")
            }
        },
        containerColor = QuestColors.surfaceDark,
        shape = RoundedCornerShape(12.dp)
    )
}
