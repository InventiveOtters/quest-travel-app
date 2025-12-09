package com.inotter.onthegovr.spatial.data

import androidx.annotation.DrawableRes
import com.inotter.onthegovr.R
import com.meta.spatial.core.Vector3

/**
 * Available environment types for the theatre experience.
 * Each environment defines a different visual setting for watching movies.
 */
enum class EnvironmentType(
    val displayName: String,
    val nodeName: String?,  // Node name in the GLXF composition
    @DrawableRes val previewImage: Int? = null,  // R.drawable reference for preview thumbnail
    val skyboxResource: Int? = null  // R.drawable reference for skybox
) {
    COLLAB_ROOM("Winter Lodge", nodeName = "Environment", previewImage = R.drawable.room1),
    COLLAB_ROOM_2("Private Theatre", nodeName = "Environment2", previewImage = R.drawable.room2),
    COLLAB_ROOM_3("Car Cinema", nodeName = "Environment3", previewImage = R.drawable.room3),
    VOID("Void", nodeName = null, previewImage = null);  // No environment, just panels in space

    companion object {
        val default = COLLAB_ROOM
    }
}

/**
 * Lighting preset for different viewing moods.
 */
data class LightingPreset(
    val name: String,
    val ambientColor: Vector3,
    val sunColor: Vector3,
    val sunDirection: Vector3,
    val environmentIntensity: Float
) {
    companion object {
        val BRIGHT = LightingPreset(
            name = "Bright",
            ambientColor = Vector3(0.4f, 0.4f, 0.4f),
            sunColor = Vector3(7.0f, 7.0f, 7.0f),
            sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
            environmentIntensity = 0.5f
        )

        val NORMAL = LightingPreset(
            name = "Normal",
            ambientColor = Vector3(0.2f, 0.2f, 0.2f),
            sunColor = Vector3(5.0f, 5.0f, 5.0f),
            sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
            environmentIntensity = 0.3f
        )

        val DIM = LightingPreset(
            name = "Dim",
            ambientColor = Vector3(0.1f, 0.1f, 0.1f),
            sunColor = Vector3(2.0f, 2.0f, 2.0f),
            sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
            environmentIntensity = 0.15f
        )

        val DARK = LightingPreset(
            name = "Dark",
            ambientColor = Vector3(0.02f, 0.02f, 0.02f),
            sunColor = Vector3(0.5f, 0.5f, 0.5f),
            sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
            environmentIntensity = 0.05f
        )

        val MOVIE = LightingPreset(
            name = "Movie",
            ambientColor = Vector3(0f, 0f, 0f),
            sunColor = Vector3(0f, 0f, 0f),
            sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
            environmentIntensity = 0f
        )

        val presets = listOf(BRIGHT, NORMAL, DIM, DARK, MOVIE)
    }
}

/**
 * Combined scene settings including environment and lighting.
 */
data class SceneSettings(
    val environment: EnvironmentType = EnvironmentType.default,
    val lightingIntensity: Float = 0.5f  // 0.0 = movie mode (dark), 1.0 = full brightness
) {
    /**
     * Calculates interpolated lighting values based on the intensity slider.
     */
    fun calculateLighting(): LightingValues {
        // Interpolate between MOVIE (dark) and BRIGHT based on intensity
        val darkPreset = LightingPreset.MOVIE
        val brightPreset = LightingPreset.BRIGHT

        return LightingValues(
            ambientColor = Vector3(
                lerp(darkPreset.ambientColor.x, brightPreset.ambientColor.x, lightingIntensity),
                lerp(darkPreset.ambientColor.y, brightPreset.ambientColor.y, lightingIntensity),
                lerp(darkPreset.ambientColor.z, brightPreset.ambientColor.z, lightingIntensity)
            ),
            sunColor = Vector3(
                lerp(darkPreset.sunColor.x, brightPreset.sunColor.x, lightingIntensity),
                lerp(darkPreset.sunColor.y, brightPreset.sunColor.y, lightingIntensity),
                lerp(darkPreset.sunColor.z, brightPreset.sunColor.z, lightingIntensity)
            ),
            sunDirection = brightPreset.sunDirection,
            environmentIntensity = lerp(
                darkPreset.environmentIntensity,
                brightPreset.environmentIntensity,
                lightingIntensity
            )
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

/**
 * Calculated lighting values to apply to the scene.
 */
data class LightingValues(
    val ambientColor: Vector3,
    val sunColor: Vector3,
    val sunDirection: Vector3,
    val environmentIntensity: Float
)
