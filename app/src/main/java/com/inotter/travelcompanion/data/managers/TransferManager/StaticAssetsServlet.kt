package com.inotter.travelcompanion.data.managers.TransferManager

import android.content.Context
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Servlet for serving static web assets from Android assets folder.
 *
 * Serves files from assets/transfer/ directory:
 * - index.html (main page)
 * - style.css (styles)
 * - upload.js (upload logic)
 * - tus.min.js (TUS client library)
 */
class StaticAssetsServlet(
    private val context: Context
) : HttpServlet() {

    companion object {
        private const val TAG = "StaticAssetsServlet"
        private const val ASSETS_BASE_PATH = "transfer"

        private const val MIME_HTML = "text/html"
        private const val MIME_CSS = "text/css"
        private const val MIME_JS = "application/javascript"
        private const val MIME_JSON = "application/json"
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val path = req.pathInfo ?: req.servletPath ?: "/"

        // Map request path to asset file
        val assetPath = when {
            path == "/" || path == "/index.html" -> "$ASSETS_BASE_PATH/index.html"
            path == "/style.css" -> "$ASSETS_BASE_PATH/style.css"
            path == "/upload.js" -> "$ASSETS_BASE_PATH/upload.js"
            path == "/tus.min.js" -> "$ASSETS_BASE_PATH/tus.min.js"
            path.startsWith("/assets/") -> path.removePrefix("/assets/")
            else -> "$ASSETS_BASE_PATH${path}"
        }

        try {
            val inputStream = context.assets.open(assetPath)

            // Set content type
            resp.contentType = getMimeType(assetPath)

            // Set cache headers
            if (assetPath.endsWith(".html")) {
                resp.setHeader("Cache-Control", "no-cache")
            } else {
                resp.setHeader("Cache-Control", "max-age=3600")
            }

            // Stream file content
            inputStream.use { input ->
                resp.outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            android.util.Log.d(TAG, "Served: $assetPath")

        } catch (e: java.io.FileNotFoundException) {
            android.util.Log.w(TAG, "Asset not found: $assetPath")
            resp.status = HttpServletResponse.SC_NOT_FOUND
            resp.contentType = MIME_JSON
            resp.writer.write("""{"error": "Not found: $path"}""")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error serving asset: $assetPath", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = MIME_JSON
            resp.writer.write("""{"error": "Server error: ${e.message}"}""")
        }
    }

    /**
     * Determines MIME type based on file extension.
     */
    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") -> MIME_HTML
            path.endsWith(".css") -> MIME_CSS
            path.endsWith(".js") -> MIME_JS
            path.endsWith(".json") -> MIME_JSON
            path.endsWith(".png") -> "image/png"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".ico") -> "image/x-icon"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".webp") -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}

