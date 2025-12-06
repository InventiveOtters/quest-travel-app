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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * Simplified to show only scene settings (lighting slider + environment selector).
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scene Settings",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Lighting Slider Section
            LightingSliderSection(
                lightingIntensity = playbackState.lightingIntensity,
                onLightingChanged = callback::onLightingChanged
            )
            
            // Environment Selector Section
            EnvironmentSelectorSection(
                currentEnvironment = playbackState.currentEnvironment,
                onEnvironmentChanged = callback::onEnvironmentChanged
            )
        }
    }
}

/**
 * Lighting slider with dark/bright icons.
 */
@Composable
private fun LightingSliderSection(
    lightingIntensity: Float,
    onLightingChanged: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Lighting",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = "Dark",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp)
            )
            
            Slider(
                value = lightingIntensity,
                onValueChange = onLightingChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
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
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Environment selector with chips.
 */
@Composable
private fun EnvironmentSelectorSection(
    currentEnvironment: EnvironmentType,
    onEnvironmentChanged: (EnvironmentType) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Environment",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = environment.displayName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
