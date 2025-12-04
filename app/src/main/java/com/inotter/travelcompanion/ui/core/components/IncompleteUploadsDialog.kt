package com.inotter.travelcompanion.ui.core.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inotter.travelcompanion.data.managers.TransferManager.FileValidator
import com.inotter.travelcompanion.data.managers.TransferManager.models.IncompleteUpload
import com.inotter.travelcompanion.data.managers.TransferManager.models.OrphanedMediaStoreEntry

/**
 * Dialog shown on app startup when incomplete uploads are detected.
 * Offers options to clean up storage or continue uploading via WiFi transfer.
 *
 * @param incompleteUploads List of incomplete uploads with database records (resumable)
 * @param orphanedEntries List of orphaned MediaStore entries without database records (cleanup only)
 * @param onCleanUp Callback when user chooses to clean up storage
 * @param onContinueUploading Callback when user chooses to continue uploading (navigate to WiFi transfer)
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun IncompleteUploadsDialog(
    incompleteUploads: List<IncompleteUpload>,
    orphanedEntries: List<OrphanedMediaStoreEntry> = emptyList(),
    onCleanUp: () -> Unit,
    onContinueUploading: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalCount = incompleteUploads.size + orphanedEntries.size
    val totalStorageUsed = remember(incompleteUploads, orphanedEntries) {
        incompleteUploads.sumOf { it.currentSize } + orphanedEntries.sumOf { it.currentSize }
    }
    val hasResumable = incompleteUploads.any { it.canResume }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⏸️", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Incomplete Uploads",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "You have $totalCount incomplete upload${if (totalCount > 1) "s" else ""} " +
                    "using ${FileValidator.formatBytes(totalStorageUsed)} of storage.",
                    style = MaterialTheme.typography.bodyMedium
                )

                // List of incomplete uploads and orphaned entries
                if (incompleteUploads.isNotEmpty() || orphanedEntries.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Resumable uploads first
                        items(incompleteUploads) { upload ->
                            IncompleteUploadItem(upload)
                        }
                        // Orphaned entries (cleanup only)
                        items(orphanedEntries) { entry ->
                            OrphanedEntryItem(entry)
                        }
                    }
                }

                Text(
                    if (hasResumable) {
                        "You can clean up the storage to remove these incomplete files, " +
                        "or continue uploading to resume where you left off."
                    } else {
                        "These files cannot be resumed. Clean up to free storage space."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (hasResumable) {
                Button(
                    onClick = onContinueUploading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Continue Uploading")
                }
            } else {
                Button(
                    onClick = onCleanUp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Clean Up Storage")
                }
            }
        },
        dismissButton = {
            if (hasResumable) {
                OutlinedButton(onClick = onCleanUp) {
                    Text("Clean Up Storage")
                }
            } else {
                OutlinedButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    )
}

@Composable
private fun IncompleteUploadItem(upload: IncompleteUpload) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = upload.session.filename,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${upload.receivedText} / ${upload.sizeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = upload.progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            LinearProgressIndicator(
                progress = { upload.session.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun OrphanedEntryItem(entry: OrphanedMediaStoreEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.sizeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Cannot resume",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

