package com.example.travelcompanion.vrvideo.domain.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility object for managing READ_MEDIA_VIDEO permission on Android 14+.
 * Handles permission checking, including detection of partial media access.
 */
object VideoPermissionManager {

    /**
     * The permission required to read videos from MediaStore on Android 14+.
     */
    const val VIDEO_PERMISSION = Manifest.permission.READ_MEDIA_VIDEO

    /**
     * The permission indicating partial (user-selected) media access on Android 14+.
     */
    @Suppress("InlinedApi")
    const val PARTIAL_ACCESS_PERMISSION = Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED

    /**
     * Checks if the app has full READ_MEDIA_VIDEO permission granted.
     *
     * @param context Android context
     * @return true if full video access is granted, false otherwise
     */
    fun hasVideoPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            VIDEO_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if the app has partial media access (user-selected files only).
     * This is an Android 14+ feature where users can grant access to specific files
     * instead of all media.
     *
     * @param context Android context
     * @return true if partial access is granted (but not full access), false otherwise
     */
    fun isPartialAccessGranted(context: Context): Boolean {
        // Partial access permission only exists on Android 14 (API 34) and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false
        }
        
        // Check if partial access is granted but full access is not
        val hasPartial = ContextCompat.checkSelfPermission(
            context,
            PARTIAL_ACCESS_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasFull = hasVideoPermission(context)
        
        return hasPartial && !hasFull
    }

    /**
     * Checks if any level of video access is granted (full or partial).
     *
     * @param context Android context
     * @return true if either full or partial video access is granted
     */
    fun hasAnyVideoAccess(context: Context): Boolean {
        return hasVideoPermission(context) || isPartialAccessGranted(context)
    }

    /**
     * Gets the current permission status for display purposes.
     *
     * @param context Android context
     * @return A PermissionStatus indicating the current state
     */
    fun getPermissionStatus(context: Context): PermissionStatus {
        return when {
            hasVideoPermission(context) -> PermissionStatus.GRANTED
            isPartialAccessGranted(context) -> PermissionStatus.PARTIAL
            else -> PermissionStatus.DENIED
        }
    }

    /**
     * Returns the list of permissions to request for MediaStore video access.
     * On Android 14+, this includes both full and partial access permissions.
     *
     * @return Array of permission strings to request
     */
    @Suppress("InlinedApi")
    fun getPermissionsToRequest(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(VIDEO_PERMISSION, PARTIAL_ACCESS_PERMISSION)
        } else {
            arrayOf(VIDEO_PERMISSION)
        }
    }
}

/**
 * Represents the current state of video media permission.
 */
enum class PermissionStatus {
    /**
     * Full access to all videos via READ_MEDIA_VIDEO.
     */
    GRANTED,

    /**
     * Partial access to user-selected videos only (Android 14+).
     */
    PARTIAL,

    /**
     * No video access permission granted.
     */
    DENIED
}

