package com.inotter.travelcompanion.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inotter.travelcompanion.data.db.VideoLibraryDatabase
import com.inotter.travelcompanion.data.repo.UploadSessionRepository
import com.inotter.travelcompanion.domain.transfer.IncompleteUploadDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing incomplete uploads detection and cleanup.
 * Used to show the IncompleteUploadsDialog on app startup.
 *
 * Detects two types of incomplete uploads:
 * 1. Incomplete uploads with database records (can be resumed)
 * 2. Orphaned MediaStore entries without database records (can only be cleaned up)
 */
class IncompleteUploadsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = VideoLibraryDatabase.getInstance(application)
    private val uploadSessionRepository = UploadSessionRepository(db)
    private val incompleteUploadDetector = IncompleteUploadDetector(
        application.contentResolver,
        uploadSessionRepository
    )

    // Incomplete uploads with database records (resumable)
    private val _incompleteUploads = MutableStateFlow<List<IncompleteUploadDetector.IncompleteUpload>>(emptyList())
    val incompleteUploads: StateFlow<List<IncompleteUploadDetector.IncompleteUpload>> = _incompleteUploads.asStateFlow()

    // Orphaned MediaStore entries without database records (cleanup only)
    private val _orphanedEntries = MutableStateFlow<List<IncompleteUploadDetector.OrphanedMediaStoreEntry>>(emptyList())
    val orphanedEntries: StateFlow<List<IncompleteUploadDetector.OrphanedMediaStoreEntry>> = _orphanedEntries.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isCleaningUp = MutableStateFlow(false)
    val isCleaningUp: StateFlow<Boolean> = _isCleaningUp.asStateFlow()

    init {
        checkForIncompleteUploads()
    }

    /**
     * Checks for incomplete uploads and orphaned MediaStore entries on app startup.
     */
    fun checkForIncompleteUploads() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Check for incomplete uploads with database records
                val uploads = incompleteUploadDetector.detectIncompleteUploads()
                _incompleteUploads.value = uploads

                // Check for orphaned MediaStore entries (no database record)
                val orphaned = incompleteUploadDetector.detectTrulyOrphanedMediaStoreEntries()
                _orphanedEntries.value = orphaned

                android.util.Log.d(
                    "IncompleteUploadsVM",
                    "Found ${uploads.size} incomplete uploads, ${orphaned.size} orphaned entries"
                )

                // Show dialog if there are any incomplete uploads or orphaned entries
                if (uploads.isNotEmpty() || orphaned.isNotEmpty()) {
                    _showDialog.value = true
                }
            } catch (e: Exception) {
                android.util.Log.e("IncompleteUploadsVM", "Failed to check incomplete uploads", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Cleans up all incomplete uploads and orphaned entries.
     */
    fun cleanUpIncompleteUploads() {
        viewModelScope.launch {
            _isCleaningUp.value = true
            try {
                val cleanedCount = incompleteUploadDetector.cleanupAllIncompleteUploads()
                android.util.Log.i("IncompleteUploadsVM", "Cleaned up $cleanedCount incomplete uploads")

                // Clear the lists and hide dialog
                _incompleteUploads.value = emptyList()
                _orphanedEntries.value = emptyList()
                _showDialog.value = false
            } catch (e: Exception) {
                android.util.Log.e("IncompleteUploadsVM", "Failed to cleanup uploads", e)
            } finally {
                _isCleaningUp.value = false
            }
        }
    }

    /**
     * Dismisses the dialog without cleaning up.
     */
    fun dismissDialog() {
        _showDialog.value = false
    }

    /**
     * Called when user chooses to continue uploading.
     * Just dismisses the dialog - navigation is handled by the caller.
     */
    fun onContinueUploading() {
        _showDialog.value = false
    }

    /**
     * Gets the total storage used by incomplete uploads and orphaned entries.
     */
    fun getTotalStorageUsed(): Long {
        val uploadsSize = _incompleteUploads.value.sumOf { it.currentSize }
        val orphanedSize = _orphanedEntries.value.sumOf { it.currentSize }
        return uploadsSize + orphanedSize
    }

    /**
     * Returns the total count of incomplete items (uploads + orphaned entries).
     */
    fun getTotalIncompleteCount(): Int {
        return _incompleteUploads.value.size + _orphanedEntries.value.size
    }

    /**
     * Returns true if there are any resumable uploads (with database records).
     */
    fun hasResumableUploads(): Boolean {
        return _incompleteUploads.value.any { it.canResume }
    }
}

