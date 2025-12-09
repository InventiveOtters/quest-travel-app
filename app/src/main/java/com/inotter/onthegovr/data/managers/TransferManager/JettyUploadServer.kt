package com.inotter.onthegovr.data.managers.TransferManager

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.desair.tus.server.TusFileUploadService
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder

class JettyUploadServer(
    private val context: Context,
    private val port: Int = DEFAULT_PORT,
    private val tusService: TusFileUploadService,
    private val uploadHandler: TusUploadHandler? = null,
    private val pinVerifier: (String) -> Boolean = { true },
    private val isPinEnabled: () -> Boolean = { false },
    private val onFileUploaded: (android.net.Uri) -> Unit = {},
    private val tusDataDir: java.io.File? = null
) {
    companion object {
        const val DEFAULT_PORT = 8080
        val FALLBACK_PORTS = listOf(8081, 8082, 8083, 8084, 8085, 8088, 8089, 8090)
        private const val TAG = "JettyUploadServer"

        fun createWithFallbackPorts(
            context: Context,
            tusService: TusFileUploadService,
            uploadHandler: TusUploadHandler? = null,
            pinVerifier: (String) -> Boolean = { true },
            isPinEnabled: () -> Boolean = { false },
            onFileUploaded: (android.net.Uri) -> Unit = {},
            tusDataDir: java.io.File? = null
        ): Pair<JettyUploadServer, Int> {
            val portsToTry = listOf(DEFAULT_PORT) + FALLBACK_PORTS
            for (port in portsToTry) {
                try {
                    val server = JettyUploadServer(
                        context, port, tusService, uploadHandler,
                        pinVerifier, isPinEnabled, onFileUploaded, tusDataDir
                    )
                    server.start()
                    android.util.Log.i(TAG, "Server started on port $port")
                    return Pair(server, port)
                } catch (e: java.net.BindException) {
                    android.util.Log.w(TAG, "Port $port in use, trying next...")
                    continue
                } catch (e: Exception) {
                    if (e.cause is java.net.BindException) continue
                    throw e
                }
            }
            throw java.net.BindException("All ports are in use: $portsToTry")
        }
    }

    private var server: Server? = null

    data class UploadedFile(
        val name: String,
        val size: Long,
        val uploadedAt: Long = System.currentTimeMillis()
    )

    private val _uploadedFiles = MutableStateFlow<List<UploadedFile>>(emptyList())
    val uploadedFiles: StateFlow<List<UploadedFile>> = _uploadedFiles.asStateFlow()

    private val _lastActivityTime = MutableStateFlow(System.currentTimeMillis())
    val lastActivityTime: StateFlow<Long> = _lastActivityTime.asStateFlow()

    private var uploadCount = 0

    val isAlive: Boolean get() = server?.isRunning == true

    fun start() {
        if (server?.isRunning == true) return

        val jettyServer = Server(port)
        val contextHandler = ServletContextHandler(ServletContextHandler.NO_SESSIONS)
        contextHandler.contextPath = "/"

        val tusServlet = TusUploadServlet(tusService, uploadHandler, pinVerifier, isPinEnabled, tusDataDir)
        contextHandler.addServlet(ServletHolder(tusServlet), "/tus/*")

        val apiServlet = ApiServlet(context, pinVerifier, isPinEnabled)
        contextHandler.addServlet(ServletHolder(apiServlet), "/api/*")

        val staticServlet = StaticAssetsServlet(context)
        contextHandler.addServlet(ServletHolder(staticServlet), "/*")

        jettyServer.handler = contextHandler
        jettyServer.start()
        server = jettyServer

        android.util.Log.i(TAG, "Jetty server started on port $port")
    }

    fun stop() {
        try {
            server?.stop()
            server = null
            android.util.Log.i(TAG, "Jetty server stopped")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping server", e)
        }
    }

    fun getUploadCount(): Int = uploadCount

    fun addUploadedFile(filename: String, size: Long) {
        uploadCount++
        _uploadedFiles.value = listOf(UploadedFile(filename, size)) + _uploadedFiles.value
        _lastActivityTime.value = System.currentTimeMillis()
    }

    fun clearUploadHistory() {
        _uploadedFiles.value = emptyList()
        uploadCount = 0
    }
}

