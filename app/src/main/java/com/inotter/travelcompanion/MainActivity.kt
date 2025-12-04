package com.inotter.travelcompanion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.meta.spatial.okhttp3.OkHttpAssetFetcher
import com.meta.spatial.runtime.NetworkedAssetLoader
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * Main 2D panel activity for Travel Companion app.
 * This activity displays the app as a 2D panel in Meta Quest Home.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    Log.d("MainActivity", "Creating 2D panel activity")
    
    // Initialize asset loader for any networked assets
    NetworkedAssetLoader.init(
        File(applicationContext.cacheDir.canonicalPath),
        OkHttpAssetFetcher(),
    )
    
    // Set up Compose UI
    setContent {
      MaterialTheme {
        // Display the VR Video Library UI in 2D panel mode
        com.inotter.travelcompanion.ui.VRNavigationHost()
      }
    }
  }
}

