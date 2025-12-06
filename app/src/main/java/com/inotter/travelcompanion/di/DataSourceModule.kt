package com.inotter.travelcompanion.di

import android.content.Context
import androidx.room.Room
import com.inotter.travelcompanion.data.datasources.videolibrary.VideoLibraryDataSource
import com.inotter.travelcompanion.data.datasources.videolibrary.VideoLibraryDataSourceImpl
import com.inotter.travelcompanion.data.datasources.videolibrary.VideoLibraryDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides data source dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Binds
    @Singleton
    abstract fun bindVideoLibraryDataSource(
        impl: VideoLibraryDataSourceImpl
    ): VideoLibraryDataSource

    companion object {
        @Provides
        @Singleton
        fun provideVideoLibraryDatabase(
            @ApplicationContext context: Context
        ): VideoLibraryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                VideoLibraryDatabase::class.java,
                "video_library.db"
            )
                .addMigrations(
                    VideoLibraryDatabase.MIGRATION_1_2,
                    VideoLibraryDatabase.MIGRATION_2_3,
                    VideoLibraryDatabase.MIGRATION_3_4,
                    VideoLibraryDatabase.MIGRATION_4_5
                )
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }
}

