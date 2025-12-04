package com.inotter.travelcompanion.spatial.entities

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Vector2
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelDimensions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.inotter.travelcompanion.R
import com.inotter.travelcompanion.spatial.SpatialConstants
import com.inotter.travelcompanion.spatial.SpatialUtils

/**
 * Represents the library panel for browsing videos.
 * This is the main navigation panel shown before entering playback mode.
 */
class LibraryPanelEntity private constructor(
    val entity: Entity
) {
    
    private var isVisible = false
    
    companion object {
        private const val TAG = "LibraryPanelEntity"
        
        // Panel dimensions in dp
        const val PANEL_WIDTH_DP = 1600
        const val PANEL_HEIGHT_DP = 1200
        
        // Physical dimensions in meters
        private val WIDTH_IN_METERS = PANEL_WIDTH_DP / SpatialConstants.LIBRARY_PANEL_DP_PER_METER
        private val HEIGHT_IN_METERS = PANEL_HEIGHT_DP / SpatialConstants.LIBRARY_PANEL_DP_PER_METER
        
        /**
         * Creates the library panel entity.
         */
        fun create(): LibraryPanelEntity {
            val entity = Entity.create(
                Panel(R.id.library_panel),
                Transform(),
                Visible(false),
                PanelDimensions(Vector2(WIDTH_IN_METERS, HEIGHT_IN_METERS))
            )
            
            Log.d(TAG, "Library panel entity created")
            return LibraryPanelEntity(entity)
        }
    }
    
    /**
     * Positions the library panel in front of the user.
     */
    fun positionInFrontOfUser() {
        SpatialUtils.setDistanceAndFov(
            entity,
            distance = SpatialConstants.SPAWN_DISTANCE,
            fov = SpatialConstants.PANEL_FOV
        )
        Log.d(TAG, "Library panel positioned in front of user")
    }
    
    /**
     * Shows the library panel with optional animation.
     */
    fun show() {
        isVisible = true
        entity.setComponent(Visible(true))
        Log.d(TAG, "Library panel shown")
    }
    
    /**
     * Hides the library panel.
     */
    fun hide() {
        isVisible = false
        entity.setComponent(Visible(false))
        Log.d(TAG, "Library panel hidden")
    }
    
    /**
     * Returns whether the panel is currently visible.
     */
    fun isVisible(): Boolean = isVisible
    
    /**
     * Cleans up resources.
     */
    fun destroy() {
        entity.destroy()
        Log.d(TAG, "Library panel destroyed")
    }
}
