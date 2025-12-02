package com.example.travelcompanion.vrvideo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.travelcompanion.vrvideo.data.dao.LibraryFolderDao
import com.example.travelcompanion.vrvideo.data.dao.PlaybackSettingsDao
import com.example.travelcompanion.vrvideo.data.dao.ScanSettingsDao
import com.example.travelcompanion.vrvideo.data.dao.ThumbnailDao
import com.example.travelcompanion.vrvideo.data.dao.VideoItemDao

/**
 * Room database for the VR video library feature.
 * Contains entities for library folders, video items, thumbnails, playback settings, and scan settings.
 */
@Database(
    entities = [LibraryFolder::class, VideoItem::class, Thumbnail::class, PlaybackSettings::class, ScanSettings::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VideoLibraryDatabase : RoomDatabase() {
  abstract fun libraryFolderDao(): LibraryFolderDao
  abstract fun videoItemDao(): VideoItemDao
  abstract fun thumbnailDao(): ThumbnailDao
  abstract fun playbackSettingsDao(): PlaybackSettingsDao
  abstract fun scanSettingsDao(): ScanSettingsDao

  companion object {
    @Volatile private var INSTANCE: VideoLibraryDatabase? = null

    /**
     * Migration from version 1 to 2:
     * - Add sourceType and mediaStoreId columns to video_items
     * - Make folderId nullable (for MediaStore-discovered videos)
     * - Add sourceType index
     * - Create scan_settings table
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Add new columns to video_items
        db.execSQL("ALTER TABLE video_items ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'SAF'")
        db.execSQL("ALTER TABLE video_items ADD COLUMN mediaStoreId INTEGER DEFAULT NULL")

        // Create index on sourceType
        db.execSQL("CREATE INDEX IF NOT EXISTS index_video_items_sourceType ON video_items(sourceType)")

        // Create scan_settings table
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS scan_settings (
            id INTEGER NOT NULL PRIMARY KEY,
            autoScanEnabled INTEGER NOT NULL DEFAULT 0,
            lastMediaStoreScan INTEGER NOT NULL DEFAULT 0
          )
        """)

        // Insert default scan settings
        db.execSQL("INSERT OR IGNORE INTO scan_settings (id, autoScanEnabled, lastMediaStoreScan) VALUES (1, 0, 0)")
      }
    }

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
              .addMigrations(MIGRATION_1_2)
              .fallbackToDestructiveMigration(true)
              .build()
              .also { INSTANCE = it }
        }
  }
}

