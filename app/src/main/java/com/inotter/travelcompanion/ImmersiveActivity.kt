package com.inotter.travelcompanion

import android.os.Bundle
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.datamodelinspector.DataModelInspectorFeature
import com.meta.spatial.debugtools.HotReloadFeature
import com.meta.spatial.okhttp3.OkHttpAssetFetcher
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.runtime.NetworkedAssetLoader
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.vr.VRFeature
import java.io.File

/**
 * Immersive VR activity for Travel Companion.
 * Extends Meta Spatial SDK's AppSystemActivity for VR functionality.
 *
 * Note: Cannot use @AndroidEntryPoint because AppSystemActivity is not a subclass
 * of ComponentActivity. Hilt injection is handled through the Compose ViewModels.
 */
class ImmersiveActivity : AppSystemActivity() {

  override fun registerFeatures(): List<SpatialFeature> {
    val features =
        mutableListOf<SpatialFeature>(
            VRFeature(this),
            ComposeFeature(),
        )
    if (BuildConfig.DEBUG) {
      features.add(CastInputForwardFeature(this))
      features.add(HotReloadFeature(this))
      features.add(OVRMetricsFeature(this, OVRMetricsDataModel() { numberOfMeshes() }))
      features.add(DataModelInspectorFeature(spatial, this.componentManager))
    }
    return features
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NetworkedAssetLoader.init(
        File(applicationContext.getCacheDir().canonicalPath),
        OkHttpAssetFetcher(),
    )
  }

  override fun onSceneReady() {
    super.onSceneReady()

    // Bright lighting setup for 2D panel visibility
    scene.setLightingEnvironment(
        ambientColor = Vector3(1.0f, 1.0f, 1.0f),
        sunColor = Vector3(1.0f, 1.0f, 1.0f),
        sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
        environmentIntensity = 1.0f,
    )
    scene.updateIBLEnvironment("environment.env")

    scene.setViewOrigin(0.0f, 0.0f, 0.0f, 0.0f)

    // Simple 2D panel experience - no skybox or custom scene needed
  }

  override fun registerPanels(): List<PanelRegistration> {
    Log.d("ImmersiveActivity", "Registering panels")
    return listOf(
        // VR Video Library panel - Travel Companion main UI
        ComposeViewPanelRegistration(
            R.id.vr_video_library_panel,
            composeViewCreator = { _, context ->
              Log.d("ImmersiveActivity", "Creating VR Video Library panel")
              ComposeView(context).apply {
                setContent {
                  Log.d("ImmersiveActivity", "Setting up VR Navigation Host")
                  MaterialTheme {
                    // Enable the actual video library UI
                    com.inotter.travelcompanion.ui.VRNavigationHost()
                  }
                }
              }
            },
            settingsCreator = {
              UIPanelSettings(
                  shape = QuadShapeOptions(width = 2.0f, height = 1.5f),
                  style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
                  display = DpPerMeterDisplayOptions(),
              )
            },
        ),
    )
  }

  override fun onSpatialShutdown() {
    super.onSpatialShutdown()
  }
}
