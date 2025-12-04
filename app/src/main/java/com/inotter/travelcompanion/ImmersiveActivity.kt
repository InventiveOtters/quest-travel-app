package com.inotter.travelcompanion

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.core.Color4
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.datamodelinspector.DataModelInspectorFeature
import com.meta.spatial.debugtools.HotReloadFeature
import com.meta.spatial.okhttp3.OkHttpAssetFetcher
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.runtime.NetworkedAssetLoader
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Dome
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.ActivityPanelRegistration
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.MediaPanelRenderOptions
import com.meta.spatial.vr.VRFeature
import com.inotter.travelcompanion.spatial.SpatialConstants
import com.inotter.travelcompanion.spatial.TheatreViewModel
import com.inotter.travelcompanion.spatial.panels.LibraryPanelActivity
import com.inotter.travelcompanion.spatial.panels.ControlsPanelActivity
import java.io.File

/**
 * Immersive VR activity for Travel Companion.
 * Provides a spatial movie theatre experience with:
 * - Blue skybox environment
 * - Large theatre screen for video playback
 * - Control panel for playback controls
 * - Library panel for video selection
 */
class ImmersiveActivity : AppSystemActivity() {

    companion object {
        private const val TAG = "ImmersiveActivity"
    }

    // ExoPlayer instance for video playback
    private lateinit var exoPlayer: ExoPlayer

    // Theatre ViewModel manages the spatial experience
    private lateinit var theatreViewModel: TheatreViewModel

    override fun registerFeatures(): List<SpatialFeature> {
        val features = mutableListOf<SpatialFeature>(
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
        Log.d(TAG, "onCreate: Initializing immersive activity")

        // Initialize asset loader
        NetworkedAssetLoader.init(
            File(applicationContext.cacheDir.canonicalPath),
            OkHttpAssetFetcher(),
        )

        // Create ExoPlayer instance
        exoPlayer = ExoPlayer.Builder(this).build()

        // Create theatre ViewModel
        theatreViewModel = TheatreViewModel(exoPlayer, systemManager)
    }

    override fun onSceneReady() {
        super.onSceneReady()
        Log.d(TAG, "onSceneReady: Setting up spatial environment")

        // Configure theatre lighting - dimmed for cinematic experience
        scene.setLightingEnvironment(
            ambientColor = SpatialConstants.LIGHT_AMBIENT_COLOR,
            sunColor = SpatialConstants.LIGHT_SUN_COLOR,
            sunDirection = SpatialConstants.LIGHT_SUN_DIRECTION,
            environmentIntensity = 0.5f,
        )

        // Load environment IBL for reflections
        scene.updateIBLEnvironment("environment.env")

        // Set view origin
        scene.setViewOrigin(0.0f, 0.0f, 0.0f, 0.0f)

        // Create blue skybox dome
        createBlueSkybox()

        // Initialize theatre entities
        theatreViewModel.initializeEntities(scene)

        Log.d(TAG, "onSceneReady: Spatial environment setup complete")
    }

    /**
     * Creates a blue dome skybox for the theatre environment.
     */
    private fun createBlueSkybox() {
        Log.d(TAG, "Creating blue skybox dome")

        Entity.create(
            listOf(
                Mesh(Uri.parse("mesh://dome")),
                Dome(radius = 100f),  // Large dome surrounding the scene
                Material().apply {
                    // Deep blue color for cinematic theatre feel
                    baseColor = Color4(
                        red = 0.05f,
                        green = 0.08f,
                        blue = 0.20f,
                        alpha = 1.0f
                    )
                },
                Transform(Pose(Vector3(0f, 0f, 0f))),
            ),
        )
    }

    override fun registerPanels(): List<PanelRegistration> {
        Log.d(TAG, "Registering spatial panels")

        return listOf(
            // Library Panel - Main video browser UI
            createLibraryPanelRegistration(),

            // Theatre Screen Panel - Video playback surface
            createTheatreScreenRegistration(),

            // Controls Panel - Playback controls
            createControlsPanelRegistration(),
        )
    }

    /**
     * Creates the library panel registration for video browsing.
     * Uses ActivityPanelRegistration to support Hilt-injected ViewModels.
     */
    private fun createLibraryPanelRegistration(): PanelRegistration {
        return ActivityPanelRegistration(
            R.id.library_panel,
            classIdCreator = { LibraryPanelActivity::class.java },
            settingsCreator = {
                UIPanelSettings(
                    shape = QuadShapeOptions(
                        width = SpatialConstants.LIBRARY_PANEL_WIDTH,
                        height = SpatialConstants.LIBRARY_PANEL_HEIGHT
                    ),
                    style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
                    display = DpPerMeterDisplayOptions(
                        dpPerMeter = SpatialConstants.LIBRARY_PANEL_DP_PER_METER
                    ),
                )
            },
        )
    }

    /**
     * Creates the theatre screen registration for video playback.
     */
    private fun createTheatreScreenRegistration(): PanelRegistration {
        return VideoSurfacePanelRegistration(
            R.id.theatre_screen_panel,
            surfaceConsumer = { panelEntity, surface ->
                Log.d(TAG, "Theatre screen surface ready")
                // Paint black initially
                val canvas = surface.lockCanvas(null)
                canvas.drawColor(android.graphics.Color.BLACK)
                surface.unlockCanvasAndPost(canvas)

                // Connect ExoPlayer to surface
                exoPlayer.setVideoSurface(surface)
            },
            settingsCreator = {
                MediaPanelSettings(
                    shape = QuadShapeOptions(
                        width = SpatialConstants.SCREEN_WIDTH,
                        height = SpatialConstants.SCREEN_HEIGHT
                    ),
                    display = PixelDisplayOptions(
                        width = 1920,
                        height = 1080
                    ),
                    rendering = MediaPanelRenderOptions(
                        isDRM = false,
                        zIndex = 0
                    ),
                    style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent)
                )
            }
        )
    }

    /**
     * Creates the controls panel registration for playback controls.
     * Uses ActivityPanelRegistration for separate process.
     */
    private fun createControlsPanelRegistration(): PanelRegistration {
        return ActivityPanelRegistration(
            R.id.controls_panel,
            classIdCreator = { ControlsPanelActivity::class.java },
            settingsCreator = {
                UIPanelSettings(
                    shape = QuadShapeOptions(
                        width = SpatialConstants.CONTROLS_PANEL_WIDTH,
                        height = SpatialConstants.CONTROLS_PANEL_HEIGHT
                    ),
                    style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
                    display = DpPerMeterDisplayOptions(
                        dpPerMeter = SpatialConstants.CONTROLS_PANEL_DP_PER_METER
                    ),
                )
            },
        )
    }

    override fun onRecenter() {
        super.onRecenter()
        Log.d(TAG, "Recentering view")
        theatreViewModel.recenter()
    }

    override fun onHMDUnmounted() {
        super.onHMDUnmounted()
        Log.d(TAG, "HMD unmounted - pausing playback")
        theatreViewModel.onPause()
    }

    override fun onHMDMounted() {
        super.onHMDMounted()
        Log.d(TAG, "HMD mounted - resuming")
        theatreViewModel.onResume()
    }

    override fun onVRPause() {
        super.onVRPause()
        theatreViewModel.onPause()
    }

    override fun onVRReady() {
        super.onVRReady()
        theatreViewModel.onResume()
    }

    override fun onSpatialShutdown() {
        Log.d(TAG, "Spatial shutdown - cleaning up")
        theatreViewModel.destroy()
        exoPlayer.release()
        super.onSpatialShutdown()
    }
}
