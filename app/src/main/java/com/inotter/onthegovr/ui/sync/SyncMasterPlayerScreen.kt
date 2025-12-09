package com.inotter.onthegovr.ui.sync

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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.inotter.onthegovr.data.datasources.videolibrary.models.VideoItem
import com.inotter.onthegovr.spatial.sync.SyncViewModel
import kotlinx.coroutines.delay

/**
 * Player screen for sync master mode.
 * Displays the video being played locally and broadcasts playback commands to all connected clients.
 */
@Composable
fun SyncMasterPlayerScreen(
    syncViewModel: SyncViewModel,
    video: VideoItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSession by syncViewModel.currentSession.collectAsState()
    val connectedDevices by syncViewModel.connectedDevices.collectAsState()
    val isPlaybackLoading by syncViewModel.isPlaybackLoading.collectAsState()

    var showControls by remember { mutableStateOf(true) }

    // Track playback state
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    // Track if this is the first time starting playback
    var hasStartedPlayback by remember { mutableStateOf(false) }

    // Track seeking state
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }

    // Load video when screen is first displayed and auto-start playback
    LaunchedEffect(video.id) {
        val playbackCore = syncViewModel.getPlaybackCore()
        playbackCore.prepare(android.net.Uri.parse(video.fileUri), startPositionMs = 0L)
        // Auto-start playback and broadcast to clients
        hasStartedPlayback = true
        syncViewModel.start(position = 0L)
    }

    // Check playback state periodically
    LaunchedEffect(Unit) {
        while (true) {
            val playbackCore = syncViewModel.getPlaybackCore()
            isPlaying = playbackCore.isPlaying()
            currentPosition = playbackCore.getCurrentPosition()
            val dur = playbackCore.getDuration()
            if (dur > 0) {
                duration = dur
            }
            delay(250)
        }
    }

    // Clear seeking state when playback loading completes
    LaunchedEffect(isPlaybackLoading) {
        if (!isPlaybackLoading && isSeeking) {
            isSeeking = false
        }
    }

    // Auto-hide controls after 3 seconds when playing
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // Handler for back button - close session and navigate back
    val handleBack: () -> Unit = {
        syncViewModel.closeSession()
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
                            text = "Hosting Session",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        currentSession?.let { session ->
                            Text(
                                text = "PIN: ${session.pinCode}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "${connectedDevices.size} device(s) connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Playback controls overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Video title
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    // Seek bar with time labels
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val displayPosition = if (isSeeking) seekPosition else currentPosition.toFloat()

                        Slider(
                            value = displayPosition,
                            onValueChange = { newValue ->
                                isSeeking = true
                                seekPosition = newValue
                                showControls = true
                            },
                            onValueChangeFinished = {
                                // Broadcast seek command to all clients
                                syncViewModel.seekTo(seekPosition.toLong())
                                // Keep isSeeking = true, it will be cleared when loading completes
                            },
                            valueRange = 0f..duration.toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatTime(if (isSeeking) seekPosition.toLong() else currentPosition),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                            )
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                            )
                        }
                    }

                    // Control buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Skip backward
                        IconButton(onClick = {
                            val newPosition = (currentPosition - 10_000L).coerceAtLeast(0L)
                            syncViewModel.seekTo(newPosition)
                        }) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Skip Backward",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Play/Pause or Loading Indicator
                        Box(
                            modifier = Modifier.size(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPlaybackLoading || isSeeking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = Color.White,
                                    strokeWidth = 4.dp
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        if (isPlaying) {
                                            syncViewModel.pause()
                                        } else {
                                            // First time: send "start" command
                                            // Subsequent times: send "play" command
                                            if (!hasStartedPlayback) {
                                                hasStartedPlayback = true
                                                syncViewModel.start(currentPosition)
                                            } else {
                                                syncViewModel.play(currentPosition)
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(64.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        modifier = Modifier.size(48.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Skip forward
                        IconButton(onClick = {
                            val newPosition = (currentPosition + 10_000L).coerceAtMost(duration)
                            syncViewModel.seekTo(newPosition)
                        }) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Skip Forward",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}

