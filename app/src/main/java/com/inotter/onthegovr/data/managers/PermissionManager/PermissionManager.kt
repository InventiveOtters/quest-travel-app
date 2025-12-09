package com.inotter.onthegovr.data.managers.PermissionManager

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

/**
 * Manager interface for managing READ_MEDIA_VIDEO permission on Android 14+.
 * Handles permission checking, including detection of partial media access.
 */
interface PermissionManager {

    /**
     * The permission required to read videos from MediaStore on Android 14+.
     */
    val videoPermission: String

    /**
     * The permission indicating partial (user-selected) media access on Android 14+.
     */
    val partialAccessPermission: String

    /**
     * Checks if the app has full READ_MEDIA_VIDEO permission granted.
     *
     * @return true if full video access is granted, false otherwise
     */
    fun hasVideoPermission(): Boolean

    /**
     * Checks if the app has partial media access (user-selected files only).
     * This is an Android 14+ feature where users can grant access to specific files
     * instead of all media.
     *
     * @return true if partial access is granted (but not full access), false otherwise
     */
    fun isPartialAccessGranted(): Boolean

    /**
     * Checks if any level of video access is granted (full or partial).
     *
     * @return true if either full or partial video access is granted
     */
    fun hasAnyVideoAccess(): Boolean

    /**
     * Gets the current permission status for display purposes.
     *
     * @return A PermissionStatus indicating the current state
     */
    fun getPermissionStatus(): PermissionStatus

    /**
     * Returns the list of permissions to request for MediaStore video access.
     * On Android 14+, this includes both full and partial access permissions.
     *
     * @return Array of permission strings to request
     */
    fun getPermissionsToRequest(): Array<String>
}

