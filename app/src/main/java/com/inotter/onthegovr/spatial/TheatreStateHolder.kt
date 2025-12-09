package com.inotter.onthegovr.spatial

import android.util.Log
import com.inotter.onthegovr.spatial.data.EnvironmentType
import com.inotter.onthegovr.spatial.ui.ControlsPanelCallback
import com.inotter.onthegovr.spatial.ui.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton state holder for sharing theatre state between activities.
 * 
 * In Meta Spatial SDK, panel activities run in a separate context from the
 * immersive activity. This holder provides a way to share state and callbacks
 * between them without complex IPC.
 * 
 * For production apps with more complex needs, consider using:
 * - IPCService pattern (like PremiumMediaSample)
 * - Android's Messenger/AIDL
 * - SharedPreferences with listeners
 */
object TheatreStateHolder {
    
    private const val TAG = "TheatreStateHolder"
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private var callback: ControlsPanelCallback? = null
    
    // Direct reference to lighting manager for when callback isn't available
    private var sceneLightingManager: SceneLightingManager? = null
    
    /**
     * Register the callback from the TheatreViewModel.
     * Called when ImmersiveActivity initializes.
     */
    fun registerCallback(theatreCallback: ControlsPanelCallback) {
        Log.d(TAG, "Callback registered: $theatreCallback")
        callback = theatreCallback
    }
    
    /**
     * Register the scene lighting manager for direct access.
     */
    fun registerLightingManager(manager: SceneLightingManager) {
        Log.d(TAG, "Lighting manager registered: $manager")
        sceneLightingManager = manager
    }
    
    /**
     * Unregister the callback.
     * Called when ImmersiveActivity is destroyed.
     */
    fun unregisterCallback() {
        Log.d(TAG, "Callback unregistered")
        callback = null
        sceneLightingManager = null
    }
    
    /**
     * Update the playback state.
     * Called by TheatreViewModel when state changes.
     */
    fun updatePlaybackState(state: PlaybackState) {
        _playbackState.value = state
    }
    
    /**
     * Check if callback is available.
     */
    fun isCallbackAvailable(): Boolean = callback != null
    
    // Callback forwarding methods for ControlsPanelActivity
    
    fun onPlayPause() {
        Log.d(TAG, "onPlayPause called, callback: $callback")
        callback?.onPlayPause()
    }
    
    fun onSeek(position: Float) {
        Log.d(TAG, "onSeek called: $position, callback: $callback")
        callback?.onSeek(position)
    }
    
    fun onRewind() {
        Log.d(TAG, "onRewind called, callback: $callback")
        callback?.onRewind()
    }
    
    fun onFastForward() {
        Log.d(TAG, "onFastForward called, callback: $callback")
        callback?.onFastForward()
    }
    
    fun onRestart() {
        Log.d(TAG, "onRestart called, callback: $callback")
        callback?.onRestart()
    }
    
    fun onMuteToggle() {
        Log.d(TAG, "onMuteToggle called, callback: $callback")
        callback?.onMuteToggle()
    }
    
    fun onClose() {
        Log.d(TAG, "onClose called, callback: $callback")
        callback?.onClose()
    }
    
    fun onLightingChanged(intensity: Float) {
        Log.d(TAG, "onLightingChanged called: $intensity, callback: $callback, lightingManager: $sceneLightingManager")
        
        // Try callback first
        if (callback != null) {
            callback?.onLightingChanged(intensity)
        } else {
            // Fallback: directly update lighting manager and state
            sceneLightingManager?.setLightingIntensity(intensity)
            _playbackState.value = _playbackState.value.copy(lightingIntensity = intensity)
            Log.d(TAG, "Updated lighting directly via manager")
        }
    }
    
    fun onEnvironmentChanged(environment: EnvironmentType) {
        Log.d(TAG, "onEnvironmentChanged called: $environment, callback: $callback, lightingManager: $sceneLightingManager")
        
        // Try callback first
        if (callback != null) {
            callback?.onEnvironmentChanged(environment)
        } else {
            // Fallback: directly update lighting manager and state
            sceneLightingManager?.setEnvironment(environment)
            _playbackState.value = _playbackState.value.copy(currentEnvironment = environment)
            Log.d(TAG, "Updated environment directly via manager")
        }
    }
    
    fun onToggleSettings() {
        Log.d(TAG, "onToggleSettings called, callback: $callback")
        
        // This can work without callback - just update local state
        val currentShowSettings = _playbackState.value.showSettings
        _playbackState.value = _playbackState.value.copy(showSettings = !currentShowSettings)
        Log.d(TAG, "Settings toggled to: ${!currentShowSettings}")
        
        // Also notify callback if available
        callback?.onToggleSettings()
    }
}
