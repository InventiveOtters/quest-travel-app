package com.example.travelcompanion.vrvideo.data.repo

import com.example.travelcompanion.vrvideo.data.db.StereoLayout
import com.example.travelcompanion.vrvideo.data.db.VideoItem
import com.example.travelcompanion.vrvideo.data.db.VideoLibraryDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing video items in the library.
 * Provides operations to query, update, and delete video items.
 *
 * @property db Video library database instance
 */
class VideoRepository(
    private val db: VideoLibraryDatabase,
) {
  private val dao = db.videoItemDao()

  /**
   * Returns a Flow of all video items, ordered by creation date (newest first).
   *
   * @return Flow emitting list of video items
   */
  fun queryVideos(): Flow<List<VideoItem>> = dao.getAll()

  /**
   * Deletes a video item from the database by ID.
   * The actual video file is not deleted from storage.
   *
   * @param id The video item ID to delete
   */
  suspend fun deleteById(id: Long) = dao.deleteById(id)

  /**
   * Sets or clears the stereo layout override for a video.
   *
   * @param id The video item ID
   * @param layout The stereo layout to set, or null to clear the override
   */
  suspend fun setStereoLayoutOverride(id: Long, layout: StereoLayout?) = dao.setStereoLayoutOverride(id, layout)

  /**
   * Updates the playback progress for a video.
   *
   * @param id The video item ID
   * @param lastPlayedAt Timestamp when the video was last played (epoch millis)
   * @param lastPositionMs Last playback position in milliseconds
   */
  suspend fun updatePlaybackProgress(id: Long, lastPlayedAt: Long?, lastPositionMs: Long?) =
      dao.updatePlaybackProgress(id, lastPlayedAt, lastPositionMs)

  /**
   * Inserts or updates a video item.
   *
   * @param video The video item to upsert
   * @return The ID of the inserted or updated video item
   */
  suspend fun upsert(video: VideoItem): Long = dao.insertOrReplace(video)

  /**
   * Finds a video item by its content signature.
   *
   * @param sig The content signature to search for
   * @return The matching video item, or null if not found
   */
  suspend fun findBySignature(sig: String) = dao.findBySignature(sig)
}

