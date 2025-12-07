package com.inotter.travelcompanion.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inotter.travelcompanion.ui.theme.QuestDimensions
import com.inotter.travelcompanion.ui.theme.QuestInfoCard
import com.inotter.travelcompanion.ui.theme.QuestPrimaryButton
import com.inotter.travelcompanion.ui.theme.QuestThemeExtras
import com.inotter.travelcompanion.ui.theme.QuestTypography
import com.meta.spatial.uiset.theme.LocalColorScheme

/**
 * Quest-styled panel for adding a library folder using SAF (Storage Access Framework).
 * Launches ACTION_OPEN_DOCUMENT_TREE to select a folder.
 */
@Composable
fun AddFolderPanel(
    viewModel: LibraryViewModel,
    onFolderAdded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                val data = Intent().apply { data = uri }
                viewModel.addFolder(data)
                onFolderAdded()
            }
        }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(brush = LocalColorScheme.current.panel),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(QuestDimensions.SectionSpacing.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Folder icon
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(QuestThemeExtras.colors.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📁",
                    style = QuestTypography.displayMedium,
                )
            }

            Spacer(modifier = Modifier.height(QuestDimensions.SectionSpacing.dp))

            Text(
                text = "Add Video Library Folder",
                style = QuestTypography.headlineMedium,
                color = QuestThemeExtras.colors.primaryText,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(QuestDimensions.ItemSpacing.dp))

            Text(
                text = "Select a folder containing your VR videos",
                style = QuestTypography.bodyLarge,
                color = QuestThemeExtras.colors.secondaryText,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(QuestDimensions.SectionSpacing.dp))

            // Info card with tips
            QuestInfoCard(
                modifier = Modifier.widthIn(max = 500.dp)
            ) {
                Text(
                    text = "Tip",
                    style = QuestTypography.titleSmall,
                    color = QuestThemeExtras.colors.primaryText,
                )
                Text(
                    text = "Choose folders in Movies, Downloads, or DCIM for best results. The app will find all video files inside.",
                    style = QuestTypography.bodyMedium,
                    color = QuestThemeExtras.colors.secondaryText,
                )
            }

            Spacer(modifier = Modifier.height(QuestDimensions.SectionSpacing.dp))

            QuestPrimaryButton(
                text = "Choose Folder",
                onClick = { launcher.launch(null) },
            )
        }
    }
}

