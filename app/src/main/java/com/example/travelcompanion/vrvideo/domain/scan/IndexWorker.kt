package com.example.travelcompanion.vrvideo.domain.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.travelcompanion.vrvideo.data.db.VideoItem
import com.example.travelcompanion.vrvideo.data.db.VideoLibraryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest

/**
 * WorkManager worker that scans a library folder and indexes video files.
 * Computes content signatures for de-duplication, generates thumbnails,
 * and marks missing files as unavailable.
 *
 * @param appContext Android application context
 * @param params Worker parameters containing tree URI and folder ID
 */
class IndexWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val treeUriStr = inputData.getString(KEY_TREE_URI) ?: return@withContext Result.failure()
    val folderId = inputData.getLong(KEY_FOLDER_ID, -1L).takeIf { it > 0 } ?: return@withContext Result.failure()
    val treeUri = Uri.parse(treeUriStr)
    val db = VideoLibraryDatabase.getInstance(applicationContext)
    val dao = db.videoItemDao()

    val root = DocumentFile.fromTreeUri(applicationContext, treeUri) ?: return@withContext Result.failure()
    val now = System.currentTimeMillis()

    // Track found file URIs during this scan
    val foundUris = mutableSetOf<String>()

    fun isSupported(name: String?): Boolean {
      val n = name?.lowercase() ?: return false
      return n.endsWith(".mp4") || n.endsWith(".mkv")
    }

    suspend fun scan(file: DocumentFile) {
      if (!file.isFile || !isSupported(file.name)) return
      val uri = file.uri
      val size = file.length()
      val sig = computeSignature(applicationContext, uri, size)
      val existing = dao.findBySignature(sig)

      // Track this URI as found
      foundUris.add(uri.toString())

      // Generate thumbnail and extract duration if not already present
      // Only skip regeneration if we have BOTH a valid thumbnail AND duration > 0
      val (thumbnailPath, durationMs) = if (existing?.thumbnailPath != null && existing.durationMs > 0) {
        existing.thumbnailPath to existing.durationMs
      } else {
        // Regenerate - always use the new result (overwrites old black thumbnails)
        val result = com.example.travelcompanion.vrvideo.domain.thumb.ThumbnailGenerator.generate(
            applicationContext,
            uri,
            sig.take(16)
        )
        result.thumbnailPath to result.durationMs
      }

      val item = VideoItem(
          id = existing?.id ?: 0L,
          folderId = folderId,
          fileUri = uri.toString(),
          title = file.name ?: uri.lastPathSegment ?: "video",
          durationMs = durationMs,
          sizeBytes = size,
          contentSignature = sig,
          createdAt = existing?.createdAt ?: now,
          unavailable = false,
          thumbnailPath = thumbnailPath,
      )
      dao.insertOrReplace(item)
    }

    suspend fun walk(dir: DocumentFile) {
      dir.listFiles().forEach { f ->
        if (f.isDirectory) walk(f) else scan(f)
      }
    }

    walk(root)

    // Mark missing items as unavailable by comparing previous set under this folderId
    val allItemsInFolder = dao.getByFolderId(folderId)
    val missingIds = allItemsInFolder
        .filter { it.fileUri !in foundUris }
        .map { it.id }

    if (missingIds.isNotEmpty()) {
      dao.markUnavailable(missingIds, true)
    }

    Result.success()
  }

  companion object {
    const val KEY_TREE_URI = "tree_uri"
    const val KEY_FOLDER_ID = "folder_id"

    /**
     * Creates input data for the IndexWorker.
     *
     * @param treeUri SAF document tree URI to scan
     * @param folderId Database ID of the library folder
     * @return WorkManager Data object with the required parameters
     */
    fun inputData(treeUri: Uri, folderId: Long): Data =
        Data.Builder().putString(KEY_TREE_URI, treeUri.toString()).putLong(KEY_FOLDER_ID, folderId).build()
  }
}

private fun computeSignature(context: Context, uri: Uri, size: Long): String {
  val chunk = 8L * 1024L * 1024L // 8 MiB
  val first = readSegment(context, uri, 0L, minOf(size, chunk))
  val lastStart = if (size > chunk) size - chunk else 0L
  val last = if (lastStart > 0L) readSegment(context, uri, lastStart, size - lastStart) else byteArrayOf()
  val md = MessageDigest.getInstance("SHA-256")
  md.update(first)
  md.update(last)
  val hash = md.digest().joinToString("") { b -> "%02x".format(b) }
  return "$hash:$size"
}

private fun readSegment(context: Context, uri: Uri, start: Long, length: Long): ByteArray {
  context.contentResolver.openInputStream(uri).use { input ->
    if (input == null) return byteArrayOf()
    skipFully(input, start)
    val buf = ByteArray(length.toInt())
    var total = 0
    while (total < buf.size) {
      val read = input.read(buf, total, buf.size - total)
      if (read <= 0) break
      total += read
    }
    return if (total == buf.size) buf else buf.copyOf(total)
  }
}

private fun skipFully(input: InputStream, toSkip: Long) {
  var remaining = toSkip
  while (remaining > 0) {
    val skipped = input.skip(remaining)
    if (skipped <= 0) break
    remaining -= skipped
  }
}

