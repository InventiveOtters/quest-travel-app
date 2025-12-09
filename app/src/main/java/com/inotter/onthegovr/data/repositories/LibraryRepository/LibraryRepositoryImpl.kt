package com.inotter.onthegovr.data.repositories.LibraryRepository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.inotter.onthegovr.data.datasources.videolibrary.VideoLibraryDataSource
import com.inotter.onthegovr.data.datasources.videolibrary.models.LibraryFolder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [LibraryRepository] for managing library folders.
 *
 * @property context Android application context for SAF operations
 * @property dataSource Video library data source
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val context: Context,
    private val dataSource: VideoLibraryDataSource,
) : LibraryRepository {

    override fun listFolders(): Flow<List<LibraryFolder>> = dataSource.getAllFolders()

    override suspend fun addFolder(treeUri: Uri, displayName: String?, includeSubfolders: Boolean): Long {
        val doc = DocumentFile.fromTreeUri(context, treeUri)
        val name = displayName ?: doc?.name ?: treeUri.toString()
        val now = System.currentTimeMillis()
        return dataSource.insertFolder(
            LibraryFolder(
                treeUri = treeUri.toString(),
                displayName = name,
                includeSubfolders = includeSubfolders,
                addedAt = now,
            )
        )
    }

    override suspend fun removeFolder(id: Long) = dataSource.deleteFolderById(id)
}

