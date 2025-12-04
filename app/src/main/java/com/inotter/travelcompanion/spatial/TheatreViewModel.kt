package com.inotter.travelcompanion.spatial

import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.meta.spatial.core.SystemManager
import com.meta.spatial.runtime.Scene
import com.inotter.travelcompanion.spatial.entities.ControlsPanelEntity
import com.inotter.travelcompanion.spatial.entities.LibraryPanelEntity
import com.inotter.travelcompanion.spatial.entities.TheatreScreenEntity
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
    
    // Entities (initialized later when scene is ready)
    private var libraryPanel: LibraryPanelEntity? = null
    private var theatreScreen: TheatreScreenEntity? = null
    private var controlsPanel: ControlsPanelEntity? = null
    
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
    }
    
    /**
     * Initializes the theatre entities after the scene is ready.
     */
    fun initializeEntities(scene: Scene) {
        Log.d(TAG, "Initializing theatre entities")
        
        // Create library panel
        libraryPanel = LibraryPanelEntity.create()
        
        // Create theatre screen (will be positioned when needed)
        theatreScreen = TheatreScreenEntity.create(exoPlayer)
        
        // Create controls panel
        controlsPanel = ControlsPanelEntity.create()
        
        // Position and show library panel initially
        showLibrary()
    }
    
    /**
     * Shows the library panel and hides playback components.
     */
    fun showLibrary() {
        Log.d(TAG, "Switching to library view")
        
        // Stop any playback
        exoPlayer.stop()
        
        // Hide playback components
        theatreScreen?.hide()
        controlsPanel?.hide()
        
        // Position and show library panel
        libraryPanel?.let {
            it.positionInFrontOfUser()
            it.show()
        }
        
        _currentState.value = TheatreState.LIBRARY
    }
    
    /**
     * Starts playback of a video and transitions to theatre mode.
     */
    fun playVideo(videoUri: String, videoTitle: String = "") {
        Log.d(TAG, "Playing video: $videoUri")
        
        // Update state with title
        _playbackState.value = _playbackState.value.copy(videoTitle = videoTitle)
        
        // Hide library panel
        libraryPanel?.hide()
        
        // Position and show theatre screen
        theatreScreen?.let { screen ->
            screen.positionInFrontOfUser()
            screen.show()
            screen.playVideo(videoUri)
            
            // Position controls below screen
            controlsPanel?.let { controls ->
                controls.positionBelowScreen(screen.entity)
                controls.show()
            }
        }
        
        _currentState.value = TheatreState.PLAYBACK
    }
    
    /**
     * Called when the user's head position is detected.
     */
    fun onHeadFound() {
        when (_currentState.value) {
            TheatreState.LIBRARY -> libraryPanel?.positionInFrontOfUser()
            TheatreState.PLAYBACK -> {
                theatreScreen?.positionInFrontOfUser()
                theatreScreen?.let { screen ->
                    controlsPanel?.positionBelowScreen(screen.entity)
                }
            }
        }
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
        theatreScreen?.togglePlayPause()
        updatePlaybackState()
    }
    
    override fun onSeek(position: Float) {
        theatreScreen?.seekTo(position)
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
        
        libraryPanel?.destroy()
        theatreScreen?.destroy()
        controlsPanel?.destroy()
        
        libraryPanel = null
        theatreScreen = null
        controlsPanel = null
    }
}
