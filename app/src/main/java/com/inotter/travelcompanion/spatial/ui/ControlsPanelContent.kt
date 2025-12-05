package com.inotter.travelcompanion.spatial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inotter.travelcompanion.spatial.data.EnvironmentType

/**
 * Data class representing the playback state.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val progress: Float = 0f,
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val videoTitle: String = "",
    val lightingIntensity: Float = 0.5f,
    val currentEnvironment: EnvironmentType = EnvironmentType.COLLAB_ROOM,
    val showSettings: Boolean = false  // Toggle for showing settings panel
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
    fun onLightingChanged(intensity: Float)
    fun onEnvironmentChanged(environment: EnvironmentType)
    fun onToggleSettings()
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Video title, settings toggle, and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = playbackState.videoTitle.ifEmpty { "Now Playing" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    // Settings toggle button (lighting/environment)
                    IconButton(
                        onClick = { callback.onToggleSettings() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (playbackState.showSettings) 
                                Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Scene Settings",
                            tint = if (playbackState.showSettings) 
                                Color(0xFF4A90D9) else Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
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
            }

            // Settings Section (collapsible)
            if (playbackState.showSettings) {
                SettingsSection(
                    lightingIntensity = playbackState.lightingIntensity,
                    currentEnvironment = playbackState.currentEnvironment,
                    onLightingChanged = callback::onLightingChanged,
                    onEnvironmentChanged = callback::onEnvironmentChanged
                )
            } else {
                // Progress bar (only show when settings are hidden)
                ProgressSection(
                    progress = playbackState.progress,
                    currentPosition = playbackState.currentPosition,
                    duration = playbackState.duration,
                    onSeek = callback::onSeek
                )
            }
            
            // Bottom: Playback controls
            PlaybackControlsRow(
                playbackState = playbackState,
                callback = callback
            )
        }
    }
}

/**
 * Settings section with lighting slider and environment selector.
 */
@Composable
private fun SettingsSection(
    lightingIntensity: Float,
    currentEnvironment: EnvironmentType,
    onLightingChanged: (Float) -> Unit,
    onEnvironmentChanged: (EnvironmentType) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Lighting Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = "Dark",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            
            Slider(
                value = lightingIntensity,
                onValueChange = onLightingChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFB74D),
                    activeTrackColor = Color(0xFFFFB74D),
                    inactiveTrackColor = Color(0x40FFFFFF)
                )
            )
            
            Icon(
                imageVector = Icons.Default.LightMode,
                contentDescription = "Bright",
                tint = Color(0xFFFFB74D),
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Environment Selector
        Text(
            text = "Environment",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnvironmentType.entries.forEach { environment ->
                EnvironmentChip(
                    environment = environment,
                    isSelected = environment == currentEnvironment,
                    onClick = { onEnvironmentChanged(environment) }
                )
            }
        }
    }
}

/**
 * Environment selection chip.
 */
@Composable
private fun EnvironmentChip(
    environment: EnvironmentType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) Color(0xFF4A90D9) else Color(0x40FFFFFF)
            )
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = environment.displayName,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Progress bar section.
 */
@Composable
private fun ProgressSection(
    progress: Float,
    currentPosition: Long,
    duration: Long,
    onSeek: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Slider(
            value = progress,
            onValueChange = onSeek,
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
                text = formatTime(currentPosition),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
            Text(
                text = formatTime(duration),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Playback controls row.
 */
@Composable
private fun PlaybackControlsRow(
    playbackState: PlaybackState,
    callback: ControlsPanelCallback
) {
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
            size = 36.dp
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Restart button
        ControlButton(
            icon = Icons.Default.Replay,
            contentDescription = "Restart",
            onClick = { callback.onRestart() },
            size = 36.dp
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Rewind button
        ControlButton(
            icon = Icons.Default.FastRewind,
            contentDescription = "Rewind 10s",
            onClick = { callback.onRewind() },
            size = 40.dp
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Play/Pause button (larger)
        ControlButton(
            icon = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
            onClick = { callback.onPlayPause() },
            size = 48.dp,
            isPrimary = true
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Fast Forward button
        ControlButton(
            icon = Icons.Default.FastForward,
            contentDescription = "Forward 10s",
            onClick = { callback.onFastForward() },
            size = 40.dp
        )
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
