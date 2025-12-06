package com.inotter.travelcompanion.ui.onboarding

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionManager
import com.inotter.travelcompanion.data.managers.PermissionManager.PermissionStatus
import com.inotter.travelcompanion.data.models.ViewingMode
import com.inotter.travelcompanion.data.repositories.ScanSettingsRepository.ScanSettingsRepository
import com.inotter.travelcompanion.workers.MediaStoreScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Onboarding step progression.
 */
enum class OnboardingStep {
    /** Step 1 & 2: Welcome and video access (permission or SAF) */
    VIDEO_ACCESS,
    /** Step 3: Viewing mode selection (2D Panel or Immersive VR) */
    MODE_SELECTION
}

/**
 * UI state for the onboarding screen.
 */
data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.VIDEO_ACCESS,
    val permissionStatus: PermissionStatus = PermissionStatus.DENIED,
    val isScanning: Boolean = false,
    val scanComplete: Boolean = false,
    val videosFound: Int = 0,
    val errorMessage: String? = null,
    val selectedViewingMode: ViewingMode = ViewingMode.DEFAULT,
)

/**
 * ViewModel for the onboarding screen.
 * Manages permission requests, first-run detection, and MediaStore scan triggering.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val scanSettingsRepo: ScanSettingsRepository,
    private val permissionManager: PermissionManager,
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences(
        PREFS_NAME,
        android.content.Context.MODE_PRIVATE
    )

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
        val hasPermission = permissionManager.hasAnyVideoAccess()
        
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
        val status = permissionManager.getPermissionStatus()
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

    /**
     * Advances to the mode selection step after video access is configured.
     */
    fun proceedToModeSelection() {
        _uiState.value = _uiState.value.copy(
            currentStep = OnboardingStep.MODE_SELECTION
        )
    }

    /**
     * Updates the selected viewing mode in UI state.
     */
    fun selectViewingMode(mode: ViewingMode) {
        _uiState.value = _uiState.value.copy(
            selectedViewingMode = mode
        )
    }

    /**
     * Saves the viewing mode preference and completes onboarding.
     */
    fun saveViewingModeAndComplete() {
        prefs.edit()
            .putString(KEY_VIEWING_MODE, _uiState.value.selectedViewingMode.name)
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
    }

    /**
     * Gets the saved viewing mode preference.
     */
    fun getViewingMode(): ViewingMode {
        val modeString = prefs.getString(KEY_VIEWING_MODE, null)
        return ViewingMode.fromString(modeString)
    }

    /**
     * Sets the viewing mode preference (used from Settings).
     */
    fun setViewingMode(mode: ViewingMode) {
        prefs.edit().putString(KEY_VIEWING_MODE, mode.name).apply()
    }

    companion object {
        const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_HAS_SAF_FOLDERS = "has_saf_folders"
        const val KEY_VIEWING_MODE = "viewing_mode"
    }
}

