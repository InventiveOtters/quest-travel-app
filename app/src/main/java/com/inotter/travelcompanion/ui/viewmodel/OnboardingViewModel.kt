package com.inotter.travelcompanion.ui.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.inotter.travelcompanion.data.db.VideoLibraryDatabase
import com.inotter.travelcompanion.data.repo.ScanSettingsRepository
import com.inotter.travelcompanion.domain.permission.PermissionStatus
import com.inotter.travelcompanion.domain.permission.VideoPermissionManager
import com.inotter.travelcompanion.domain.scan.MediaStoreScanWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the onboarding screen.
 */
data class OnboardingUiState(
    val permissionStatus: PermissionStatus = PermissionStatus.DENIED,
    val isScanning: Boolean = false,
    val scanComplete: Boolean = false,
    val videosFound: Int = 0,
    val errorMessage: String? = null,
)

/**
 * ViewModel for the onboarding screen.
 * Manages permission requests, first-run detection, and MediaStore scan triggering.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val prefs: SharedPreferences = application.getSharedPreferences(
        PREFS_NAME, 
        android.content.Context.MODE_PRIVATE
    )
    
    private val db = VideoLibraryDatabase.getInstance(application)
    private val scanSettingsRepo = ScanSettingsRepository(db)
    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        updatePermissionStatus()
    }

    /**
     * Returns true if onboarding should be shown (first run or no permission).
     */
    fun shouldShowOnboarding(): Boolean {
        val hasCompletedOnboarding = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        val hasPermission = VideoPermissionManager.hasAnyVideoAccess(getApplication())
        
        // Show onboarding if:
        // 1. Never completed onboarding, OR
        // 2. Permission was revoked after onboarding
        return !hasCompletedOnboarding || (!hasPermission && !hasSafFolders())
    }

    /**
     * Check if user has SAF folders configured (can skip permission-based onboarding).
     */
    private fun hasSafFolders(): Boolean {
        // This is a simplified check - in practice you'd query the database
        return prefs.getBoolean(KEY_HAS_SAF_FOLDERS, false)
    }

    /**
     * Updates the permission status based on current runtime permissions.
     */
    fun updatePermissionStatus() {
        val status = VideoPermissionManager.getPermissionStatus(getApplication())
        _uiState.value = _uiState.value.copy(permissionStatus = status)
    }

    /**
     * Called when permission is granted.
     * Enables auto-scan and triggers initial MediaStore scan.
     */
    fun onPermissionGranted() {
        viewModelScope.launch {
            updatePermissionStatus()
            
            // Enable auto-scan in settings
            scanSettingsRepo.setAutoScanEnabled(true)
            
            // Start scanning
            startMediaStoreScan()
        }
    }

    /**
     * Called when user denies permission.
     * User will proceed to SAF fallback.
     */
    fun onPermissionDenied() {
        updatePermissionStatus()
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }

    /**
     * Starts a MediaStore scan in the background.
     */
    fun startMediaStoreScan() {
        _uiState.value = _uiState.value.copy(
            isScanning = true,
            errorMessage = null
        )

        val workRequest = OneTimeWorkRequestBuilder<MediaStoreScanWorker>().build()

        workManager.enqueueUniqueWork(
            MediaStoreScanWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )

        // Observe work completion
        workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
            if (workInfo != null && workInfo.state.isFinished) {
                val videosFound = workInfo.outputData.getInt(
                    MediaStoreScanWorker.KEY_VIDEOS_FOUND, 0
                )
                val error = workInfo.outputData.getString(MediaStoreScanWorker.KEY_ERROR)
                
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanComplete = error == null,
                    videosFound = videosFound,
                    errorMessage = error
                )
            }
        }
    }

    /**
     * Marks onboarding as complete.
     * Called when user navigates away from onboarding.
     */
    fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
    }

    /**
     * Marks that user has SAF folders (skip permission onboarding next time).
     */
    fun markHasSafFolders() {
        prefs.edit().putBoolean(KEY_HAS_SAF_FOLDERS, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_HAS_SAF_FOLDERS = "has_saf_folders"
    }
}

