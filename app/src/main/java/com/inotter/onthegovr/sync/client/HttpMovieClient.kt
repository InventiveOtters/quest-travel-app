package com.inotter.onthegovr.sync.client

import android.net.Uri
import android.util.Log
import com.inotter.onthegovr.playback.PlaybackCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HTTP client for streaming video from a remote server.
 * 
 * Wraps PlaybackCore (ExoPlayer) to provide a simple interface for connecting
 * to HTTP video streams with range request support.
 * 
 * Usage:
 * ```
 * val client = HttpMovieClient(playbackCore)
 * client.connectToServer("http://192.168.43.100:8080/video/movie1")
 * // ExoPlayer will handle streaming and seeking automatically
 * client.disconnect()
 * ```
 */
class HttpMovieClient(
    private val playbackCore: PlaybackCore
) {
    companion object {
        private const val TAG = "HttpMovieClient"
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var currentUrl: String? = null

    /**
     * Connection states.
     */
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        data class Connecting(val url: String) : ConnectionState()
        data class Connected(val url: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * Connect to HTTP video stream.
     * 
     * @param httpUrl Full HTTP URL to video (e.g., "http://192.168.43.100:8080/video/movie1")
     * @param startPositionMs Optional start position in milliseconds
     * @return true if connection initiated successfully
     */
    fun connectToServer(httpUrl: String, startPositionMs: Long = 0L): Boolean {
        if (_connectionState.value is ConnectionState.Connected) {
            Log.w(TAG, "Already connected to ${currentUrl}, disconnecting first")
            disconnect()
        }

        try {
            Log.i(TAG, "Connecting to HTTP stream: $httpUrl")
            _connectionState.value = ConnectionState.Connecting(httpUrl)

            // Parse HTTP URL to Uri
            val uri = Uri.parse(httpUrl)

            // Prepare ExoPlayer with the HTTP stream
            playbackCore.prepare(uri, startPositionMs)

            currentUrl = httpUrl
            _connectionState.value = ConnectionState.Connected(httpUrl)
            Log.i(TAG, "Connected to HTTP stream: $httpUrl")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to HTTP stream: $httpUrl", e)
            _connectionState.value = ConnectionState.Error("Connection failed: ${e.message}")
            currentUrl = null
            return false
        }
    }

    /**
     * Disconnect from current stream.
     */
    fun disconnect() {
        try {
            if (currentUrl != null) {
                Log.i(TAG, "Disconnecting from: $currentUrl")
                playbackCore.stop()
                currentUrl = null
                _connectionState.value = ConnectionState.Disconnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }

    /**
     * Check if currently connected to a stream.
     */
    fun isConnected(): Boolean {
        return _connectionState.value is ConnectionState.Connected
    }

    /**
     * Get the current stream URL.
     */
    fun getCurrentUrl(): String? = currentUrl

    /**
     * Get the current playback position.
     */
    fun getCurrentPosition(): Long {
        return playbackCore.getCurrentPosition()
    }

    /**
     * Check if playback is active.
     */
    fun isPlaying(): Boolean {
        return playbackCore.isPlaying()
    }

    /**
     * Get the video duration.
     */
    fun getDuration(): Long {
        return playbackCore.getDuration()
    }
}

