package com.inotter.travelcompanion.spatial.entities

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialContext
import com.meta.spatial.core.Vector2
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.Scene
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelDimensions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import com.meta.spatial.toolkit.Visible
import com.inotter.travelcompanion.R
import com.inotter.travelcompanion.spatial.SpatialConstants
import com.inotter.travelcompanion.spatial.SpatialUtils

/**
 * Represents the controls panel that appears below the theatre screen.
 * Contains playback controls, seek bar, and other options.
 */
class ControlsPanelEntity private constructor(
    val entity: Entity
) {
    
    private var isVisible = false
    private var attachedToScreen: Entity? = null
    
    companion object {
        private const val TAG = "ControlsPanelEntity"
        
        // Panel dimensions in dp for the Compose UI
        const val PANEL_WIDTH_DP = 720
        const val PANEL_HEIGHT_DP = 240
        
        // Physical dimensions in meters
        private val WIDTH_IN_METERS = PANEL_WIDTH_DP / SpatialConstants.CONTROLS_PANEL_DP_PER_METER
        private val HEIGHT_IN_METERS = PANEL_HEIGHT_DP / SpatialConstants.CONTROLS_PANEL_DP_PER_METER
        
        /**
         * Creates the controls panel entity.
         */
        fun create(): ControlsPanelEntity {
            val entity = Entity.create(
                Panel(R.id.controls_panel),
                Transform(),
                Visible(false),
                PanelDimensions(Vector2(WIDTH_IN_METERS, HEIGHT_IN_METERS))
            )
            
            Log.d(TAG, "Controls panel entity created")
            return ControlsPanelEntity(entity)
        }
    }
    
    /**
     * Positions the controls panel below a theatre screen.
     */
    fun positionBelowScreen(screenEntity: Entity) {
        attachedToScreen = screenEntity
        
        val screenPose = screenEntity.getComponent<Transform>().transform
        val screenDimensions = screenEntity.tryGetComponent<PanelDimensions>()
        
        // Calculate position below the screen
        val offsetY = if (screenDimensions != null) {
            -(screenDimensions.dimensions.y / 2) + SpatialConstants.CONTROLS_OFFSET_Y
        } else {
            SpatialConstants.CONTROLS_OFFSET_Y
        }
        
        val controlsPose = Pose(
            screenPose.t + Vector3(0f, offsetY, 0.1f), // Slightly in front
            screenPose.q
        )
        
        entity.setComponent(Transform(controlsPose))
        Log.d(TAG, "Controls panel positioned below screen")
    }
    
    /**
     * Positions the controls panel in front of the user independently.
     */
    fun positionInFrontOfUser() {
        SpatialUtils.placeInFrontOfHead(
            entity,
            distance = SpatialConstants.SPAWN_DISTANCE,
            offset = Vector3(0f, -0.3f, 0f) // Below eye level
        )
        Log.d(TAG, "Controls panel positioned in front of user")
    }
    
    /**
     * Attaches the controls panel as a child of another entity.
     */
    fun attachToEntity(parent: Entity) {
        attachedToScreen = parent
        entity.setComponent(TransformParent(parent))
        
        // Set local position below the parent
        val localOffset = Vector3(0f, SpatialConstants.CONTROLS_OFFSET_Y, 0.05f)
        entity.setComponent(Transform(Pose(localOffset)))
        
        Log.d(TAG, "Controls panel attached to parent entity")
    }
    
    /**
     * Detaches the controls panel from its parent.
     */
    fun detachFromParent() {
        if (attachedToScreen != null) {
            // Get absolute position before detaching
            val currentPose = entity.getComponent<Transform>().transform
            entity.setComponent(TransformParent(Entity.nullEntity()))
            entity.setComponent(Transform(currentPose))
            attachedToScreen = null
            Log.d(TAG, "Controls panel detached from parent")
        }
    }
    
    /**
     * Shows the controls panel.
     */
    fun show() {
        isVisible = true
        entity.setComponent(Visible(true))
        Log.d(TAG, "Controls panel shown")
    }
    
    /**
     * Hides the controls panel.
     */
    fun hide() {
        isVisible = false
        entity.setComponent(Visible(false))
        Log.d(TAG, "Controls panel hidden")
    }
    
    /**
     * Toggles visibility.
     */
    fun toggleVisibility(): Boolean {
        if (isVisible) {
            hide()
        } else {
            show()
        }
        return isVisible
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
        Log.d(TAG, "Controls panel destroyed")
    }
}
