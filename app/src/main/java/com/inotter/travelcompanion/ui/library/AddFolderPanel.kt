package com.inotter.travelcompanion.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Panel for adding a library folder using SAF (Storage Access Framework).
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
      modifier = modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
  ) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Text(
          text = "Add Video Library Folder",
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.padding(bottom = 16.dp),
      )

      Text(
          text = "Select a folder containing your VR videos",
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(bottom = 32.dp),
      )

      Button(
          onClick = { launcher.launch(null) },
          modifier = Modifier.padding(8.dp),
      ) {
        Text("Choose Folder")
      }
    }
  }
}

