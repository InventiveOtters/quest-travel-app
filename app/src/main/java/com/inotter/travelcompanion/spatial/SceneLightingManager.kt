package com.inotter.travelcompanion.spatial

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Vector3
import com.meta.spatial.core.Color4
import com.meta.spatial.runtime.Scene
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Visible
import com.inotter.travelcompanion.spatial.data.EnvironmentType
import com.inotter.travelcompanion.spatial.data.LightingValues
import com.inotter.travelcompanion.spatial.data.SceneSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages scene lighting and environment switching for the theatre experience.
 * 
 * Controls:
 * - Ambient lighting intensity
 * - Sun/directional light intensity
 * - Environment intensity (IBL reflections)
 * - Environment mesh visibility
 * 
 * This provides a "movie mode" experience where the environment dims
 * to focus attention on the video panels.
 */
class SceneLightingManager(
    private val scene: Scene,
    private var environmentEntity: Entity? = null
) {
    companion object {
        private const val TAG = "SceneLightingManager"
    }

    private val _currentSettings = MutableStateFlow(SceneSettings())
    val currentSettings: StateFlow<SceneSettings> = _currentSettings.asStateFlow()

    // Registered environment entities for switching
    private val environmentEntities = mutableMapOf<EnvironmentType, Entity?>()

    // Skybox entity for tinting
    private var skyboxEntity: Entity? = null

    // Store original material colors for proper interpolation
    private var originalSkyboxColor: Color4 = Color4(1f, 1f, 1f, 1f)

    /**
     * Initialize with the main environment entity from the scene.
     */
    fun initialize(mainEnvironmentEntity: Entity?) {
        this.environmentEntity = mainEnvironmentEntity
        environmentEntities[EnvironmentType.COLLAB_ROOM] = mainEnvironmentEntity
        applySettings(_currentSettings.value)
        Log.d(TAG, "Initialized with environment entity: $mainEnvironmentEntity")
    }

    /**
     * Register the skybox entity for tinting with lighting changes.
     */
    fun registerSkybox(skybox: Entity) {
        Log.d(TAG, "Registering skybox entity: $skybox")
        skyboxEntity = skybox
    }

    /**
     * Register an additional environment entity for switching.
     */
    fun registerEnvironment(type: EnvironmentType, entity: Entity?) {
        environmentEntities[type] = entity
        // Hide non-active environments initially
        if (type != _currentSettings.value.environment) {
            entity?.setComponent(Visible(false))
        }
        Log.d(TAG, "Registered environment: $type -> $entity")
    }

    /**
     * Switch to a different environment.
     */
    fun setEnvironment(environmentType: EnvironmentType) {
        if (_currentSettings.value.environment == environmentType) {
            Log.d(TAG, "Already in environment: $environmentType")
            return
        }

        val oldType = _currentSettings.value.environment
        _currentSettings.value = _currentSettings.value.copy(environment = environmentType)

        // Hide old environment
        environmentEntities[oldType]?.setComponent(Visible(false))

        // Show new environment (unless VOID which has no environment)
        if (environmentType != EnvironmentType.VOID) {
            environmentEntities[environmentType]?.setComponent(Visible(true))
        }

        Log.d(TAG, "Switched environment: $oldType -> $environmentType")
    }

    /**
     * Set the lighting intensity.
     * @param intensity 0.0 = movie mode (completely dark except panels), 1.0 = full brightness
     */
    fun setLightingIntensity(intensity: Float) {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        if (_currentSettings.value.lightingIntensity == clampedIntensity) return

        _currentSettings.value = _currentSettings.value.copy(lightingIntensity = clampedIntensity)
        applyLighting(_currentSettings.value.calculateLighting())
        applyUnlitTint(clampedIntensity)
        Log.d(TAG, "Set lighting intensity: $clampedIntensity")
    }

    /**
     * Apply current settings to the scene.
     */
    fun applySettings(settings: SceneSettings) {
        _currentSettings.value = settings
        applyLighting(settings.calculateLighting())
        applyUnlitTint(settings.lightingIntensity)

        // Update environment visibility
        environmentEntities.forEach { (type, entity) ->
            val isActive = type == settings.environment && type != EnvironmentType.VOID
            entity?.setComponent(Visible(isActive))
        }
    }

    /**
     * Apply lighting values to the scene.
     */
    private fun applyLighting(lighting: LightingValues) {
        try {
            scene.setLightingEnvironment(
                ambientColor = lighting.ambientColor,
                sunColor = lighting.sunColor,
                sunDirection = lighting.sunDirection,
                environmentIntensity = lighting.environmentIntensity
            )
            Log.d(TAG, "Applied lighting - ambient: ${lighting.ambientColor}, " +
                    "sun: ${lighting.sunColor}, envIntensity: ${lighting.environmentIntensity}")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying lighting", e)
        }
    }

    /**
     * Quick preset: Enter movie mode (dark environment).
     */
    fun enterMovieMode() {
        setLightingIntensity(0f)
    }

    /**
     * Quick preset: Exit movie mode (normal lighting).
     */
    fun exitMovieMode() {
        setLightingIntensity(0.5f)
    }

    /**
     * Get available environments.
     */
    fun getAvailableEnvironments(): List<EnvironmentType> {
        return EnvironmentType.entries.toList()
    }
    
    /**
     * Apply tint to the skybox which uses an unlit material.
     * Environment meshes are handled by the scene lighting settings.
     */
    private fun applyUnlitTint(intensity: Float) {
        // Calculate tint color - at 0 intensity, everything is dark
        // At 1 intensity, everything is at full brightness
        // Use a quadratic curve for more dramatic darkening at low values
        val darkTintValue = (intensity * intensity).coerceIn(0.02f, 1f)
        
        // Tint the skybox (we created this with a Material, so it should work)
        skyboxEntity?.let { skybox ->
            try {
                val material = skybox.getComponent<Material>()
                material.baseColor = Color4(darkTintValue, darkTintValue, darkTintValue, 1f)
                skybox.setComponent(material)
                Log.d(TAG, "Skybox tint applied: $darkTintValue")
            } catch (e: Exception) {
                Log.w(TAG, "Could not tint skybox: ${e.message}")
            }
        }
    }
}
