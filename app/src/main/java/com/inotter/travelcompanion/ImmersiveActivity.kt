package com.inotter.travelcompanion

import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.exoplayer.ExoPlayer
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.datamodelinspector.DataModelInspectorFeature
import com.meta.spatial.debugtools.HotReloadFeature
import com.meta.spatial.okhttp3.OkHttpAssetFetcher
import com.meta.spatial.ovrmetrics.OVRMetricsDataModel
import com.meta.spatial.ovrmetrics.OVRMetricsFeature
import com.meta.spatial.physics.PhysicsFeature
import com.meta.spatial.physics.PhysicsWorldBounds
import com.meta.spatial.runtime.NetworkedAssetLoader
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.runtime.SceneMaterial
import com.meta.spatial.toolkit.ActivityPanelRegistration
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.GLXFInfo
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.vr.VRFeature
import com.meta.spatial.vr.VrInputSystemType
import com.inotter.travelcompanion.spatial.SpatialConstants
import com.inotter.travelcompanion.spatial.TheatreViewModel
import com.inotter.travelcompanion.spatial.panels.ControlsPanelActivity
import com.inotter.travelcompanion.spatial.panels.LibraryPanelActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    // Coroutine scope for async operations
    private val activityScope = CoroutineScope(Dispatchers.Main)

    // GLXF entity for the loaded scene composition
    private var gltfxEntity: Entity? = null

    // Skybox entity for the environment dome
    private var skybox: Entity? = null

    // ExoPlayer instance for video playback
    private lateinit var exoPlayer: ExoPlayer

    // Theatre ViewModel manages the spatial experience
    private lateinit var theatreViewModel: TheatreViewModel

    // Track GLXF composition for deferred initialization
    private var glxfComposition: GLXFInfo? = null
    private var sceneReady = false
    private var theatreInitialized = false

    override fun registerFeatures(): List<SpatialFeature> {
        val features = mutableListOf<SpatialFeature>(
            PhysicsFeature(spatial, worldBounds = PhysicsWorldBounds(minY = -100.0f)),
            VRFeature(this, inputSystemType = VrInputSystemType.SIMPLE_CONTROLLER),
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

        loadGLXF { composition ->
          // Set the environment to unlit
          val environmentEntity: Entity? = composition.getNodeByName("Environment").entity
          val environmentMesh = environmentEntity?.getComponent<Mesh>()
          environmentMesh?.defaultShaderOverride = SceneMaterial.UNLIT_SHADER
          environmentEntity?.setComponent(environmentMesh!!)

          // Store composition for initialization
          glxfComposition = composition
          Log.d(TAG, "GLXF loaded")

          // Initialize theatre if scene is ready (handles race condition)
          if (sceneReady) {
              initializeTheatreFromGLXF(composition)
          }
        }

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
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)

        scene.setLightingEnvironment(
            ambientColor = Vector3(0f),
            sunColor = Vector3(7.0f, 7.0f, 7.0f),
            sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
            environmentIntensity = 0.3f,
        )

        // Load environment IBL for reflections
        scene.updateIBLEnvironment("environment.env")

        // Set view origin
        scene.setViewOrigin(0.0f, 0.0f, 0.0f, 0.0f)

        skybox =
        Entity.create(
            listOf(
                Mesh("mesh://skybox".toUri(), hittable = MeshCollision.NoCollision),
                Material().apply {
                  baseTextureAndroidResourceId = R.drawable.skydome
                  unlit = true
                },
                Transform(Pose(Vector3(x = 0f, y = 0f, z = 0f))),
            )
        )

        // Mark scene as ready
        sceneReady = true

        // Initialize theatre if GLXF is already loaded (handles race condition)
        glxfComposition?.let { composition ->
            initializeTheatreFromGLXF(composition)
        }

        Log.d(TAG, "onSceneReady: Spatial environment setup complete")
    }

    /**
     * Initialize theatre entities from GLXF composition.
     * Called when both scene is ready AND GLXF is loaded.
     */
    private fun initializeTheatreFromGLXF(composition: GLXFInfo) {
        if (theatreInitialized) {
            Log.d(TAG, "Theatre already initialized, skipping")
            return
        }
        theatreInitialized = true
        
        val libraryPanel = composition.getNodeByName("VRVideoLibraryPanel").entity
        val controlsPanel = composition.getNodeByName("ControlsPanel").entity
        Log.d(TAG, "Initializing theatre - library: $libraryPanel, controls: $controlsPanel")
        theatreViewModel.initializeEntities(scene, libraryPanel, controlsPanel)
    }

    private fun loadGLXF(onLoaded: ((GLXFInfo) -> Unit) = {}): Job {
        gltfxEntity = Entity.create()
        return activityScope.launch {
            glXFManager.inflateGLXF(
                "apk:///scenes/Composition.glxf".toUri(),
                rootEntity = gltfxEntity!!,
                onLoaded = onLoaded,
            )
        }
    }

    override fun registerPanels(): List<PanelRegistration> {
        Log.d(TAG, "Registering spatial panels")

        return listOf(
            // Video Library Panel - Main video screen (matches scene's VRVideoLibraryPanel)
            createVideoLibraryPanelRegistration(),

            // Controls Panel - Playback controls (matches scene's ControlsPanel)
            createControlsPanelRegistration(),
        )
    }

    /**
     * Creates the library panel registration.
     * Uses ActivityPanelRegistration to show VRNavigationHost UI.
     * Panel ID must match scene's VRVideoLibraryPanel: @id/library_panel
     */
    private fun createVideoLibraryPanelRegistration(): PanelRegistration {
        return ActivityPanelRegistration(
            R.id.library_panel,
            classIdCreator = { LibraryPanelActivity::class.java },
            settingsCreator = {
                UIPanelSettings(
                    shape = QuadShapeOptions(
                        width = SpatialConstants.SCREEN_WIDTH,
                        height = SpatialConstants.SCREEN_HEIGHT
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
     * Creates the controls panel registration for playback controls.
     * Panel ID must match scene's ControlsPanel: @id/controls_panel
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
