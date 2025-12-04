package com.inotter.travelcompanion.di

import com.inotter.travelcompanion.data.repositories.LibraryRepository.LibraryRepository
import com.inotter.travelcompanion.data.repositories.LibraryRepository.LibraryRepositoryImpl
import com.inotter.travelcompanion.data.repositories.ScanSettingsRepository.ScanSettingsRepository
import com.inotter.travelcompanion.data.repositories.ScanSettingsRepository.ScanSettingsRepositoryImpl
import com.inotter.travelcompanion.data.repositories.TransferRepository.TransferRepository
import com.inotter.travelcompanion.data.repositories.TransferRepository.TransferRepositoryImpl
import com.inotter.travelcompanion.data.repositories.UploadSessionRepository.UploadSessionRepository
import com.inotter.travelcompanion.data.repositories.UploadSessionRepository.UploadSessionRepositoryImpl
import com.inotter.travelcompanion.data.repositories.VideoRepository.VideoRepository
import com.inotter.travelcompanion.data.repositories.VideoRepository.VideoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides repository dependencies.
 * Binds repository interfaces to their implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(
        impl: LibraryRepositoryImpl
    ): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindVideoRepository(
        impl: VideoRepositoryImpl
    ): VideoRepository

    @Binds
    @Singleton
    abstract fun bindScanSettingsRepository(
        impl: ScanSettingsRepositoryImpl
    ): ScanSettingsRepository

    @Binds
    @Singleton
    abstract fun bindUploadSessionRepository(
        impl: UploadSessionRepositoryImpl
    ): UploadSessionRepository

    @Binds
    @Singleton
    abstract fun bindTransferRepository(
        impl: TransferRepositoryImpl
    ): TransferRepository
}

