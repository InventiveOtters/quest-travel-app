package com.inotter.travelcompanion.data.managers.PermissionManager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [PermissionManager] for managing READ_MEDIA_VIDEO permission.
 *
 * @property context Android application context
 */
@Singleton
class PermissionManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PermissionManager {

    override val videoPermission: String = Manifest.permission.READ_MEDIA_VIDEO

    @Suppress("InlinedApi")
    override val partialAccessPermission: String = Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED

    override fun hasVideoPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            videoPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isPartialAccessGranted(): Boolean {
        // Partial access permission only exists on Android 14 (API 34) and above
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false
        }

        // Check if partial access is granted but full access is not
        val hasPartial = ContextCompat.checkSelfPermission(
            context,
            partialAccessPermission
        ) == PackageManager.PERMISSION_GRANTED

        val hasFull = hasVideoPermission()

        return hasPartial && !hasFull
    }

    override fun hasAnyVideoAccess(): Boolean {
        return hasVideoPermission() || isPartialAccessGranted()
    }

    override fun getPermissionStatus(): PermissionStatus {
        return when {
            hasVideoPermission() -> PermissionStatus.GRANTED
            isPartialAccessGranted() -> PermissionStatus.PARTIAL
            else -> PermissionStatus.DENIED
        }
    }

    @Suppress("InlinedApi")
    override fun getPermissionsToRequest(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(videoPermission, partialAccessPermission)
        } else {
            arrayOf(videoPermission)
        }
    }
}

