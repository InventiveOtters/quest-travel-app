package com.inotter.travelcompanion.spatial.entities

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialContext
import com.meta.spatial.core.Vector2
import com.meta.spatial.runtime.Scene
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.MediaPanelRenderOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelDimensions
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.toolkit.Visible
import com.inotter.travelcompanion.R
import com.inotter.travelcompanion.spatial.SpatialConstants
import com.inotter.travelcompanion.spatial.SpatialUtils

/**
 * Represents the main theatre screen for video playback.
 * Uses VideoSurfacePanelRegistration for direct ExoPlayer rendering.
 */
class TheatreScreenEntity private constructor(
    val entity: Entity,
    val exoPlayer: ExoPlayer
) {
    
    private var isVisible = false
    
    companion object {
        private const val TAG = "TheatreScreenEntity"
        
        /**
         * Creates a theatre screen panel registration.
         */
        fun createPanelRegistration(
            scene: Scene,
            context: SpatialContext,
            exoPlayer: ExoPlayer
        ): VideoSurfacePanelRegistration {
            return VideoSurfacePanelRegistration(
                R.id.theatre_screen_panel,
                surfaceConsumer = { panelEntity, surface ->
                    // Paint black initially to avoid showing garbage
                    val canvas = surface.lockCanvas(null)
                    canvas.drawColor(android.graphics.Color.BLACK)
                    surface.unlockCanvasAndPost(canvas)
                    
                    // Connect ExoPlayer to the surface
                    exoPlayer.setVideoSurface(surface)
                    Log.d(TAG, "ExoPlayer connected to theatre screen surface")
                },
                settingsCreator = {
                    MediaPanelSettings(
                        shape = QuadShapeOptions(
                            width = SpatialConstants.SCREEN_WIDTH,
                            height = SpatialConstants.SCREEN_HEIGHT
                        ),
                        display = PixelDisplayOptions(
                            width = 1920,  // Full HD width
                            height = 1080  // Full HD height
                        ),
                        rendering = MediaPanelRenderOptions(
                            isDRM = false,
                            zIndex = 0
                        ),
                        style = PanelStyleOptions(
                            themeResourceId = R.style.PanelAppThemeTransparent
                        )
                    )
                }
            )
        }
        
        /**
         * Creates and positions the theatre screen entity in the scene.
         */
        fun create(exoPlayer: ExoPlayer): TheatreScreenEntity {
            val entity = Entity.create(
                Panel(R.id.theatre_screen_panel),
                Transform(),
                Visible(false),
                PanelDimensions(
                    Vector2(SpatialConstants.SCREEN_WIDTH, SpatialConstants.SCREEN_HEIGHT)
                )
            )
            
            return TheatreScreenEntity(entity, exoPlayer)
        }
    }
    
    /**
     * Positions the screen in front of the user at the configured distance.
     */
    fun positionInFrontOfUser() {
        SpatialUtils.placeInFrontOfHead(
            entity,
            distance = SpatialConstants.SCREEN_DISTANCE,
            offset = com.meta.spatial.core.Vector3(0f, 0.3f, 0f) // Slightly above eye level
        )
        Log.d(TAG, "Theatre screen positioned in front of user")
    }
    
    /**
     * Gets the current pose of the screen.
     */
    fun getScreenPose(): Pose {
        return entity.getComponent<Transform>().transform
    }
    
    /**
     * Shows the theatre screen with animation.
     */
    fun show() {
        isVisible = true
        entity.setComponent(Visible(true))
        Log.d(TAG, "Theatre screen shown")
    }
    
    /**
     * Hides the theatre screen.
     */
    fun hide() {
        isVisible = false
        entity.setComponent(Visible(false))
        Log.d(TAG, "Theatre screen hidden")
    }
    
    /**
     * Plays a video from the given URI.
     */
    fun playVideo(videoUri: String) {
        val mediaItem = MediaItem.fromUri(videoUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        Log.d(TAG, "Playing video: $videoUri")
    }
    
    /**
     * Pauses playback.
     */
    fun pause() {
        exoPlayer.pause()
    }
    
    /**
     * Resumes playback.
     */
    fun resume() {
        exoPlayer.play()
    }
    
    /**
     * Toggles play/pause state.
     */
    fun togglePlayPause(): Boolean {
        return if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            false
        } else {
            exoPlayer.play()
            true
        }
    }
    
    /**
     * Seeks to a position (0.0 to 1.0).
     */
    fun seekTo(position: Float) {
        val duration = exoPlayer.duration
        if (duration > 0) {
            exoPlayer.seekTo((position * duration).toLong())
        }
    }
    
    /**
     * Gets the current playback progress (0.0 to 1.0).
     */
    fun getProgress(): Float {
        val duration = exoPlayer.duration
        return if (duration > 0) {
            exoPlayer.currentPosition.toFloat() / duration.toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Checks if currently playing.
     */
    fun isPlaying(): Boolean = exoPlayer.isPlaying
    
    /**
     * Cleans up resources.
     */
    fun destroy() {
        exoPlayer.stop()
        exoPlayer.setVideoSurface(null)
        entity.destroy()
        Log.d(TAG, "Theatre screen destroyed")
    }
}
