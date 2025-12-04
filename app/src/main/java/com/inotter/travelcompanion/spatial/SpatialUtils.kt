package com.inotter.travelcompanion.spatial

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Query
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector2
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.AvatarAttachment
import com.meta.spatial.toolkit.GrabbableType
import com.meta.spatial.toolkit.PanelDimensions
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import kotlin.math.atan
import kotlin.math.tan

/**
 * Utility functions for spatial positioning and calculations.
 */
object SpatialUtils {
    
    /**
     * Gets the current pose of the user's head.
     */
    fun getHeadPose(): Pose {
        val head = Query.where { has(AvatarAttachment.id) }
            .filter { isLocal() and by(AvatarAttachment.typeData).isEqualTo("head") }
            .eval()
            .firstOrNull() ?: return Pose()
        return head.getComponent<Transform>().transform
    }
    
    /**
     * Places an entity in front of the user's head at a specified distance.
     */
    fun placeInFrontOfHead(
        entity: Entity,
        distance: Float = 1.0f,
        offset: Vector3 = Vector3(0f, 0f, 0f),
        pivotType: GrabbableType = GrabbableType.PIVOT_Y,
        angleYAxisFromHead: Float = 0f
    ) {
        val pose = getPoseInFrontOfHead(distance, offset, pivotType, angleYAxisFromHead)
        
        val transformParent = entity.tryGetComponent<TransformParent>()
        if (transformParent != null && transformParent.entity.id != Entity.nullEntity().id) {
            setAbsolutePosition(entity, pose)
        } else {
            entity.setComponent(Transform(pose))
        }
    }
    
    /**
     * Calculates a pose in front of the user's head.
     */
    fun getPoseInFrontOfHead(
        distance: Float = 1.0f,
        offset: Vector3 = Vector3(0f, 0f, 0f),
        pivotType: GrabbableType = GrabbableType.PIVOT_Y,
        angleYAxisFromHead: Float = 0f,
        useHeadY: Boolean = true
    ): Pose {
        val headPose = getHeadPose()
        return getPoseInFrontOfVector(
            headPose, distance, offset, pivotType, angleYAxisFromHead, useHeadY
        )
    }
    
    /**
     * Calculates a pose in front of a given vector/pose.
     */
    fun getPoseInFrontOfVector(
        vector: Pose,
        distance: Float = 1.0f,
        offset: Vector3 = Vector3(0f, 0f, 0f),
        pivotType: GrabbableType = GrabbableType.PIVOT_Y,
        angleYAxisFromHead: Float = 0f,
        useHeadY: Boolean = true
    ): Pose {
        val resultPose = Pose(vector.t, vector.q)
        
        if (angleYAxisFromHead != 0f) {
            resultPose.q = resultPose.q.times(Quaternion(0f, angleYAxisFromHead, 0f))
        }
        
        val forward = resultPose.forward()
        val offsetFromHead: Vector3
        
        if (useHeadY) {
            val flatForward = Vector3(forward.x, 0f, forward.z).normalize()
            offsetFromHead = flatForward * distance + offset
        } else {
            offsetFromHead = forward * distance + offset
        }
        
        resultPose.t += offsetFromHead
        
        if (pivotType == GrabbableType.PIVOT_Y) {
            val lookDirection = Vector3(offsetFromHead.x, 0f, offsetFromHead.z)
            resultPose.q = Quaternion.lookRotation(lookDirection)
        }
        
        return resultPose
    }
    
    /**
     * Sets the absolute position of an entity, accounting for parent transforms.
     */
    fun setAbsolutePosition(entity: Entity, absolutePosition: Pose) {
        val transformParent = entity.tryGetComponent<TransformParent>()
        if (transformParent == null) {
            entity.setComponent(Transform(absolutePosition))
            return
        }
        
        val parentTransform = transformParent.entity.getComponent<Transform>()
        val localPose = parentTransform.transform.inverse().times(absolutePosition)
        entity.setComponent(Transform(localPose))
    }
    
    /**
     * Gets the size of an entity considering panel dimensions and scale.
     */
    fun getSize(entity: Entity): Vector3 {
        val panelDimensions = entity.tryGetComponent<PanelDimensions>()
        val scale = entity.tryGetComponent<Scale>()
        
        return when {
            panelDimensions != null && scale != null -> Vector3(
                panelDimensions.dimensions.x * scale.scale.x,
                panelDimensions.dimensions.y * scale.scale.y,
                scale.scale.z
            )
            panelDimensions != null -> Vector3(
                panelDimensions.dimensions.x,
                panelDimensions.dimensions.y,
                1f
            )
            scale != null -> scale.scale
            else -> Vector3(1f)
        }
    }
    
    /**
     * Sets the size of an entity.
     */
    fun setSize(entity: Entity, size: Vector3) {
        val scale = if (entity.hasComponent<Scale>()) entity.getComponent<Scale>() else Scale()
        val panel = entity.tryGetComponent<PanelDimensions>()
        
        if (panel != null) {
            scale.scale.x = if (panel.dimensions.x != 0f) size.x / panel.dimensions.x else 0f
            scale.scale.y = if (panel.dimensions.y != 0f) size.y / panel.dimensions.y else 0f
        }
        entity.setComponent(scale)
    }
    
    /**
     * Calculates size from field of view at a given distance.
     */
    fun getSizeFromFov(
        entity: Entity,
        distance: Float,
        fov: Float,
        basedOnWidth: Boolean = true
    ): Vector2 {
        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val size = getSize(entity)
        val aspectRatio = size.x / size.y
        
        return if (basedOnWidth) {
            val width = 2 * distance * tan(fovRad / 2)
            Vector2(width, width / aspectRatio)
        } else {
            val height = 2 * distance * tan(fovRad / 2)
            Vector2(height * aspectRatio, height)
        }
    }
    
    /**
     * Sets the distance and size of an entity based on FOV.
     */
    fun setDistanceAndFov(
        entity: Entity,
        distance: Float,
        fov: Float,
        basedOnWidth: Boolean = true,
        eyeAngle: Float = 0f,
        axisAngle: Float = 0f,
        angleContent: Boolean = false,
        headPose: Pose = getHeadPose()
    ) {
        val newSize = getSizeFromFov(entity, distance, fov, basedOnWidth)
        val pose = getPoseInFrontOfVector(headPose, distance, angleYAxisFromHead = axisAngle)
        
        if (eyeAngle != 0f) {
            val yDistance = (tan(Math.toRadians(eyeAngle.toDouble())) * distance).toFloat()
            pose.t += Vector3(0f, yDistance, 0f)
            if (angleContent) {
                pose.q *= Quaternion(-eyeAngle, 0f, 0f)
            }
        }
        
        entity.setComponent(Transform(pose))
        setSize(entity, Vector3(newSize.x, newSize.y, 1f))
    }
    
    /**
     * Converts milliseconds to float seconds.
     */
    fun Int.toSeconds(): Float = this.toFloat() / 1000f
}
