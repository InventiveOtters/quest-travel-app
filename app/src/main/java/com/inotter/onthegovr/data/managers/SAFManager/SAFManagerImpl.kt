package com.inotter.onthegovr.data.managers.SAFManager

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [SAFManager] for managing Storage Access Framework URI permissions.
 *
 * @property context Android application context
 */
@Singleton
class SAFManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SAFManager {

    override fun persistFromResult(data: Intent?) {
        val uri = data?.data ?: return
        try {
            // For document tree URIs, we only need read permission
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // ignore
        }
    }

    override fun hasPersisted(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri }
    }

    override fun release(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
    }
}

