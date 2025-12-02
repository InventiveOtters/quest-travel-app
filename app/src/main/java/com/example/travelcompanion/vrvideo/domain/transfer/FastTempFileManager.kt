package com.example.travelcompanion.vrvideo.domain.transfer

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Custom TempFileManager that writes temporary files directly to the upload directory.
 *
 * This avoids the performance penalty of writing to Android's default temp directory
 * (which is on internal storage) and then copying to the final destination.
 *
 * Key optimizations:
 * 1. Files are written directly to the target upload directory
 * 2. Uses 256KB buffer size for optimal large file I/O throughput
 *    - Research shows 256KB-1MB provides optimal performance for sequential large file writes
 *    - 256KB is a good balance between memory usage and throughput
 *    - Default Java buffer size (8KB) causes too many system calls for large files
 * 3. Temp files are named with a prefix for easy identification
 * 4. Enables atomic rename instead of copy when moving to final location
 */
class FastTempFileManager(
    private val uploadDir: File
) : NanoHTTPD.TempFileManager {

    companion object {
        /** Prefix for temporary upload files */
        private const val TEMP_FILE_PREFIX = ".upload_temp_"

        /** Suffix for temporary upload files */
        private const val TEMP_FILE_SUFFIX = ".tmp"

        /**
         * Buffer size for writing uploaded files.
         * 256KB is optimal for large file sequential writes over network:
         * - Reduces system call overhead compared to default 8KB
         * - Good balance between memory usage and throughput
         * - Aligns well with typical filesystem block sizes
         */
        private const val BUFFER_SIZE = 256 * 1024 // 256KB
    }

    private val tempFiles = mutableListOf<FastTempFile>()

    init {
        // Ensure upload directory exists
        if (!uploadDir.exists()) {
            uploadDir.mkdirs()
        }
    }

    override fun createTempFile(filename_hint: String?): NanoHTTPD.TempFile {
        // Create temp file in the upload directory itself
        // This way, we can use rename() instead of copy() when moving to final location
        val tempFile = File.createTempFile(
            TEMP_FILE_PREFIX,
            TEMP_FILE_SUFFIX,
            uploadDir
        )
        
        val fastTempFile = FastTempFile(tempFile)
        tempFiles.add(fastTempFile)
        return fastTempFile
    }

    override fun clear() {
        for (tempFile in tempFiles) {
            try {
                tempFile.delete()
            } catch (e: Exception) {
                // Log but don't fail - temp files will be cleaned up eventually
                android.util.Log.w("FastTempFileManager", "Failed to delete temp file: ${e.message}")
            }
        }
        tempFiles.clear()
    }

    /**
     * Custom TempFile implementation with optimized I/O.
     */
    inner class FastTempFile(
        private val file: File
    ) : NanoHTTPD.TempFile {

        // Use a buffered output stream with larger buffer for better performance
        private var outputStream: OutputStream? = null

        override fun getName(): String = file.absolutePath

        override fun open(): OutputStream {
            // Use 256KB buffer for optimal large file I/O performance
            val fos = FileOutputStream(file)
            outputStream = java.io.BufferedOutputStream(fos, BUFFER_SIZE)
            return outputStream!!
        }

        override fun delete() {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
            if (file.exists() && !file.delete()) {
                // Schedule for deletion on exit if immediate delete fails
                file.deleteOnExit()
            }
        }
    }

    /**
     * Factory for creating FastTempFileManager instances.
     */
    class Factory(
        private val uploadDir: File
    ) : NanoHTTPD.TempFileManagerFactory {
        override fun create(): NanoHTTPD.TempFileManager {
            return FastTempFileManager(uploadDir)
        }
    }
}

