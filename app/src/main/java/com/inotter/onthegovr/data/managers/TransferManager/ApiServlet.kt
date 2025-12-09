package com.inotter.onthegovr.data.managers.TransferManager

import android.content.Context
import org.json.JSONObject
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Servlet for API endpoints (status, PIN verification).
 *
 * Handles:
 * - GET /api/status - Server status and storage info
 * - POST /api/verify-pin - PIN verification
 */
class ApiServlet(
    private val context: Context,
    private val pinVerifier: (String) -> Boolean = { true },
    private val isPinEnabled: () -> Boolean = { false }
) : HttpServlet() {

    companion object {
        private const val TAG = "ApiServlet"
        private const val MIME_JSON = "application/json"
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val path = req.pathInfo ?: "/"

        when (path) {
            "/status" -> handleStatus(resp)
            else -> {
                resp.status = HttpServletResponse.SC_NOT_FOUND
                resp.contentType = MIME_JSON
                resp.writer.write("""{"error": "Not found: $path"}""")
            }
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val path = req.pathInfo ?: "/"

        when (path) {
            "/verify-pin" -> handleVerifyPin(req, resp)
            else -> {
                resp.status = HttpServletResponse.SC_NOT_FOUND
                resp.contentType = MIME_JSON
                resp.writer.write("""{"error": "Not found: $path"}""")
            }
        }
    }

    /**
     * Handles GET /api/status - returns server status and storage info.
     */
    private fun handleStatus(resp: HttpServletResponse) {
        val availableStorage = FileValidator.getAvailableStorage(context)

        val json = JSONObject().apply {
            put("running", true)
            put("storageAvailable", availableStorage)
            put("storageAvailableFormatted", FileValidator.formatBytes(availableStorage))
            put("pinRequired", isPinEnabled())
            put("tusEnabled", true)
        }

        resp.status = HttpServletResponse.SC_OK
        resp.contentType = MIME_JSON
        resp.writer.write(json.toString())

        android.util.Log.d(TAG, "Status request: storage=${FileValidator.formatBytes(availableStorage)}")
    }

    /**
     * Handles POST /api/verify-pin - verifies the provided PIN.
     */
    private fun handleVerifyPin(req: HttpServletRequest, resp: HttpServletResponse) {
        // Read PIN from request body or parameters
        val pin = extractPin(req)

        val isValid = pinVerifier(pin.trim())

        val json = JSONObject().apply {
            put("success", isValid)
            if (!isValid) {
                put("error", "Invalid PIN")
            }
        }

        resp.status = if (isValid) HttpServletResponse.SC_OK else HttpServletResponse.SC_UNAUTHORIZED
        resp.contentType = MIME_JSON
        resp.writer.write(json.toString())

        android.util.Log.d(TAG, "PIN verification: ${if (isValid) "success" else "failed"}")
    }

    /**
     * Extracts PIN from various request formats.
     */
    private fun extractPin(req: HttpServletRequest): String {
        // Try form parameter first
        val paramPin = req.getParameter("pin")
        if (!paramPin.isNullOrEmpty()) {
            return paramPin
        }

        // Try reading from request body
        return try {
            req.reader.readText().let { body ->
                // Check if it's JSON
                if (body.startsWith("{")) {
                    JSONObject(body).optString("pin", "")
                } else {
                    // Plain text or form-encoded
                    body.substringAfter("pin=", "").substringBefore("&")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to extract PIN from request", e)
            ""
        }
    }
}

