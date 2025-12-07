package com.inotter.travelcompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.meta.spatial.uiset.theme.SpatialColorScheme
import com.meta.spatial.uiset.theme.SpatialTheme
import com.meta.spatial.uiset.theme.darkSpatialColorScheme
import com.meta.spatial.uiset.theme.lightSpatialColorScheme

/**
 * Quest-native color palette following Meta Horizon OS design guidelines.
 * Avoids pure white (#FFFFFF) and pure black (#000000) for VR comfort.
 * Maintains 4.5:1 contrast ratio for text, 3:1 for non-text elements.
 */
object QuestColors {
    // Background colors - avoiding pure white/black
    val backgroundDark = Color(0xFF0D0D0F)      // Near-black, not pure black
    val backgroundLight = Color(0xFFDADADA)     // Max brightness per Meta guidelines
    val surfaceDark = Color(0xFF1A1A1E)         // Elevated surface
    val surfaceLight = Color(0xFFC8C8CC)        // Light mode surface
    
    // Container colors
    val surfaceContainerDark = Color(0xFF252529)
    val surfaceContainerLight = Color(0xFFB8B8BC)
    
    // Primary accent - Meta blue tones
    val primaryDark = Color(0xFF6B8AFF)         // Lighter blue for dark mode
    val primaryLight = Color(0xFF3B5BDB)        // Darker blue for light mode
    val primaryContainerDark = Color(0xFF1E3A5F)
    val primaryContainerLight = Color(0xFFD0DBFF)
    
    // Text colors with proper contrast
    val onBackgroundDark = Color(0xFFE8E8EC)    // Off-white for dark mode
    val onBackgroundLight = Color(0xFF1A1A1E)   // Near-black for light mode
    val onSurfaceDark = Color(0xFFE8E8EC)
    val onSurfaceLight = Color(0xFF1A1A1E)
    val onSurfaceVariantDark = Color(0xFFA8A8AC)
    val onSurfaceVariantLight = Color(0xFF505054)
    
    // Success/Error/Warning
    val success = Color(0xFF4ADE80)
    val error = Color(0xFFF87171)
    val warning = Color(0xFFFBBF24)
    
    // Divider and outline
    val outlineDark = Color(0xFF3A3A3E)
    val outlineLight = Color(0xFF9A9A9E)
    
    // Hover and focus states
    val hoverOverlayDark = Color(0x1AFFFFFF)    // 10% white overlay
    val hoverOverlayLight = Color(0x1A000000)   // 10% black overlay
    val focusBorderDark = Color(0xFF6B8AFF)
    val focusBorderLight = Color(0xFF3B5BDB)
}

/**
 * Quest-specific UI dimensions following Meta Horizon OS design requirements.
 * Hit targets: 48dp minimum, 60dp recommended for primary actions.
 */
object QuestDimensions {
    const val MinHitTarget = 48
    const val RecommendedHitTarget = 60
    const val IconSize = 24
    const val IconHitSlop = 12  // Extra padding around icons
    const val CardMinHeight = 200
    const val GridCellMinWidth = 240
    const val ButtonHeight = 60
    const val SmallButtonHeight = 48
    const val CardCornerRadius = 16
    const val SectionSpacing = 24
    const val ItemSpacing = 16
    const val ContentPadding = 20
}

/**
 * Local composition for Quest-specific colors that extend beyond SpatialTheme.
 * Note: Primary button text color now uses LocalColorScheme.current.primaryOpaqueButton
 * for proper contrast as per Meta Horizon OS UI Set guidelines.
 */
data class QuestExtendedColors(
    val success: Color,
    val warning: Color,
    val hoverOverlay: Color,
    val focusBorder: Color,
    val isDark: Boolean,
    // Additional colors for text and surfaces
    val secondary: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val secondaryButtonText: Color
)

val LocalQuestColors = staticCompositionLocalOf {
    QuestExtendedColors(
        success = QuestColors.success,
        warning = QuestColors.warning,
        hoverOverlay = QuestColors.hoverOverlayDark,
        focusBorder = QuestColors.focusBorderDark,
        isDark = true,
        secondary = QuestColors.surfaceDark,
        primaryText = QuestColors.onSurfaceDark,
        secondaryText = QuestColors.onSurfaceVariantDark,
        secondaryButtonText = QuestColors.onSurfaceDark
    )
}

/**
 * Returns the appropriate SpatialColorScheme based on system dark mode.
 */
@Composable
fun getQuestColorScheme(): SpatialColorScheme =
    if (isSystemInDarkTheme()) darkSpatialColorScheme() else lightSpatialColorScheme()

/**
 * Main Quest theme wrapper that applies Meta Spatial UI Set styling.
 * Use this instead of MaterialTheme for Quest-native look and feel.
 */
@Composable
fun QuestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkSpatialColorScheme() else lightSpatialColorScheme()

    val extendedColors = QuestExtendedColors(
        success = QuestColors.success,
        warning = QuestColors.warning,
        hoverOverlay = if (darkTheme) QuestColors.hoverOverlayDark else QuestColors.hoverOverlayLight,
        focusBorder = if (darkTheme) QuestColors.focusBorderDark else QuestColors.focusBorderLight,
        isDark = darkTheme,
        secondary = if (darkTheme) QuestColors.surfaceDark else QuestColors.surfaceLight,
        primaryText = if (darkTheme) QuestColors.onSurfaceDark else QuestColors.onSurfaceLight,
        secondaryText = if (darkTheme) QuestColors.onSurfaceVariantDark else QuestColors.onSurfaceVariantLight,
        secondaryButtonText = if (darkTheme) QuestColors.onSurfaceDark else QuestColors.onSurfaceLight
    )

    CompositionLocalProvider(LocalQuestColors provides extendedColors) {
        SpatialTheme(colorScheme = colorScheme) {
            content()
        }
    }
}

/**
 * Access extended Quest colors from composition.
 */
object QuestThemeExtras {
    val colors: QuestExtendedColors
        @Composable
        get() = LocalQuestColors.current
}

