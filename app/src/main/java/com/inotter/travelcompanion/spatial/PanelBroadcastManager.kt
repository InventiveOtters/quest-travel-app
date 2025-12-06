package com.inotter.travelcompanion.spatial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.inotter.travelcompanion.spatial.data.EnvironmentType

/**
 * Handles cross-process communication between panel activities and ImmersiveActivity.
 * 
 * Panel activities run in a separate process in Meta Spatial SDK, so we use
 * Android BroadcastReceiver for IPC instead of in-memory singletons.
 */
object PanelBroadcastManager {
    
    private const val TAG = "PanelBroadcastManager"
    
    // Action constants
    const val ACTION_PLAY_PAUSE = "com.inotter.travelcompanion.ACTION_PLAY_PAUSE"
    const val ACTION_SEEK = "com.inotter.travelcompanion.ACTION_SEEK"
    const val ACTION_REWIND = "com.inotter.travelcompanion.ACTION_REWIND"
    const val ACTION_FAST_FORWARD = "com.inotter.travelcompanion.ACTION_FAST_FORWARD"
    const val ACTION_RESTART = "com.inotter.travelcompanion.ACTION_RESTART"
    const val ACTION_MUTE_TOGGLE = "com.inotter.travelcompanion.ACTION_MUTE_TOGGLE"
    const val ACTION_CLOSE = "com.inotter.travelcompanion.ACTION_CLOSE"
    const val ACTION_LIGHTING_CHANGED = "com.inotter.travelcompanion.ACTION_LIGHTING_CHANGED"
    const val ACTION_ENVIRONMENT_CHANGED = "com.inotter.travelcompanion.ACTION_ENVIRONMENT_CHANGED"
    const val ACTION_TOGGLE_SETTINGS = "com.inotter.travelcompanion.ACTION_TOGGLE_SETTINGS"
    
    // Extra keys
    const val EXTRA_POSITION = "position"
    const val EXTRA_INTENSITY = "intensity"
    const val EXTRA_ENVIRONMENT = "environment"
    
    /**
     * Interface for receiving panel commands in ImmersiveActivity.
     */
    interface PanelCommandListener {
        fun onPlayPause()
        fun onSeek(position: Float)
        fun onRewind()
        fun onFastForward()
        fun onRestart()
        fun onMuteToggle()
        fun onClose()
        fun onLightingChanged(intensity: Float)
        fun onEnvironmentChanged(environment: EnvironmentType)
        fun onToggleSettings()
    }
    
    /**
     * BroadcastReceiver that handles commands from panel activities.
     * Register this in ImmersiveActivity.
     */
    class PanelCommandReceiver(private val listener: PanelCommandListener) : BroadcastReceiver() {
        
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received broadcast: ${intent?.action}")
            
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> listener.onPlayPause()
                ACTION_SEEK -> {
                    val position = intent.getFloatExtra(EXTRA_POSITION, 0f)
                    listener.onSeek(position)
                }
                ACTION_REWIND -> listener.onRewind()
                ACTION_FAST_FORWARD -> listener.onFastForward()
                ACTION_RESTART -> listener.onRestart()
                ACTION_MUTE_TOGGLE -> listener.onMuteToggle()
                ACTION_CLOSE -> listener.onClose()
                ACTION_LIGHTING_CHANGED -> {
                    val intensity = intent.getFloatExtra(EXTRA_INTENSITY, 1f)
                    Log.d(TAG, "Lighting changed broadcast received: $intensity")
                    listener.onLightingChanged(intensity)
                }
                ACTION_ENVIRONMENT_CHANGED -> {
                    val envName = intent.getStringExtra(EXTRA_ENVIRONMENT) ?: EnvironmentType.COLLAB_ROOM.name
                    val environment = try {
                        EnvironmentType.valueOf(envName)
                    } catch (e: Exception) {
                        EnvironmentType.COLLAB_ROOM
                    }
                    Log.d(TAG, "Environment changed broadcast received: $environment")
                    listener.onEnvironmentChanged(environment)
                }
                ACTION_TOGGLE_SETTINGS -> listener.onToggleSettings()
            }
        }
        
        /**
         * Get the IntentFilter for all panel actions.
         */
        fun getIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(ACTION_PLAY_PAUSE)
                addAction(ACTION_SEEK)
                addAction(ACTION_REWIND)
                addAction(ACTION_FAST_FORWARD)
                addAction(ACTION_RESTART)
                addAction(ACTION_MUTE_TOGGLE)
                addAction(ACTION_CLOSE)
                addAction(ACTION_LIGHTING_CHANGED)
                addAction(ACTION_ENVIRONMENT_CHANGED)
                addAction(ACTION_TOGGLE_SETTINGS)
            }
        }
    }
    
    // Sender functions for panel activities
    
    fun sendPlayPause(context: Context) {
        Log.d(TAG, "Sending play/pause broadcast")
        context.sendBroadcast(Intent(ACTION_PLAY_PAUSE).setPackage(context.packageName))
    }
    
    fun sendSeek(context: Context, position: Float) {
        Log.d(TAG, "Sending seek broadcast: $position")
        context.sendBroadcast(Intent(ACTION_SEEK).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_POSITION, position)
        })
    }
    
    fun sendRewind(context: Context) {
        Log.d(TAG, "Sending rewind broadcast")
        context.sendBroadcast(Intent(ACTION_REWIND).setPackage(context.packageName))
    }
    
    fun sendFastForward(context: Context) {
        Log.d(TAG, "Sending fast forward broadcast")
        context.sendBroadcast(Intent(ACTION_FAST_FORWARD).setPackage(context.packageName))
    }
    
    fun sendRestart(context: Context) {
        Log.d(TAG, "Sending restart broadcast")
        context.sendBroadcast(Intent(ACTION_RESTART).setPackage(context.packageName))
    }
    
    fun sendMuteToggle(context: Context) {
        Log.d(TAG, "Sending mute toggle broadcast")
        context.sendBroadcast(Intent(ACTION_MUTE_TOGGLE).setPackage(context.packageName))
    }
    
    fun sendClose(context: Context) {
        Log.d(TAG, "Sending close broadcast")
        context.sendBroadcast(Intent(ACTION_CLOSE).setPackage(context.packageName))
    }
    
    fun sendLightingChanged(context: Context, intensity: Float) {
        Log.d(TAG, "Sending lighting changed broadcast: $intensity")
        context.sendBroadcast(Intent(ACTION_LIGHTING_CHANGED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_INTENSITY, intensity)
        })
    }
    
    fun sendEnvironmentChanged(context: Context, environment: EnvironmentType) {
        Log.d(TAG, "Sending environment changed broadcast: $environment")
        context.sendBroadcast(Intent(ACTION_ENVIRONMENT_CHANGED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ENVIRONMENT, environment.name)
        })
    }
    
    fun sendToggleSettings(context: Context) {
        Log.d(TAG, "Sending toggle settings broadcast")
        context.sendBroadcast(Intent(ACTION_TOGGLE_SETTINGS).setPackage(context.packageName))
    }
}
