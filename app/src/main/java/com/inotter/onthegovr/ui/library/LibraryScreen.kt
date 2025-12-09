package com.inotter.onthegovr.ui.library

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inotter.onthegovr.data.datasources.videolibrary.models.SourceType
import com.inotter.onthegovr.data.datasources.videolibrary.models.VideoItem
import com.inotter.onthegovr.ui.theme.QuestCard
import com.inotter.onthegovr.ui.theme.QuestColors
import com.inotter.onthegovr.ui.theme.QuestDimensions
import com.inotter.onthegovr.ui.theme.QuestDivider
import com.inotter.onthegovr.ui.theme.QuestPrimaryButton
import com.inotter.onthegovr.ui.theme.QuestSecondaryButton
import com.inotter.onthegovr.ui.theme.QuestThemeExtras
import com.inotter.onthegovr.ui.theme.QuestTypography
import com.meta.spatial.uiset.theme.LocalColorScheme
import java.io.File

/**
 * Library screen displaying a grid of videos with thumbnails.
 * Quest-native design with proper hit targets, hover states, and VR-optimized styling.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onVideoSelected: (VideoItem) -> Unit,
    onAddFolder: () -> Unit,
    onManageSources: () -> Unit = {},
    onSettings: () -> Unit = {},
    onWifiTransfer: () -> Unit = {},
    onSync: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val videos by viewModel.videos.collectAsState()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(brush = LocalColorScheme.current.panel),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Quest-styled buttons
            LibraryHeader(
                onSettings = onSettings,
                onWifiTransfer = onWifiTransfer,
                onSync = onSync,
            )

            QuestDivider()

            // Video grid
            if (videos.isEmpty()) {
                EmptyLibraryContent(
                    onAddFolder = onAddFolder,
                    onManageSources = onManageSources,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = QuestDimensions.GridCellMinWidth.dp),
                    contentPadding = PaddingValues(QuestDimensions.ContentPadding.dp),
                    horizontalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp),
                    verticalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp),
                ) {
                    items(videos.sortedBy { it.title }) { video ->
                        VideoCard(video = video, onClick = { onVideoSelected(video) })
                    }
                }
            }
        }
    }
}

/**
 * Quest-styled header with proper hit targets and spacing.
 * Following Meta Horizon OS design guidelines:
 * - Primary button for the main action (Watch Together)
 * - Secondary buttons for supporting actions (Transfer Videos, Settings)
 * - Maximum 3 buttons to avoid clutter and maintain VR usability
 */
@Composable
private fun LibraryHeader(
    onSettings: () -> Unit,
    onWifiTransfer: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(QuestDimensions.ContentPadding.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "OnTheGoVR",
            style = QuestTypography.headlineMedium,
            color = QuestThemeExtras.colors.primaryText,
        )

        // Wrap buttons in a Row with intrinsic size to prevent stretching
        // Following Meta Horizon guidelines: Primary action first, then secondary actions
        // Using compact=true to match the visual size of secondary buttons when side-by-side
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuestPrimaryButton(text = "Watch Together", onClick = onSync, compact = true)
            QuestSecondaryButton(text = "Transfer Videos", onClick = onWifiTransfer)
            QuestSecondaryButton(text = "Settings", onClick = onSettings)
        }
    }
}

/**
 * Quest-styled empty state with proper typography and buttons.
 */
@Composable
private fun EmptyLibraryContent(
    onAddFolder: () -> Unit,
    onManageSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(QuestDimensions.ItemSpacing.dp),
            modifier = Modifier
                .padding(QuestDimensions.SectionSpacing.dp)
                .widthIn(max = 500.dp),
        ) {
            // Icon placeholder - could be replaced with proper icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuestThemeExtras.colors.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎬",
                    style = QuestTypography.displayMedium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No Videos Found",
                style = QuestTypography.headlineMedium,
                color = QuestThemeExtras.colors.primaryText,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "Add videos to your library by enabling auto-scan or selecting folders manually.",
                style = QuestTypography.bodyMedium,
                color = QuestThemeExtras.colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            QuestPrimaryButton(
                text = "Enable Auto-Scan",
                onClick = onManageSources,
                expanded = true,
            )

            QuestSecondaryButton(
                text = "Add Folder Manually",
                onClick = onAddFolder,
                expanded = true,
            )
        }
    }
}

/**
 * Quest-styled video card with hover states and proper hit targets.
 */
@Composable
private fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.03f else 1f,
        animationSpec = tween(150),
        label = "video_card_scale"
    )

    val borderColor = if (isHovered) {
        LocalColorScheme.current.primaryButton.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }

    QuestCard(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .hoverable(interactionSource),
        minHeight = QuestDimensions.CardMinHeight.dp,
    ) {
        // Thumbnail area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(QuestThemeExtras.colors.secondary),
            contentAlignment = Alignment.Center,
        ) {
            if (video.thumbnailPath != null && File(video.thumbnailPath).exists()) {
                val bitmap = BitmapFactory.decodeFile(video.thumbnailPath)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Video thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    VideoPlaceholderIcon()
                }
            } else {
                VideoPlaceholderIcon()
            }

            // Status badges overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Source type badge
                if (video.sourceType == SourceType.MEDIASTORE) {
                    VideoBadge(text = "Auto", isError = false)
                }

                // Unavailable badge
                if (video.unavailable) {
                    VideoBadge(text = "Unavailable", isError = true)
                }
            }

            // Duration badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = formatDuration(video.durationMs),
                    style = QuestTypography.labelSmall,
                    color = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Video info
        Text(
            text = video.title,
            style = QuestTypography.titleSmall,
            color = QuestThemeExtras.colors.primaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Placeholder icon for videos without thumbnails.
 */
@Composable
private fun VideoPlaceholderIcon() {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LocalColorScheme.current.hover.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🎬",
            style = QuestTypography.headlineMedium,
        )
    }
}

/**
 * Quest-styled status badge.
 * Uses proper contrast colors following Meta Horizon OS UI Set guidelines.
 */
@Composable
private fun VideoBadge(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isError) {
        QuestColors.error.copy(alpha = 0.9f)
    } else {
        LocalColorScheme.current.primaryButton.copy(alpha = 0.9f)
    }

    val textColor = if (isError) {
        Color.White  // White text on red error background has good contrast
    } else {
        LocalColorScheme.current.primaryOpaqueButton  // Proper contrast for primary button
    }

    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = QuestTypography.labelSmall,
            color = textColor,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}

