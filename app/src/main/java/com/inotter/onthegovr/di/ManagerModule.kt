package com.inotter.onthegovr.di

import com.inotter.onthegovr.data.managers.PermissionManager.PermissionManager
import com.inotter.onthegovr.data.managers.PermissionManager.PermissionManagerImpl
import com.inotter.onthegovr.data.managers.SAFManager.SAFManager
import com.inotter.onthegovr.data.managers.SAFManager.SAFManagerImpl
import com.inotter.onthegovr.data.managers.ScannerManager.ScannerManager
import com.inotter.onthegovr.data.managers.ScannerManager.ScannerManagerImpl
import com.inotter.onthegovr.data.managers.ThumbnailManager.ThumbnailManager
import com.inotter.onthegovr.data.managers.ThumbnailManager.ThumbnailManagerImpl
import com.inotter.onthegovr.data.managers.TransferManager.TransferManager
import com.inotter.onthegovr.data.managers.TransferManager.TransferManagerImpl
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

