package com.inotter.onthegovr.data.managers.SAFManager

import android.content.Intent
import android.net.Uri

/**
 * Manager interface for Storage Access Framework (SAF) URI permissions.
 * Handles persisting, checking, and releasing URI permissions for library folders.
 */
interface SAFManager {

    /**
     * Persists URI permissions from an ACTION_OPEN_DOCUMENT_TREE result.
     * This allows the app to retain access to the folder across app restarts.
     *
     * @param data Intent data from the SAF picker result
     */
    fun persistFromResult(data: Intent?)

    /**
     * Checks if the app has persisted permissions for a given URI.
     *
     * @param uri The URI to check
     * @return true if permissions are persisted, false otherwise
     */
    fun hasPersisted(uri: Uri): Boolean

    /**
     * Releases persisted URI permissions for a given URI.
     *
     * @param uri The URI to release permissions for
     */
    fun release(uri: Uri)
}

