package com.inotter.onthegovr

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.inotter.onthegovr.data.models.ViewingMode
import com.inotter.onthegovr.ui.theme.QuestTheme
import com.inotter.onthegovr.ui.VRNavigationHost
import com.inotter.onthegovr.ui.onboarding.OnboardingViewModel
import com.meta.spatial.okhttp3.OkHttpAssetFetcher
import com.meta.spatial.runtime.NetworkedAssetLoader
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * Main 2D panel activity for OnTheGoVR app.
 * This is the single entry point (launcher) for the app.
 *
 * On launch:
 * - If onboarding not complete → Show onboarding flow
 * - If onboarding complete and preference is Immersive → Launch ImmersiveActivity
 * - If onboarding complete and preference is 2D Panel → Show library
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  companion object {
    private const val TAG = "MainActivity"
  }

  private val prefs: SharedPreferences by lazy {
    getSharedPreferences(OnboardingViewModel.PREFS_NAME, MODE_PRIVATE)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Log.d(TAG, "Creating 2D panel activity")

    // Initialize asset loader for any networked assets
    NetworkedAssetLoader.init(
        File(applicationContext.cacheDir.canonicalPath),
        OkHttpAssetFetcher(),
    )

    // Check if we should immediately launch immersive mode
    if (shouldLaunchImmersive()) {
      Log.d(TAG, "Preference is Immersive, launching ImmersiveActivity")
      launchImmersiveMode()
      return
    }

    // Set up Compose UI for 2D panel mode with Quest-native theming
    setContent {
      QuestTheme {
        VRNavigationHost(
            onLaunchImmersive = { launchImmersiveMode() },
            onLaunchPanel = { /* Already in panel mode */ }
        )
      }
    }
  }

  /**
   * Check if we should immediately launch immersive mode.
   * Returns true if onboarding is complete AND preference is IMMERSIVE.
   */
  private fun shouldLaunchImmersive(): Boolean {
    val onboardingComplete = prefs.getBoolean("onboarding_complete", false)
    val modeString = prefs.getString(OnboardingViewModel.KEY_VIEWING_MODE, null)
    val viewingMode = ViewingMode.fromString(modeString)

    Log.d(TAG, "Checking launch mode: onboardingComplete=$onboardingComplete, viewingMode=$viewingMode")

    return onboardingComplete && viewingMode == ViewingMode.IMMERSIVE
  }

  /**
   * Launch ImmersiveActivity and close this activity.
   * Used when user selects Immersive mode during onboarding or from settings.
   */
  private fun launchImmersiveMode() {
    Log.d(TAG, "Launching immersive mode")
    val immersiveIntent = Intent(this, ImmersiveActivity::class.java).apply {
      action = Intent.ACTION_MAIN
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    startActivity(immersiveIntent)
    finishAndRemoveTask()
  }
}

