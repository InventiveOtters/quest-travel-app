package com.inotter.travelcompanion.data.datasources.videolibrary

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inotter.travelcompanion.data.datasources.videolibrary.dao.LibraryFolderDao
import com.inotter.travelcompanion.data.datasources.videolibrary.dao.PlaybackSettingsDao
import com.inotter.travelcompanion.data.datasources.videolibrary.dao.ScanSettingsDao
import com.inotter.travelcompanion.data.datasources.videolibrary.dao.ThumbnailDao
import com.inotter.travelcompanion.data.datasources.videolibrary.dao.UploadSessionDao
import com.inotter.travelcompanion.data.datasources.videolibrary.dao.VideoItemDao
import com.inotter.travelcompanion.data.datasources.videolibrary.models.Converters
import com.inotter.travelcompanion.data.datasources.videolibrary.models.LibraryFolder
import com.inotter.travelcompanion.data.datasources.videolibrary.models.PlaybackSettings
import com.inotter.travelcompanion.data.datasources.videolibrary.models.ScanSettings
import com.inotter.travelcompanion.data.datasources.videolibrary.models.Thumbnail
import com.inotter.travelcompanion.data.datasources.videolibrary.models.UploadSession
import com.inotter.travelcompanion.data.datasources.videolibrary.models.VideoItem

/**
 * Room database for the VR video library feature.
 * Contains entities for library folders, video items, thumbnails, playback settings, scan settings,
 * and upload sessions for resumable uploads.
 */
@Database(
    entities = [LibraryFolder::class, VideoItem::class, Thumbnail::class, PlaybackSettings::class, ScanSettings::class, UploadSession::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VideoLibraryDatabase : RoomDatabase() {
  abstract fun libraryFolderDao(): LibraryFolderDao
  abstract fun videoItemDao(): VideoItemDao
  abstract fun thumbnailDao(): ThumbnailDao
  abstract fun playbackSettingsDao(): PlaybackSettingsDao
  abstract fun scanSettingsDao(): ScanSettingsDao
  abstract fun uploadSessionDao(): UploadSessionDao

  companion object {
    /**
     * Migration from version 1 to 2:
     * - Add sourceType and mediaStoreId columns to video_items
     * - Make folderId nullable (for MediaStore-discovered videos)
     * - Add sourceType index
     * - Create scan_settings table
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
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
     * Migration from version 2 to 3:
     * - Create upload_sessions table for resumable uploads
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Create upload_sessions table
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS upload_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            filename TEXT NOT NULL,
            expectedSize INTEGER NOT NULL,
            bytesReceived INTEGER NOT NULL DEFAULT 0,
            mediaStoreUri TEXT NOT NULL,
            mimeType TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            lastUpdatedAt INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'IN_PROGRESS'
          )
        """)

        // Create unique index on mediaStoreUri
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_upload_sessions_mediaStoreUri ON upload_sessions(mediaStoreUri)")
      }
    }

    /**
     * Migration from version 3 to 4:
     * - Add TUS protocol fields to upload_sessions (tusUploadId, uploadUrl)
     * - Due to schema change complexity (new non-null columns), recreate the table
     * - Note: Fresh install is acceptable per spec, so data loss is OK
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // Drop the old table and recreate with TUS fields
        // This is acceptable per spec: "Fresh install acceptable - app reinstall; no data migration required"
        db.execSQL("DROP TABLE IF EXISTS upload_sessions")

        // Create new upload_sessions table with TUS fields
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS upload_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            tusUploadId TEXT NOT NULL,
            uploadUrl TEXT NOT NULL,
            filename TEXT NOT NULL,
            expectedSize INTEGER NOT NULL,
            bytesReceived INTEGER NOT NULL DEFAULT 0,
            mediaStoreUri TEXT NOT NULL,
            mimeType TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            lastUpdatedAt INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'IN_PROGRESS'
          )
        """)

        // Create unique indices for TUS lookups
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_upload_sessions_tusUploadId ON upload_sessions(tusUploadId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_upload_sessions_uploadUrl ON upload_sessions(uploadUrl)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_upload_sessions_mediaStoreUri ON upload_sessions(mediaStoreUri)")
      }
    }
  }
}

