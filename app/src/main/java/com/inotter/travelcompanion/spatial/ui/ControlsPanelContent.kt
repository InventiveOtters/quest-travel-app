package com.inotter.travelcompanion.spatial.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inotter.travelcompanion.R
import com.inotter.travelcompanion.spatial.data.EnvironmentType
import com.inotter.travelcompanion.ui.theme.QuestDimensions
import com.inotter.travelcompanion.ui.theme.QuestThemeExtras
import com.inotter.travelcompanion.ui.theme.QuestTypography
import com.meta.spatial.uiset.theme.LocalColorScheme

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
    val showSettings: Boolean = false,  // Toggle for showing settings panel
    // Sync-related fields (no longer used in immersive controls panel; kept for compatibility)
    val isInSyncMode: Boolean = false,  // Whether sync is active
    val isSyncMaster: Boolean = false,  // Whether this device is the master
    val syncPinCode: String? = null,    // PIN code for the session
    val connectedDeviceCount: Int = 0   // Number of connected devices
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
 * Styled to match LibraryScreen with Quest theme.
 */
@Composable
fun ControlsPanelContent(
    playbackState: PlaybackState,
    callback: ControlsPanelCallback,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(brush = LocalColorScheme.current.panel),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(QuestDimensions.ContentPadding.dp),
            verticalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp)
        ) {
            // Header with title and logo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = QuestTypography.headlineMedium,
                    color = QuestThemeExtras.colors.primaryText,
                )
                
                // App logo
                Image(
                    painter = painterResource(id = R.drawable.onthego_logo),
                    contentDescription = "On The Go Logo",
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DarkMode,
            contentDescription = "Dark",
            tint = QuestThemeExtras.colors.secondaryText,
            modifier = Modifier.size(22.dp)
        )
        
        Slider(
            value = lightingIntensity,
            onValueChange = onLightingChanged,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = LocalColorScheme.current.primaryButton,
                activeTrackColor = LocalColorScheme.current.primaryButton,
                inactiveTrackColor = QuestThemeExtras.colors.secondary
            )
        )
        
        Icon(
            imageVector = Icons.Default.LightMode,
            contentDescription = "Bright",
            tint = LocalColorScheme.current.primaryButton,
            modifier = Modifier.size(22.dp)
        )
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
            style = QuestTypography.labelMedium,
            color = QuestThemeExtras.colors.secondaryText,
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
 * Environment selection card with image and text overlay.
 */
@Composable
private fun EnvironmentChip(
    environment: EnvironmentType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cardWidth = 140.dp
    val cardHeight = 100.dp
    
    val borderColor = if (isSelected) {
        LocalColorScheme.current.primaryButton
    } else {
        QuestThemeExtras.colors.secondary
    }
    
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        // Background image or fallback color
        environment.previewImage?.let { imageRes ->
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = environment.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .background(QuestThemeExtras.colors.secondary)
        )
        
        // Gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        
        // Selection overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalColorScheme.current.primaryButton.copy(alpha = 0.3f))
            )
        }
        
        // Text label at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = environment.displayName,
                style = QuestTypography.labelSmall,
                color = Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
	}
