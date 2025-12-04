package com.inotter.travelcompanion.data.repositories.VideoRepository

import com.inotter.travelcompanion.data.datasources.videolibrary.models.StereoLayout
import com.inotter.travelcompanion.data.datasources.videolibrary.models.VideoItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing video items in the library.
 * Provides operations to query, update, and delete video items.
 */
interface VideoRepository {
    /**
     * Returns a Flow of all video items, ordered by creation date (newest first).
     *
     * @return Flow emitting list of video items
     */
    fun queryVideos(): Flow<List<VideoItem>>

    /**
     * Deletes a video item from the database by ID.
     * The actual video file is not deleted from storage.
     *
     * @param id The video item ID to delete
     */
    suspend fun deleteById(id: Long)

    /**
     * Sets or clears the stereo layout override for a video.
     *
     * @param id The video item ID
     * @param layout The stereo layout to set, or null to clear the override
     */
    suspend fun setStereoLayoutOverride(id: Long, layout: StereoLayout?)

    /**
     * Updates the playback progress for a video.
     *
     * @param id The video item ID
     * @param lastPlayedAt Timestamp when the video was last played (epoch millis)
     * @param lastPositionMs Last playback position in milliseconds
     */
    suspend fun updatePlaybackProgress(id: Long, lastPlayedAt: Long?, lastPositionMs: Long?)

    /**
     * Inserts or updates a video item.
     *
     * @param video The video item to upsert
     * @return The ID of the inserted or updated video item
     */
    suspend fun upsert(video: VideoItem): Long

    /**
     * Finds a video item by its content signature.
     *
     * @param sig The content signature to search for
     * @return The matching video item, or null if not found
     */
    suspend fun findBySignature(sig: String): VideoItem?
}

