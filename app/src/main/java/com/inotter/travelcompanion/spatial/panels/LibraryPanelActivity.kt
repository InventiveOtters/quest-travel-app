package com.inotter.travelcompanion.spatial.panels

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.inotter.travelcompanion.ui.VRNavigationHost
import com.inotter.travelcompanion.ui.theme.QuestTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Separate activity for the Library Panel.
 * This activity is Hilt-compatible and can use injected ViewModels.
 * It runs in its own process to avoid interference with the main VR activity.
 * Uses Quest-native theming for proper VR look and feel.
 */
@AndroidEntryPoint
class LibraryPanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QuestTheme {
                VRNavigationHost()
            }
        }
    }
}
