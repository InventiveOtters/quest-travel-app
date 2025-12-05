package com.inotter.travelcompanion.spatial

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.meta.spatial.core.SystemManager
import com.meta.spatial.runtime.Scene
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.inotter.travelcompanion.spatial.data.EnvironmentType
import com.inotter.travelcompanion.spatial.ui.ControlsPanelCallback
import com.inotter.travelcompanion.spatial.ui.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages the state and logic for the immersive theatre experience.
 * Coordinates between the library panel, theatre screen, and controls panel.
 */
class TheatreViewModel(
    private val exoPlayer: ExoPlayer,
    private val systemManager: SystemManager
) : ControlsPanelCallback {
    
    companion object {
        private const val TAG = "TheatreViewModel"
        private const val SEEK_INCREMENT_MS = 10_000L  // 10 seconds
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    }
    
    // Current state
    enum class TheatreState {
        LIBRARY,    // Showing the library panel
        PLAYBACK    // Showing the theatre screen and controls
    }
    
    private val _currentState = MutableStateFlow(TheatreState.LIBRARY)
    val currentState: StateFlow<TheatreState> = _currentState.asStateFlow()
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    // Panel entities from GLXF (like Object3DSample pattern)
    private var libraryPanelEntity: Entity? = null
    private var controlsPanelEntity: Entity? = null
    
    // Scene lighting manager for environment and lighting control
    private var sceneLightingManager: SceneLightingManager? = null
    
    // Coroutine scope for progress updates
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressUpdateJob: Job? = null
    
    // Player listener
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }
        
        override fun onPlaybackStateChanged(state: Int) {
            updatePlaybackState()
        }
    }
    
    init {
        exoPlayer.addListener(playerListener)
        // Register with state holder for panel communication
        TheatreStateHolder.registerCallback(this)
    }
    
    /**
     * Initializes the theatre entities after the scene is ready.
     * Following Object3DSample pattern: panels are created by GLXF, we just get references.
     */
    fun initializeEntities(
        scene: Scene, 
        libraryPanel: Entity?, 
        controlsPanel: Entity?,
        environmentEntity: Entity? = null
    ) {
        Log.d(TAG, "Initializing theatre entities from GLXF")
        
        // Store references to GLXF-created panel entities
        this.libraryPanelEntity = libraryPanel
        this.controlsPanelEntity = controlsPanel
        
        // Initialize scene lighting manager
        sceneLightingManager = SceneLightingManager(scene, environmentEntity)
        sceneLightingManager?.initialize(environmentEntity)
        
        // Register lighting manager with state holder for direct access from panels
        sceneLightingManager?.let { TheatreStateHolder.registerLightingManager(it) }
        
        // Show library panel initially (it's already positioned in GLXF)
        libraryPanelEntity?.setComponent(Visible(true))
        
        // TESTING: Show controls panel immediately for testing lighting slider
        // TODO: Remove this after testing - controls should only show during playback
        controlsPanelEntity?.setComponent(Visible(true))
        Log.d(TAG, "Controls panel entity: $controlsPanelEntity")
        
        // TESTING: Set initial state with settings visible for testing lighting
        _playbackState.value = _playbackState.value.copy(showSettings = true)
        TheatreStateHolder.updatePlaybackState(_playbackState.value)
        
        _currentState.value = TheatreState.LIBRARY
        Log.d(TAG, "Theatre initialized - library visible, controls visible (testing mode)")
    }
    
    /**
     * Toggle controls panel visibility for testing.
     * Call this to manually show/hide the controls panel.
     */
    fun toggleControlsPanel() {
        val currentlyVisible = controlsPanelEntity?.getComponent<Visible>()?.isVisible ?: false
        controlsPanelEntity?.setComponent(Visible(!currentlyVisible))
        Log.d(TAG, "Controls panel toggled: ${!currentlyVisible}")
    }
    
    /**
     * Register additional environment entities for scene switching.
     */
    fun registerEnvironment(type: EnvironmentType, entity: Entity?) {
        sceneLightingManager?.registerEnvironment(type, entity)
    }
    
    /**
     * Get the scene lighting manager for external access.
     */
    fun getSceneLightingManager(): SceneLightingManager? = sceneLightingManager
    
    /**
     * Shows the library panel and hides playback components.
     */
    fun showLibrary() {
        Log.d(TAG, "Switching to library view")
        
        // Stop any playback
        exoPlayer.stop()
        
        // Hide controls, show library
        controlsPanelEntity?.setComponent(Visible(false))
        libraryPanelEntity?.setComponent(Visible(true))
        
        _currentState.value = TheatreState.LIBRARY
    }
    
    /**
     * Starts playback of a video and transitions to theatre mode.
     * The library panel (VRVideoLibraryPanel) acts as the video surface.
     */
    fun playVideo(videoUri: String, videoTitle: String = "") {
        Log.d(TAG, "Playing video: $videoUri")
        
        // Update state with title
        _playbackState.value = _playbackState.value.copy(videoTitle = videoTitle)
        
        // Show controls panel
        controlsPanelEntity?.setComponent(Visible(true))
        
        // Play video (ExoPlayer is connected to library_panel surface via VideoSurfacePanelRegistration)
        val mediaItem = androidx.media3.common.MediaItem.fromUri(videoUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        
        _currentState.value = TheatreState.PLAYBACK
    }
    
    /**
     * Called when the user's head position is detected.
     * Panels are positioned by GLXF, so this is a no-op now.
     */
    fun onHeadFound() {
        // Panels are positioned in GLXF, no dynamic repositioning needed
        Log.d(TAG, "Head found - panels positioned by GLXF")
    }
    
    /**
     * Recenters all panels to face the user.
     */
    fun recenter() {
        Log.d(TAG, "Recentering panels")
        onHeadFound()
    }
    
    // ControlsPanelCallback implementation
    
    override fun onPlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        updatePlaybackState()
    }
    
    override fun onSeek(position: Float) {
        val duration = exoPlayer.duration
        if (duration > 0) {
            exoPlayer.seekTo((position * duration).toLong())
        }
        updatePlaybackState()
    }
    
    override fun onRewind() {
        val newPosition = (exoPlayer.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0)
        exoPlayer.seekTo(newPosition)
        updatePlaybackState()
    }
    
    override fun onFastForward() {
        val newPosition = (exoPlayer.currentPosition + SEEK_INCREMENT_MS)
            .coerceAtMost(exoPlayer.duration)
        exoPlayer.seekTo(newPosition)
        updatePlaybackState()
    }
    
    override fun onRestart() {
        exoPlayer.seekTo(0)
        exoPlayer.play()
        updatePlaybackState()
    }
    
    override fun onMuteToggle() {
        val isMuted = exoPlayer.volume == 0f
        exoPlayer.volume = if (isMuted) 1f else 0f
        updatePlaybackState()
    }
    
    override fun onClose() {
        showLibrary()
    }
    
    override fun onLightingChanged(intensity: Float) {
        Log.d(TAG, "Lighting changed: $intensity")
        sceneLightingManager?.setLightingIntensity(intensity)
        _playbackState.value = _playbackState.value.copy(lightingIntensity = intensity)
        TheatreStateHolder.updatePlaybackState(_playbackState.value)
    }
    
    override fun onEnvironmentChanged(environment: EnvironmentType) {
        Log.d(TAG, "Environment changed: $environment")
        sceneLightingManager?.setEnvironment(environment)
        _playbackState.value = _playbackState.value.copy(currentEnvironment = environment)
        TheatreStateHolder.updatePlaybackState(_playbackState.value)
    }
    
    override fun onToggleSettings() {
        val currentShowSettings = _playbackState.value.showSettings
        _playbackState.value = _playbackState.value.copy(showSettings = !currentShowSettings)
        TheatreStateHolder.updatePlaybackState(_playbackState.value)
        Log.d(TAG, "Settings toggled: ${!currentShowSettings}")
    }
    
    // Progress updates
    
    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressUpdateJob = scope.launch {
            while (isActive) {
                updatePlaybackState()
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }
    
    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
    
    private fun updatePlaybackState() {
        val duration = exoPlayer.duration
        val currentPosition = exoPlayer.currentPosition
        val progress = if (duration > 0) {
            currentPosition.toFloat() / duration.toFloat()
        } else {
            0f
        }
        
        _playbackState.value = _playbackState.value.copy(
            isPlaying = exoPlayer.isPlaying,
            isMuted = exoPlayer.volume == 0f,
            progress = progress,
            duration = duration,
            currentPosition = currentPosition
        )
        
        // Update shared state holder for panel activities
        TheatreStateHolder.updatePlaybackState(_playbackState.value)
    }
    
    // Lifecycle
    
    fun onPause() {
        exoPlayer.pause()
    }
    
    fun onResume() {
        // Only resume if we were playing
        if (_playbackState.value.isPlaying) {
            exoPlayer.play()
        }
    }
    
    fun destroy() {
        Log.d(TAG, "Destroying TheatreViewModel")
        
        scope.cancel()
        exoPlayer.removeListener(playerListener)
        TheatreStateHolder.unregisterCallback()
        
        // Entities are owned by GLXF, just clear references
        libraryPanelEntity = null
        controlsPanelEntity = null
        sceneLightingManager = null
    }
}
