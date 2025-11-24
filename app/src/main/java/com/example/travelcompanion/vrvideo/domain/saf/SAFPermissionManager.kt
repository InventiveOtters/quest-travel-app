package com.example.travelcompanion.vrvideo.domain.saf

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Manages Storage Access Framework (SAF) URI permissions.
 * Handles persisting, checking, and releasing URI permissions for library folders.
 */
object SAFPermissionManager {
  /**
   * Persists URI permissions from an ACTION_OPEN_DOCUMENT_TREE result.
   * This allows the app to retain access to the folder across app restarts.
   *
   * @param context Android application context
   * @param data Intent data from the SAF picker result
   */
  fun persistFromResult(context: Context, data: Intent?) {
    val uri = data?.data ?: return
    try {
      // For document tree URIs, we only need read permission
      context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
      // ignore
    }
  }

  /**
   * Checks if the app has persisted permissions for a given URI.
   *
   * @param context Android application context
   * @param uri The URI to check
   * @return true if permissions are persisted, false otherwise
   */
  fun hasPersisted(context: Context, uri: Uri): Boolean {
    return context.contentResolver.persistedUriPermissions.any { it.uri == uri }
  }

  /**
   * Releases persisted URI permissions for a given URI.
   *
   * @param context Android application context
   * @param uri The URI to release permissions for
   */
  fun release(context: Context, uri: Uri) {
    try {
      context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
    }
  }
}

