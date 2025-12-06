package com.inotter.travelcompanion.di

import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionManager
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionManagerImpl
import com.inotter.travelcompanion.data.managers.SAFManager.SAFManager
import com.inotter.travelcompanion.data.managers.SAFManager.SAFManagerImpl
import com.inotter.travelcompanion.data.managers.ScannerManager.ScannerManager
import com.inotter.travelcompanion.data.managers.ScannerManager.ScannerManagerImpl
import com.inotter.travelcompanion.data.managers.ThumbnailManager.ThumbnailManager
import com.inotter.travelcompanion.data.managers.ThumbnailManager.ThumbnailManagerImpl
import com.inotter.travelcompanion.data.managers.TransferManager.TransferManager
import com.inotter.travelcompanion.data.managers.TransferManager.TransferManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides manager dependencies.
 * Binds manager interfaces to their implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ManagerModule {

    @Binds
    @Singleton
    abstract fun bindScannerManager(
        impl: ScannerManagerImpl
    ): ScannerManager

    @Binds
    @Singleton
    abstract fun bindThumbnailManager(
        impl: ThumbnailManagerImpl
    ): ThumbnailManager

    @Binds
    @Singleton
    abstract fun bindPermissionManager(
        impl: PermissionManagerImpl
    ): PermissionManager

    @Binds
    @Singleton
    abstract fun bindSAFManager(
        impl: SAFManagerImpl
    ): SAFManager

    @Binds
    @Singleton
    abstract fun bindTransferManager(
        impl: TransferManagerImpl
    ): TransferManager
}

