package com.inotter.travelcompanion.spatial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data class representing the playback state.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val progress: Float = 0f,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val videoTitle: String = ""
)

/**
 * Callback interface for controls panel actions.
 */
interface ControlsPanelCallback {
    fun onPlayPause()
    fun onSeek(position: Float)
    fun onRewind()
    fun onFastForward()
    fun onRestart()
    fun onMuteToggle()
    fun onClose()
}

/**
 * Main composable for the controls panel content.
 */
@Composable
fun ControlsPanelContent(
    playbackState: PlaybackState,
    callback: ControlsPanelCallback,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xCC1A1A2E),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Video title and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = playbackState.videoTitle.ifEmpty { "Now Playing" },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                
                IconButton(
                    onClick = { callback.onClose() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
            
            // Middle: Progress bar
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = playbackState.progress,
                    onValueChange = { callback.onSeek(it) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF4A90D9),
                        activeTrackColor = Color(0xFF4A90D9),
                        inactiveTrackColor = Color(0x40FFFFFF)
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(playbackState.currentPosition),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(playbackState.duration),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
            
            // Bottom: Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute button
                ControlButton(
                    icon = if (playbackState.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (playbackState.isMuted) "Unmute" else "Mute",
                    onClick = { callback.onMuteToggle() },
                    size = 40.dp
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Restart button
                ControlButton(
                    icon = Icons.Default.Replay,
                    contentDescription = "Restart",
                    onClick = { callback.onRestart() },
                    size = 40.dp
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Rewind button
                ControlButton(
                    icon = Icons.Default.FastRewind,
                    contentDescription = "Rewind 10s",
                    onClick = { callback.onRewind() },
                    size = 44.dp
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Play/Pause button (larger)
                ControlButton(
                    icon = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    onClick = { callback.onPlayPause() },
                    size = 56.dp,
                    isPrimary = true
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Fast Forward button
                ControlButton(
                    icon = Icons.Default.FastForward,
                    contentDescription = "Forward 10s",
                    onClick = { callback.onFastForward() },
                    size = 44.dp
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    isPrimary: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isPrimary) Color(0xFF4A90D9) else Color(0x40FFFFFF)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * Formats milliseconds to MM:SS format.
 */
private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
