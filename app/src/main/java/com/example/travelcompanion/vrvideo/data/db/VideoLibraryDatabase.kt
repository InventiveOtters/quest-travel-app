package com.example.travelcompanion.vrvideo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.travelcompanion.vrvideo.data.dao.LibraryFolderDao
import com.example.travelcompanion.vrvideo.data.dao.PlaybackSettingsDao
import com.example.travelcompanion.vrvideo.data.dao.ThumbnailDao
import com.example.travelcompanion.vrvideo.data.dao.VideoItemDao

/**
 * Room database for the VR video library feature.
 * Contains entities for library folders, video items, thumbnails, and playback settings.
 */
@Database(
    entities = [LibraryFolder::class, VideoItem::class, Thumbnail::class, PlaybackSettings::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VideoLibraryDatabase : RoomDatabase() {
  abstract fun libraryFolderDao(): LibraryFolderDao
  abstract fun videoItemDao(): VideoItemDao
  abstract fun thumbnailDao(): ThumbnailDao
  abstract fun playbackSettingsDao(): PlaybackSettingsDao

  companion object {
    @Volatile private var INSTANCE: VideoLibraryDatabase? = null

    /**
     * Gets the singleton instance of the database.
     * Thread-safe lazy initialization with double-checked locking.
     *
     * @param context Android application context
     * @return The database instance
     */
    fun getInstance(context: Context): VideoLibraryDatabase =
        INSTANCE ?: synchronized(this) {
          INSTANCE ?: Room.databaseBuilder(
                  context.applicationContext,
                  VideoLibraryDatabase::class.java,
                  "video_library.db",
              )
              .fallbackToDestructiveMigration(true)
              .build()
              .also { INSTANCE = it }
        }
  }
}

