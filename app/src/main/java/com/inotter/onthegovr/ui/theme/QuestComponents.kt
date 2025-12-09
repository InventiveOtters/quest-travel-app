package com.inotter.onthegovr.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meta.spatial.uiset.theme.LocalColorScheme
import com.meta.spatial.uiset.theme.SpatialTheme

/**
 * Quest-native card with hover and focus states.
 * Provides visual feedback for controller and hand tracking interactions.
 */
@Composable
fun QuestCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    minHeight: Dp = QuestDimensions.CardMinHeight.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.98f
            isHovered || isFocused -> 1.02f
            else -> 1f
        },
        animationSpec = tween(150),
        label = "card_scale"
    )
    
    val borderColor = when {
        selected -> LocalColorScheme.current.primaryButton
        isFocused -> QuestThemeExtras.colors.focusBorder
        isHovered -> QuestThemeExtras.colors.focusBorder.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    
    val backgroundColor = when {
        isHovered || isFocused -> LocalColorScheme.current.hover
        else -> QuestThemeExtras.colors.secondary
    }
    
    Surface(
        modifier = modifier
            .scale(scale)
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(QuestDimensions.CardCornerRadius.dp))
            .border(
                width = if (selected || isFocused || isHovered) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp)
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(QuestDimensions.ContentPadding.dp),
            content = content
        )
    }
}

/**
 * Quest-styled primary button with proper 60dp hit target.
 * @param compact If true, uses 48dp height to match secondary buttons when placed side-by-side
 */
@Composable
fun QuestPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expanded: Boolean = false,
    compact: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.97f
            isHovered -> 1.02f
            else -> 1f
        },
        animationSpec = tween(100),
        label = "button_scale"
    )

    val backgroundColor = when {
        !enabled -> LocalColorScheme.current.primaryButton.copy(alpha = 0.5f)
        isHovered -> LocalColorScheme.current.hover
        else -> LocalColorScheme.current.primaryButton
    }

    val buttonHeight = if (compact) QuestDimensions.SmallButtonHeight.dp else QuestDimensions.ButtonHeight.dp

    Surface(
        modifier = modifier
            .scale(scale)
            .height(buttonHeight)
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .clip(SpatialTheme.shapes.medium)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        shape = SpatialTheme.shapes.medium,
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier
                .then(if (expanded) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                .padding(horizontal = if (compact) 20.dp else 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = if (compact) QuestTypography.labelLarge else QuestTypography.button,
                color = if (enabled) LocalColorScheme.current.primaryOpaqueButton
                       else LocalColorScheme.current.primaryOpaqueButton.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Quest-styled secondary button with proper hit target.
 */
@Composable
fun QuestSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expanded: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.97f
            isHovered -> 1.02f
            else -> 1f
        },
        animationSpec = tween(100),
        label = "secondary_button_scale"
    )

    val borderColor = when {
        !enabled -> LocalColorScheme.current.secondaryButton.copy(alpha = 0.5f)
        isHovered -> LocalColorScheme.current.hover
        else -> LocalColorScheme.current.secondaryButton
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .height(QuestDimensions.SmallButtonHeight.dp)
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .clip(SpatialTheme.shapes.medium)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = SpatialTheme.shapes.medium
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        shape = SpatialTheme.shapes.medium,
        color = if (isHovered) LocalColorScheme.current.hover.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .then(if (expanded) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = QuestTypography.labelLarge,
                color = if (enabled) QuestThemeExtras.colors.secondaryButtonText
                       else QuestThemeExtras.colors.secondaryButtonText.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Quest-styled text/tertiary button.
 */
@Composable
fun QuestTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val textColor = when {
        !enabled -> LocalColorScheme.current.primaryButton.copy(alpha = 0.5f)
        isHovered -> LocalColorScheme.current.hover
        else -> LocalColorScheme.current.primaryButton
    }

    Box(
        modifier = modifier
            .heightIn(min = QuestDimensions.SmallButtonHeight.dp)
            .clip(SpatialTheme.shapes.small)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = QuestTypography.labelLarge,
            color = textColor
        )
    }
}

/**
 * Quest-styled selectable card for radio-button-like selection.
 */
@Composable
fun QuestSelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isHovered || isFocused -> 1.01f
            else -> 1f
        },
        animationSpec = tween(150),
        label = "selectable_scale"
    )

    val borderColor = when {
        selected -> LocalColorScheme.current.primaryButton
        isFocused || isHovered -> QuestThemeExtras.colors.focusBorder.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val backgroundColor = when {
        selected -> LocalColorScheme.current.primaryButton.copy(alpha = 0.15f)
        isHovered || isFocused -> LocalColorScheme.current.hover.copy(alpha = 0.1f)
        else -> QuestThemeExtras.colors.secondary
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(QuestDimensions.CardCornerRadius.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) borderColor else QuestThemeExtras.colors.secondary,
                shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp)
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(QuestDimensions.ContentPadding.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp),
            content = content
        )
    }
}

/**
 * Quest-styled header/title section.
 */
@Composable
fun QuestSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = QuestTypography.headlineMedium,
        color = QuestThemeExtras.colors.primaryText,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

/**
 * Quest-styled divider.
 */
@Composable
fun QuestDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(QuestThemeExtras.colors.secondary.copy(alpha = 0.3f))
    )
}

/**
 * Quest-styled icon wrapper with proper hit target.
 */
@Composable
fun QuestIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.9f
            isHovered -> 1.1f
            else -> 1f
        },
        animationSpec = tween(100),
        label = "icon_scale"
    )

    Box(
        modifier = modifier
            .size(QuestDimensions.MinHitTarget.dp)
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(
                if (isHovered) LocalColorScheme.current.hover.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Quest-styled info/explanation card.
 */
@Composable
fun QuestInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(QuestDimensions.CardCornerRadius.dp)),
        shape = RoundedCornerShape(QuestDimensions.CardCornerRadius.dp),
        color = LocalColorScheme.current.primaryButton.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(QuestDimensions.ContentPadding.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * Quest-styled loading/progress content.
 */
@Composable
fun QuestLoadingContent(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp)
    ) {
        // Simple animated loading indicator using composition
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 6.dp,
            color = LocalColorScheme.current.primaryButton
        )

        Text(
            text = message,
            style = QuestTypography.headlineSmall,
            color = QuestThemeExtras.colors.primaryText,
            textAlign = TextAlign.Center
        )

        detail?.let {
            Text(
                text = it,
                style = QuestTypography.bodyMedium,
                color = QuestThemeExtras.colors.secondaryText,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Quest-styled empty state content.
 */
@Composable
fun QuestEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(QuestDimensions.SectionSpacing.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp)
    ) {
        Text(
            text = title,
            style = QuestTypography.headlineSmall,
            color = QuestThemeExtras.colors.primaryText,
            textAlign = TextAlign.Center
        )

        Text(
            text = description,
            style = QuestTypography.bodyMedium,
            color = QuestThemeExtras.colors.secondaryText,
            textAlign = TextAlign.Center
        )

        action?.let {
            Spacer(modifier = Modifier.height(8.dp))
            it()
        }
    }
}

