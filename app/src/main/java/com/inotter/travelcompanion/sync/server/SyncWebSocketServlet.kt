package com.inotter.travelcompanion.sync.server

import android.util.Log
import org.eclipse.jetty.websocket.servlet.WebSocketServlet
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory

/**
 * Jetty WebSocket servlet for accepting sync client connections.
 * 
 * Handles WebSocket upgrade requests and creates SyncWebSocketHandler instances
 * for each connected client.
 */
class SyncWebSocketServlet(
    private val syncServer: SyncCommandServer
) : WebSocketServlet() {
    
    companion object {
        private const val TAG = "SyncWebSocketServlet"
        private const val IDLE_TIMEOUT_MS = 300000L // 5 minutes
    }
    
    override fun configure(factory: WebSocketServletFactory) {
        // Configure WebSocket factory
        factory.policy.idleTimeout = IDLE_TIMEOUT_MS
        factory.policy.maxTextMessageSize = 64 * 1024 // 64KB
        
        // Register WebSocket handler creator
        factory.setCreator { req, resp ->
            val clientId = req.getHeader("X-Client-Id") ?: "unknown-${System.currentTimeMillis()}"
            Log.i(TAG, "Creating WebSocket handler for client: $clientId")
            SyncWebSocketHandler(clientId, syncServer)
        }
        
        Log.i(TAG, "WebSocket servlet configured")
    }
}

