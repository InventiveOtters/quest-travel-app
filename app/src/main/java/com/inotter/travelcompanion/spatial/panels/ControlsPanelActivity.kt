package com.inotter.travelcompanion.spatial.panels

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.inotter.travelcompanion.spatial.PanelBroadcastManager
import com.inotter.travelcompanion.spatial.TheatreStateHolder
import com.inotter.travelcompanion.spatial.data.EnvironmentType
import com.inotter.travelcompanion.spatial.ui.ControlsPanelCallback
import com.inotter.travelcompanion.spatial.ui.ControlsPanelContent
import com.inotter.travelcompanion.ui.theme.QuestTheme

/**
 * Separate activity for the Controls Panel.
 * Displays playback controls for the video player with lighting and environment settings.
 * 
 * Uses BroadcastReceiver for cross-process communication with ImmersiveActivity.
 */
class ControlsPanelActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "ControlsPanelActivity"
    }
    
    // Local state for the panel (since we can't share state across processes)
    private var showSettings by mutableStateOf(true) // Start with settings visible for testing
    private var lightingIntensity by mutableFloatStateOf(1f)
    private var currentEnvironment by mutableStateOf(EnvironmentType.COLLAB_ROOM)
    
    // Callback that sends broadcasts to ImmersiveActivity
    private val panelCallback = object : ControlsPanelCallback {
        override fun onPlayPause() {
            Log.d(TAG, "onPlayPause pressed - sending broadcast")
            PanelBroadcastManager.sendPlayPause(this@ControlsPanelActivity)
        }
        override fun onSeek(position: Float) {
            Log.d(TAG, "onSeek: $position - sending broadcast")
            PanelBroadcastManager.sendSeek(this@ControlsPanelActivity, position)
        }
        override fun onRewind() {
            Log.d(TAG, "onRewind pressed - sending broadcast")
            PanelBroadcastManager.sendRewind(this@ControlsPanelActivity)
        }
        override fun onFastForward() {
            Log.d(TAG, "onFastForward pressed - sending broadcast")
            PanelBroadcastManager.sendFastForward(this@ControlsPanelActivity)
        }
        override fun onRestart() {
            Log.d(TAG, "onRestart pressed - sending broadcast")
            PanelBroadcastManager.sendRestart(this@ControlsPanelActivity)
        }
        override fun onMuteToggle() {
            Log.d(TAG, "onMuteToggle pressed - sending broadcast")
            PanelBroadcastManager.sendMuteToggle(this@ControlsPanelActivity)
        }
        override fun onClose() {
            Log.d(TAG, "onClose pressed - sending broadcast")
            PanelBroadcastManager.sendClose(this@ControlsPanelActivity)
        }
        override fun onLightingChanged(intensity: Float) {
            Log.d(TAG, "onLightingChanged: $intensity - sending broadcast")
            lightingIntensity = intensity
            PanelBroadcastManager.sendLightingChanged(this@ControlsPanelActivity, intensity)
        }
        override fun onEnvironmentChanged(environment: EnvironmentType) {
            Log.d(TAG, "onEnvironmentChanged: $environment - sending broadcast")
            currentEnvironment = environment
            PanelBroadcastManager.sendEnvironmentChanged(this@ControlsPanelActivity, environment)
        }
        override fun onToggleSettings() {
            Log.d(TAG, "onToggleSettings pressed")
            showSettings = !showSettings
            // Also notify ImmersiveActivity
            PanelBroadcastManager.sendToggleSettings(this@ControlsPanelActivity)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ControlsPanelActivity created")

        setContent {
            QuestTheme {
                // Observe shared playback state from TheatreStateHolder
                val sharedState by TheatreStateHolder.playbackState.collectAsState()

                // Merge shared state with local state
                val playbackState = sharedState.copy(
                    showSettings = showSettings,
                    lightingIntensity = lightingIntensity,
                    currentEnvironment = currentEnvironment
                )

                // Use the shared ControlsPanelContent composable
                ControlsPanelContent(
                    playbackState = playbackState,
                    callback = panelCallback
                )
            }
        }
    }
}
