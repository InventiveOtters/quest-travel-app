package com.inotter.onthegovr.sync.server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import java.io.File
import java.net.BindException

/**
 * HTTP server for streaming video files to client devices.
 * 
 * Uses Jetty HTTP server with VideoStreamingServlet for range request support.
 * Supports multiple video formats and concurrent client connections.
 * 
 * Usage:
 * ```
 * val server = HttpMovieServer(context)
 * val port = server.start()
 * server.registerVideo("movie1", File("/path/to/movie.mp4"))
 * val url = server.getVideoUrl("movie1") // http://192.168.43.100:8080/video/movie1
 * // ... clients connect and stream
 * server.stop()
 * ```
 */
class HttpMovieServer(
    private val context: Context
) {
    companion object {
        private const val TAG = "HttpMovieServer"
        const val DEFAULT_PORT = 8080
        val FALLBACK_PORTS = listOf(8081, 8082, 8083, 8084, 8085)
        
        /**
         * Create server with automatic port fallback.
         * Tries DEFAULT_PORT first, then FALLBACK_PORTS if occupied.
         */
        fun createWithFallbackPorts(context: Context): Pair<HttpMovieServer, Int> {
            val server = HttpMovieServer(context)
            val portsToTry = listOf(DEFAULT_PORT) + FALLBACK_PORTS
            
            for (port in portsToTry) {
                try {
                    server.start(port)
                    Log.i(TAG, "Server started on port $port")
                    return Pair(server, port)
                } catch (e: BindException) {
                    Log.w(TAG, "Port $port in use, trying next...")
                    continue
                } catch (e: Exception) {
                    if (e.cause is BindException) continue
                    throw e
                }
            }
            throw BindException("All ports are in use: $portsToTry")
        }
    }

    private var jettyServer: Server? = null
    private var videoServlet: VideoStreamingServlet? = null
    private var currentPort: Int = DEFAULT_PORT

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _registeredVideos = MutableStateFlow<Map<String, String>>(emptyMap())
    val registeredVideos: StateFlow<Map<String, String>> = _registeredVideos.asStateFlow()

    /**
     * Start the HTTP server on the specified port.
     * @param port Port to bind to (default: 8080)
     * @throws BindException if port is already in use
     */
    fun start(port: Int = DEFAULT_PORT) {
        if (_isRunning.value) {
            Log.w(TAG, "Server already running on port $currentPort")
            return
        }

        try {
            currentPort = port
            
            // Create Jetty server
            val server = Server(port)
            val contextHandler = ServletContextHandler(ServletContextHandler.NO_SESSIONS)
            contextHandler.contextPath = "/"

            // Create and register video streaming servlet
            val servlet = VideoStreamingServlet()
            contextHandler.addServlet(ServletHolder(servlet), "/video/*")
            
            server.handler = contextHandler
            server.start()

            jettyServer = server
            videoServlet = servlet
            _isRunning.value = true

            Log.i(TAG, "HTTP movie server started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server on port $port", e)
            jettyServer = null
            videoServlet = null
            _isRunning.value = false
            throw e
        }
    }

    /**
     * Stop the HTTP server.
     */
    fun stop() {
        try {
            videoServlet?.clearVideos()
            jettyServer?.stop()
            jettyServer = null
            videoServlet = null
            _isRunning.value = false
            _registeredVideos.value = emptyMap()
            Log.i(TAG, "HTTP movie server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    /**
     * Register a video file for streaming.
     * @param movieId Unique identifier for the movie
     * @param videoFile File to stream
     * @return true if registered successfully
     */
    fun registerVideo(movieId: String, videoFile: File): Boolean {
        val servlet = videoServlet
        if (servlet == null) {
            Log.e(TAG, "Cannot register video: server not running")
            return false
        }

        if (!videoFile.exists() || !videoFile.canRead()) {
            Log.e(TAG, "Cannot register video: file not found or not readable: ${videoFile.absolutePath}")
            return false
        }

        servlet.registerVideo(movieId, videoFile)
        _registeredVideos.value = _registeredVideos.value + (movieId to videoFile.name)
        Log.i(TAG, "Registered video: $movieId -> ${videoFile.name}")
        return true
    }

    /**
     * Unregister a video file.
     * @param movieId Movie identifier to unregister
     */
    fun unregisterVideo(movieId: String) {
        videoServlet?.unregisterVideo(movieId)
        _registeredVideos.value = _registeredVideos.value - movieId
        Log.i(TAG, "Unregistered video: $movieId")
    }

    /**
     * Get the HTTP URL for a registered video.
     * @param movieId Movie identifier
     * @param serverIp Server IP address (e.g., "192.168.43.100")
     * @return HTTP URL (e.g., "http://192.168.43.100:8080/video/movie1")
     */
    fun getVideoUrl(movieId: String, serverIp: String): String {
        return "http://$serverIp:$currentPort/video/$movieId"
    }

    /**
     * Get the current server port.
     */
    fun getPort(): Int = currentPort

    /**
     * Check if server is alive and running.
     */
    fun isAlive(): Boolean = jettyServer?.isRunning == true && _isRunning.value
}

