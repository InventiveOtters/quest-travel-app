package com.inotter.travelcompanion.ui.sync

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.inotter.travelcompanion.spatial.sync.SyncViewModel
import kotlinx.coroutines.delay

/**
 * Player screen for sync client mode.
 * Displays the video being streamed from the master device.
 * Playback is controlled by sync commands from the master.
 */
@Composable
fun SyncClientPlayerScreen(
    syncViewModel: SyncViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSession by syncViewModel.currentSession.collectAsState()
    val connectedDevices by syncViewModel.connectedDevices.collectAsState()
    
    var showControls by remember { mutableStateOf(true) }
    
    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }
    
    // Handler for back button - leave session and navigate back
    val handleBack: () -> Unit = {
        syncViewModel.leaveSession()
        onBack()
    }
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // Tap anywhere to show controls
                    showControls = true
                }
        ) {
            // Video surface using AndroidView with SurfaceView
            // The SyncViewModel's PlaybackCore renders video frames to this surface
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                syncViewModel.getPlaybackCore().setSurface(holder.surface)
                            }
                            
                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                                // Surface dimensions changed, ExoPlayer handles this automatically
                            }
                            
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                syncViewModel.getPlaybackCore().setSurface(null)
                            }
                        })
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
            
            // Back button at top left corner
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                IconButton(
                    onClick = handleBack,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
            
            // Sync status overlay at top right
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Synced Playback",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        currentSession?.let { session ->
                            Text(
                                text = "Master: ${session.masterDevice.deviceName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

