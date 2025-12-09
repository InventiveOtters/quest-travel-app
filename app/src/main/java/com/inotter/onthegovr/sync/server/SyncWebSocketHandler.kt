package com.inotter.onthegovr.sync.server

import android.util.Log
import okhttp3.WebSocket
import okio.ByteString
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter

/**
 * WebSocket handler for individual client connections.
 * 
 * Handles incoming messages from clients and wraps the Jetty WebSocket session
 * to work with the existing OkHttp WebSocket interface used by SyncCommandServer.
 */
class SyncWebSocketHandler(
    private val clientId: String,
    private val syncServer: SyncCommandServer
) : WebSocketAdapter() {
    
    companion object {
        private const val TAG = "SyncWebSocketHandler"
    }
    
    // Wrapper to make Jetty Session compatible with OkHttp WebSocket interface
    private var webSocketWrapper: WebSocket? = null
    
    override fun onWebSocketConnect(session: Session) {
        super.onWebSocketConnect(session)
        Log.i(TAG, "Client connected: $clientId")
        
        // Create wrapper that implements OkHttp WebSocket interface
        webSocketWrapper = JettyWebSocketWrapper(session)
        
        // Register client with sync server
        syncServer.registerClient(clientId, webSocketWrapper!!)
    }
    
    override fun onWebSocketText(message: String) {
        super.onWebSocketText(message)
        Log.d(TAG, "Received message from $clientId: ${message.take(100)}")
        
        // Forward message to sync server for processing
        syncServer.handleClientMessage(clientId, message)
    }
    
    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        super.onWebSocketClose(statusCode, reason)
        Log.i(TAG, "Client disconnected: $clientId (code: $statusCode, reason: $reason)")
        
        // Unregister client from sync server
        syncServer.unregisterClient(clientId)
    }
    
    override fun onWebSocketError(cause: Throwable) {
        super.onWebSocketError(cause)
        Log.e(TAG, "WebSocket error for client $clientId", cause)
    }
    
    /**
     * Wrapper class to adapt Jetty WebSocket Session to OkHttp WebSocket interface.
     * This allows the existing SyncCommandServer code to work without changes.
     *
     * Note: This wrapper uses blocking send methods. The caller (MasterSyncCoordinator)
     * must ensure these methods are called from a background thread to avoid
     * NetworkOnMainThreadException.
     */
    private class JettyWebSocketWrapper(
        private val session: Session
    ) : WebSocket {

        override fun request() = throw UnsupportedOperationException("Not used in server mode")

        override fun queueSize(): Long = 0L

        override fun send(text: String): Boolean {
            return try {
                // Blocking send - must be called from background thread
                session.remote.sendString(text)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                false
            }
        }

        override fun send(bytes: ByteString): Boolean {
            return try {
                // Blocking send - must be called from background thread
                session.remote.sendBytes(java.nio.ByteBuffer.wrap(bytes.toByteArray()))
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send bytes", e)
                false
            }
        }

        override fun close(code: Int, reason: String?): Boolean {
            return try {
                session.close(code, reason)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close session", e)
                false
            }
        }

        override fun cancel() {
            try {
                session.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel session", e)
            }
        }
    }
}

