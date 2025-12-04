package com.inotter.travelcompanion.spatial.panels

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import dagger.hilt.android.AndroidEntryPoint
import com.inotter.travelcompanion.ui.VRNavigationHost

/**
 * Separate activity for the Library Panel.
 * This activity is Hilt-compatible and can use injected ViewModels.
 * It runs in its own process to avoid interference with the main VR activity.
 */
@AndroidEntryPoint
class LibraryPanelActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                VRNavigationHost()
            }
        }
    }
}
