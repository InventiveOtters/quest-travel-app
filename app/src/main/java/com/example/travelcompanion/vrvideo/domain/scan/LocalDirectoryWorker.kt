package com.example.travelcompanion.vrvideo.domain.scan

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.travelcompanion.vrvideo.data.db.VideoItem
import com.example.travelcompanion.vrvideo.data.db.VideoLibraryDatabase
import com.example.travelcompanion.vrvideo.domain.thumb.ThumbnailGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * WorkManager worker that scans a local directory (not SAF) and indexes video files.
 * Used for indexing files uploaded via WiFi transfer to the app's internal storage.
 *
 * Unlike IndexWorker which uses SAF DocumentFile, this worker directly accesses
 * files in the app's internal storage directory.
 *
 * @param appContext Android application context
 * @param params Worker parameters containing directory path and folder ID
 */
class LocalDirectoryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val directoryPath = inputData.getString(KEY_DIRECTORY_PATH) 
            ?: return@withContext Result.failure()
        val folderId = inputData.getLong(KEY_FOLDER_ID, -1L)
            .takeIf { it > 0 } ?: return@withContext Result.failure()

        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext Result.failure()
        }

        val db = VideoLibraryDatabase.getInstance(applicationContext)
        val dao = db.videoItemDao()
        val now = System.currentTimeMillis()

        // Track found file paths during this scan
        val foundPaths = mutableSetOf<String>()

        // Scan all video files in the directory
        directory.listFiles()?.forEach { file ->
            if (file.isFile && isSupported(file.name)) {
                scanFile(file, folderId, dao, now, foundPaths)
            }
        }

        // Mark missing items as unavailable
        val allItemsInFolder = dao.getByFolderId(folderId)
        val missingIds = allItemsInFolder
            .filter { it.fileUri !in foundPaths }
            .map { it.id }

        if (missingIds.isNotEmpty()) {
            dao.markUnavailable(missingIds, true)
        }

        Result.success()
    }

    private suspend fun scanFile(
        file: File,
        folderId: Long,
        dao: com.example.travelcompanion.vrvideo.data.dao.VideoItemDao,
        now: Long,
        foundPaths: MutableSet<String>
    ) {
        val fileUri = Uri.fromFile(file)
        val size = file.length()
        val sig = computeSignatureForFile(file, size)
        val existing = dao.findBySignature(sig)

        // Track this file path as found
        foundPaths.add(fileUri.toString())

        // Generate thumbnail and extract duration if not already present
        val (thumbnailPath, durationMs) = if (existing?.thumbnailPath != null && existing.durationMs > 0) {
            existing.thumbnailPath to existing.durationMs
        } else {
            val result = ThumbnailGenerator.generate(
                applicationContext,
                fileUri,
                sig.take(16)
            )
            result.thumbnailPath to result.durationMs
        }

        val item = VideoItem(
            id = existing?.id ?: 0L,
            folderId = folderId,
            fileUri = fileUri.toString(),
            title = file.name,
            durationMs = durationMs,
            sizeBytes = size,
            contentSignature = sig,
            createdAt = existing?.createdAt ?: now,
            unavailable = false,
            thumbnailPath = thumbnailPath,
        )
        dao.insertOrReplace(item)
    }

    private fun isSupported(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        return n.endsWith(".mp4") || n.endsWith(".mkv")
    }

    /**
     * Computes content signature for a local file.
     * Uses first/last 8MiB + size for de-duplication.
     */
    private fun computeSignatureForFile(file: File, size: Long): String {
        val chunk = 8L * 1024L * 1024L // 8 MiB
        val first = readFileSegment(file, 0L, minOf(size, chunk))
        val lastStart = if (size > chunk) size - chunk else 0L
        val last = if (lastStart > 0L) readFileSegment(file, lastStart, size - lastStart) else byteArrayOf()
        val md = MessageDigest.getInstance("SHA-256")
        md.update(first)
        md.update(last)
        val hash = md.digest().joinToString("") { b -> "%02x".format(b) }
        return "$hash:$size"
    }

    private fun readFileSegment(file: File, start: Long, length: Long): ByteArray {
        return try {
            FileInputStream(file).use { input ->
                input.skip(start)
                val buf = ByteArray(length.toInt())
                var total = 0
                while (total < buf.size) {
                    val read = input.read(buf, total, buf.size - total)
                    if (read <= 0) break
                    total += read
                }
                if (total == buf.size) buf else buf.copyOf(total)
            }
        } catch (e: Exception) {
            byteArrayOf()
        }
    }

    companion object {
        const val KEY_DIRECTORY_PATH = "directory_path"
        const val KEY_FOLDER_ID = "folder_id"

        /**
         * Creates input data for the LocalDirectoryWorker.
         *
         * @param directoryPath Absolute path to the directory to scan
         * @param folderId Database ID of the library folder
         * @return WorkManager Data object with the required parameters
         */
        fun inputData(directoryPath: String, folderId: Long): Data =
            Data.Builder()
                .putString(KEY_DIRECTORY_PATH, directoryPath)
                .putLong(KEY_FOLDER_ID, folderId)
                .build()
    }
}

