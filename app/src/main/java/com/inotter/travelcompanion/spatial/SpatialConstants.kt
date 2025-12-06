package com.inotter.travelcompanion.spatial

import com.meta.spatial.core.Vector3

/**
 * Constants for the spatial theatre experience.
 */
object SpatialConstants {
    // Theatre screen dimensions (meters)
    const val SCREEN_WIDTH = 4.0f       // Width of the theatre screen
    const val SCREEN_HEIGHT = 2.25f     // Height (16:9 aspect ratio)
    const val SCREEN_DISTANCE = 3.0f    // Distance from user to screen
    
    // Controls panel dimensions
    // Compact settings panel with lighting slider and environment selector only
    const val CONTROLS_PANEL_WIDTH = 0.45f  // Compact width
    const val CONTROLS_PANEL_HEIGHT = 0.28f  // Compact height
    const val CONTROLS_PANEL_DP_PER_METER = 800f  // Higher density for readability
    const val CONTROLS_OFFSET_Y = -0.8f  // Below the screen

    // Library panel dimensions
    const val LIBRARY_PANEL_WIDTH = 2.0f  // Physical width in meters
    const val LIBRARY_PANEL_HEIGHT = 1.25f  // Physical height in meters (matching 1024:640 aspect ratio)
    // Use same dp values as the 2D panel mode (from AndroidManifest layout)
    const val LIBRARY_PANEL_DP_WIDTH = 1024f  // Layout width in dp (matches 2D panel)
    const val LIBRARY_PANEL_DP_HEIGHT = 640f  // Layout height in dp (matches 2D panel)
    
    // Spawn and positioning
    const val SPAWN_DISTANCE = 1.5f
    const val PANEL_FOV = 55f
    
    // Blue skybox color (RGB normalized)
    val SKYBOX_COLOR = Vector3(0.1f, 0.2f, 0.5f)
    
    // Lighting configuration for theatre
    val LIGHT_AMBIENT_COLOR = Vector3(0.3f, 0.3f, 0.4f)
    val LIGHT_SUN_COLOR = Vector3(0.2f, 0.2f, 0.3f)
    val LIGHT_SUN_DIRECTION = -Vector3(1.0f, 3.0f, 2.0f)
    
    // Animation timings (milliseconds)
    object Timings {
        const val PANEL_FADE = 400
        const val SCREEN_FADE = 500
        const val CONTROLS_FADE = 300
    }
}
