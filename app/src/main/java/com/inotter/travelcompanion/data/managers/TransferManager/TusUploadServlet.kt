package com.inotter.travelcompanion.data.managers.TransferManager

import me.desair.tus.server.TusFileUploadService
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Servlet wrapper for TUS file upload service.
 *
 * This thin wrapper adds PIN verification before delegating to tus-java-server.
 * All TUS protocol logic is handled by the library; we only:
 * 1. Check PIN for non-OPTIONS requests (if PIN protection is enabled)
 * 2. Delegate to TusFileUploadService for actual TUS handling
 * 3. Check for completed uploads after PATCH requests
 *
 * Handles endpoints:
 * - OPTIONS /tus/ - Capability discovery
 * - POST /tus/ - Create new upload
 * - HEAD /tus/{id} - Get upload offset (resume)
 * - PATCH /tus/{id} - Upload chunk
 * - DELETE /tus/{id} - Cancel upload
 */
class TusUploadServlet(
    private val tusService: TusFileUploadService,
    private val uploadHandler: TusUploadHandler? = null,
    private val pinVerifier: (String) -> Boolean = { true },
    private val isPinEnabled: () -> Boolean = { false }
) : HttpServlet() {

    companion object {
        private const val TAG = "TusUploadServlet"
        private const val PIN_HEADER = "X-Upload-Pin"
    }

    /**
     * Handle all HTTP methods by delegating to TUS service.
     * PIN check is performed for non-OPTIONS requests when enabled.
     */
    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
        android.util.Log.d(TAG, "TUS request: ${req.method} ${req.requestURI} (servletPath=${req.servletPath}, pathInfo=${req.pathInfo})")

        // OPTIONS requests don't require PIN (capability discovery)
        if (req.method != "OPTIONS" && isPinEnabled()) {
            val pin = req.getHeader(PIN_HEADER) ?: ""
            if (!pinVerifier(pin)) {
                android.util.Log.w(TAG, "PIN verification failed for ${req.method} ${req.requestURI}")
                resp.status = HttpServletResponse.SC_UNAUTHORIZED
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "PIN required. Please enter the PIN shown on the VR headset."}""")
                return
            }
        }

        try {
            // Delegate to TUS service - it handles all protocol logic
            tusService.process(req, resp)
            android.util.Log.d(TAG, "TUS request completed: ${req.method} ${req.requestURI} -> ${resp.status}")

            // After PATCH request, check if upload is complete
            if (req.method == "PATCH" && resp.status in 200..299) {
                checkUploadCompletion(req.requestURI)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "TUS processing error: ${e.message}", e)
            if (!resp.isCommitted) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.contentType = "application/json"
                resp.writer.write("""{"success": false, "error": "${e.message?.replace("\"", "'")}"}""")
            }
        }
    }

    private fun checkUploadCompletion(uploadUri: String) {
        try {
            val uploadInfo = tusService.getUploadInfo(uploadUri, null)
            uploadHandler?.checkAndProcessUpload(uploadInfo, uploadUri)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to check upload completion: ${e.message}")
        }
    }
}

