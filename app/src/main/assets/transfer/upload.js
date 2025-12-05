/**
 * WiFi Transfer - Upload Logic
 * Handles drag-and-drop, file selection, upload queue with TUS resumable uploads
 *
 * Uses tus-js-client library for resumable uploads with automatic retry.
 * @see https://github.com/tus/tus-js-client
 */

// DOM Elements
const dropZone = document.getElementById('dropZone');
const fileInput = document.getElementById('fileInput');
const browseBtn = document.getElementById('browseBtn');
const uploadQueue = document.getElementById('uploadQueue');
const queueList = document.getElementById('queueList');
const fileListSection = document.getElementById('fileListSection');
const fileList = document.getElementById('fileList');
const storageAvailable = document.getElementById('storageAvailable');

// State
let uploadCounter = 0;
const activeUploads = new Map(); // Map<id, tus.Upload>
const uploadMetadata = new Map(); // Track start time, speed calculations
let pinRequired = false;
let verifiedPin = null;
let previousUploads = []; // Track previous uploads from localStorage that can be resumed

// TUS Configuration
const TUS_ENDPOINT = '/tus/';
const TUS_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB chunks
const TUS_RETRY_DELAYS = [0, 1000, 3000, 5000, 10000, 30000]; // Retry delays in ms

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    setupEventListeners();
    setupBeforeUnloadHandler();
    fetchStatus();
    fetchFileList();
    cleanupExpiredLocalStorageUploads(); // Clean up old entries first
    findPreviousUploads(); // Check for resumable uploads from localStorage
    // Refresh status periodically
    setInterval(fetchStatus, 30000);
});

// Event Listeners Setup
function setupEventListeners() {
    // Browse button
    browseBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        fileInput.click();
    });

    // File input change
    fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) {
            handleFiles(e.target.files);
            e.target.value = ''; // Reset for re-selection
        }
    });

    // Drop zone click
    dropZone.addEventListener('click', () => fileInput.click());

    // Drag events
    ['dragenter', 'dragover'].forEach(event => {
        dropZone.addEventListener(event, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.add('drag-over');
        });
    });

    ['dragleave', 'drop'].forEach(event => {
        dropZone.addEventListener(event, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.remove('drag-over');
        });
    });

    // Handle drop
    dropZone.addEventListener('drop', (e) => {
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleFiles(files);
        }
    });

    // Prevent page-level drag/drop
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(event => {
        document.body.addEventListener(event, (e) => {
            if (!dropZone.contains(e.target)) {
                e.preventDefault();
            }
        });
    });
}

// Setup beforeunload handler to warn user when upload is in progress
function setupBeforeUnloadHandler() {
    window.addEventListener('beforeunload', (e) => {
        // Check if there are any active uploads
        if (activeUploads.size > 0) {
            // Standard way to show confirmation dialog
            e.preventDefault();
            // Chrome requires returnValue to be set
            e.returnValue = 'You have uploads in progress. Are you sure you want to leave?';
            return e.returnValue;
        }
    });
}

// Handle selected files
function handleFiles(files) {
    // Check if PIN is required but not verified
    if (pinRequired && !verifiedPin) {
        showError('Please enter the PIN shown on the VR headset first.');
        updatePinUI();
        return;
    }

    // Check if storage is critical before allowing any uploads
    const storageEl = storageAvailable.parentElement;
    if (storageEl && storageEl.classList.contains('critical')) {
        showError('Uploads are disabled: Storage is critically low. Please free up space on the device first.');
        return;
    }

    // Check if user is trying to resume a specific upload
    const pendingResume = window.pendingResumeUpload;
    window.pendingResumeUpload = null; // Clear pending resume

    Array.from(files).forEach(file => {
        // Validate file type
        const ext = file.name.split('.').pop().toLowerCase();
        if (!['mp4', 'mkv'].includes(ext)) {
            showError(`"${file.name}" is not supported. Only MP4 and MKV files allowed.`);
            return;
        }

        // Check file size (warn for very large files)
        const maxSize = 10 * 1024 * 1024 * 1024; // 10 GB
        if (file.size > maxSize) {
            showError(`"${file.name}" is too large (${formatBytes(file.size)}). Maximum file size is 10 GB.`);
            return;
        }

        // Check against available storage (if known)
        const storageText = storageAvailable.textContent;
        const storageMatch = storageText.match(/([0-9.]+)\s*(GB|MB|TB)/i);
        if (storageMatch) {
            const storageValue = parseFloat(storageMatch[1]);
            const storageUnit = storageMatch[2].toUpperCase();
            let availableBytes = storageValue;
            if (storageUnit === 'GB') availableBytes *= 1024 * 1024 * 1024;
            else if (storageUnit === 'MB') availableBytes *= 1024 * 1024;
            else if (storageUnit === 'TB') availableBytes *= 1024 * 1024 * 1024 * 1024;

            if (file.size > availableBytes * 0.95) { // 95% threshold
                showError(`Not enough storage space for "${file.name}" (${formatBytes(file.size)}). Only ${storageText.replace('💾 ', '')} available.`);
                return;
            }
        }

        // Check if this file matches a pending resume upload
        let previousUpload = null;
        if (pendingResume && pendingResume.filename === file.name && pendingResume.size === file.size) {
            previousUpload = pendingResume;
            showToast('Resuming upload...', 'info');
        } else {
            // Check if there's a matching previous upload in localStorage
            previousUpload = previousUploads.find(u => u.filename === file.name && u.size === file.size);
            if (previousUpload) {
                showToast('Found previous upload, resuming...', 'info');
            }
        }

        queueUpload(file, previousUpload);
    });
}

// Add file to upload queue
function queueUpload(file, previousUpload = null) {
    const id = ++uploadCounter;
    const isResume = previousUpload !== null;

    // Create queue item UI
    const item = document.createElement('div');
    item.className = 'queue-item';
    item.id = `queue-item-${id}`;
    item.innerHTML = `
        <div class="queue-item-header">
            <span class="queue-item-name">${isResume ? '↻ ' : ''}${escapeHtml(file.name)}</span>
            <div class="queue-item-actions">
                <span class="queue-item-size">${formatBytes(file.size)}</span>
                <button class="cancel-btn" id="cancel-${id}" title="Cancel upload">✕</button>
            </div>
        </div>
        <div class="progress-bar">
            <div class="progress-fill" id="progress-${id}"></div>
        </div>
        <div class="queue-status" id="status-${id}">${isResume ? 'Resuming...' : 'Preparing...'}</div>
    `;

    queueList.appendChild(item);
    uploadQueue.classList.add('has-items');

    // Add cancel button click handler
    const cancelBtn = document.getElementById(`cancel-${id}`);
    if (cancelBtn) {
        cancelBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            cancelUpload(id);
        });
    }

    // Start upload (will resume if previousUpload is provided)
    uploadFile(id, file, previousUpload);
}

// Upload a single file using TUS resumable upload protocol
function uploadFile(id, file, previousUpload = null) {
    // Initialize upload metadata for speed tracking
    // Note: initialOffset will be set when first progress event arrives (for resumed uploads)
    uploadMetadata.set(id, {
        startTime: Date.now(),
        fileSize: file.size,
        initialOffset: null, // Will be set on first progress callback
        lastDisplayUpdate: null,
        lastSpeedInfo: null
    });

    // Build headers with PIN if available
    const headers = {};
    if (verifiedPin) {
        headers['X-Upload-Pin'] = verifiedPin;
    }

    // Create TUS upload with retry configuration
    const upload = new tus.Upload(file, {
        endpoint: TUS_ENDPOINT,
        retryDelays: TUS_RETRY_DELAYS,
        chunkSize: TUS_CHUNK_SIZE,
        headers: headers,
        metadata: {
            filename: file.name,
            filetype: file.type || 'application/octet-stream'
        },
        // Store URL in localStorage for resume after page refresh
        storeFingerprintForResuming: true,
        // Remove fingerprint from localStorage on successful upload
        removeFingerprintOnSuccess: true,
        // Use default fingerprint which includes: name, type, size, lastModified, endpoint
        // This is better than a custom one as it prevents resuming wrong upload if file is modified

        // Called when a previous upload is found for resume
        onShouldRetry: function(err, retryAttempt, options) {
            const status = err.originalResponse ? err.originalResponse.getStatus() : 0;
            // Don't retry on auth errors
            if (status === 401 || status === 403) {
                return false;
            }
            return true;
        },

        // Progress callback
        onProgress: function(bytesUploaded, bytesTotal) {
            const percent = Math.round((bytesUploaded / bytesTotal) * 100);
            const speedInfo = calculateSpeed(id, bytesUploaded, bytesTotal);
            updateProgress(id, percent, speedInfo);
        },

        // Success callback
        onSuccess: function() {
            activeUploads.delete(id);
            uploadMetadata.delete(id);

            // Manually clean up localStorage entries for this upload
            // tus-js-client's removeFingerprintOnSuccess may not clean all entries
            // due to the key format: tus::{fingerprint}::{random}
            cleanupLocalStorageForFile(file);

            markSuccess(id, { success: true });
            fetchFileList(); // Refresh file list
            fetchStatus(); // Refresh storage info
            // Refresh previous uploads list since this one is now complete
            findPreviousUploads();
        },

        // Error callback
        onError: function(error) {
            console.error('TUS upload error:', error);
            activeUploads.delete(id);
            uploadMetadata.delete(id);

            // Parse error status
            const status = error.originalResponse ? error.originalResponse.getStatus() : 0;

            if (status === 401) {
                markError(id, 'PIN required - please enter the PIN');
                verifiedPin = null;
                fetchStatus();
            } else if (status === 413) {
                markError(id, 'File too large for server');
            } else if (status === 507) {
                markError(id, 'Not enough storage space on device');
            } else if (status === 0) {
                // Network error - show resume hint
                markError(id, 'Connection lost - upload can be resumed');
                showToast('Upload interrupted. Refresh the page to resume.', 'info');
            } else {
                markError(id, error.message || `Upload failed (${status})`);
            }

            // Refresh previous uploads list to show this interrupted upload
            findPreviousUploads();
        },

        // Called when upload is being retried after error
        onAfterResponse: function(req, res) {
            // Log retry attempts for debugging
            console.log(`TUS response: ${res.getStatus()} for ${req.getMethod()}`);
        }
    });

    activeUploads.set(id, upload);

    // If resuming from a previous upload, use that URL
    if (previousUpload && previousUpload.uploadUrl) {
        // Remove the old localStorage entry to prevent duplicates
        // tus-js-client will create a new entry when the upload starts
        if (previousUpload.key) {
            localStorage.removeItem(previousUpload.key);
            console.log('Removed old localStorage entry before resume:', previousUpload.key);
        }
        upload.url = previousUpload.uploadUrl;
        updateStatus(id, 'Resuming upload...');
    } else {
        updateStatus(id, 'Starting upload...');
    }

    // Start the upload
    upload.start();

    // Refresh the previous uploads UI to hide this upload from the resumable list
    // (since it's now active)
    findPreviousUploads();
}

// Parse error message from server response
function parseErrorMessage(error) {
    if (!error) return 'Upload failed';

    // Map common error patterns to user-friendly messages
    if (error.includes('storage') || error.includes('space') || error.includes('disk')) {
        return 'Not enough storage space';
    }
    if (error.includes('type') || error.includes('format') || error.includes('extension')) {
        return 'File type not supported';
    }
    if (error.includes('size') || error.includes('large')) {
        return 'File too large';
    }
    if (error.includes('permission')) {
        return 'Permission denied';
    }

    return error;
}

// Calculate upload speed and ETA
// Updates displayed values only every SPEED_UPDATE_INTERVAL_MS for stability
const SPEED_UPDATE_INTERVAL_MS = 5000; // Update speed display every 5 seconds

function calculateSpeed(id, loaded, total) {
    const meta = uploadMetadata.get(id);
    if (!meta) return null;

    const now = Date.now();

    // Initialize initial offset on first progress callback
    // This is crucial for resumed uploads where 'loaded' starts at a non-zero value
    if (meta.initialOffset === null) {
        meta.initialOffset = loaded;
        meta.startTime = now; // Reset start time to when we actually started this session
    }

    // Initialize display update time if not set
    if (!meta.lastDisplayUpdate) {
        meta.lastDisplayUpdate = now;
    }

    // Calculate speed based on bytes uploaded THIS SESSION only
    const bytesThisSession = loaded - meta.initialOffset;
    const totalElapsed = now - meta.startTime;

    // Need some time to pass before we can calculate meaningful speed
    if (totalElapsed < 500) return meta.lastSpeedInfo || null;

    const overallSpeed = (bytesThisSession / totalElapsed) * 1000; // bytes per second

    // Calculate remaining time based on current speed
    const remaining = total - loaded;
    const etaSeconds = overallSpeed > 0 ? remaining / overallSpeed : 0;

    // Only update displayed values every SPEED_UPDATE_INTERVAL_MS
    const timeSinceLastDisplay = now - meta.lastDisplayUpdate;
    if (timeSinceLastDisplay < SPEED_UPDATE_INTERVAL_MS && meta.lastSpeedInfo) {
        return meta.lastSpeedInfo;
    }

    // Update display values
    meta.lastDisplayUpdate = now;

    const speedInfo = {
        speed: overallSpeed,
        speedFormatted: formatSpeed(overallSpeed),
        etaFormatted: formatEta(etaSeconds)
    };

    meta.lastSpeedInfo = speedInfo;
    return speedInfo;
}

// Format speed to human readable (e.g., "12.5 MB/s")
function formatSpeed(bytesPerSecond) {
    if (bytesPerSecond === 0) return '0 B/s';
    const k = 1024;
    const sizes = ['B/s', 'KB/s', 'MB/s', 'GB/s'];
    const i = Math.floor(Math.log(bytesPerSecond) / Math.log(k));
    const value = bytesPerSecond / Math.pow(k, i);
    return value.toFixed(1) + ' ' + sizes[Math.min(i, sizes.length - 1)];
}

// Format ETA to human readable (e.g., "2:30" or "1:05:30")
function formatEta(seconds) {
    if (!isFinite(seconds) || seconds <= 0) return '--:--';

    seconds = Math.ceil(seconds);

    if (seconds < 60) {
        return `0:${seconds.toString().padStart(2, '0')}`;
    } else if (seconds < 3600) {
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    } else {
        const hours = Math.floor(seconds / 3600);
        const mins = Math.floor((seconds % 3600) / 60);
        const secs = seconds % 60;
        return `${hours}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
}

// Update progress bar
function updateProgress(id, percent, speedInfo) {
    const progressEl = document.getElementById(`progress-${id}`);
    if (progressEl) {
        progressEl.style.width = `${percent}%`;
    }

    let statusText = `Uploading... ${percent}%`;
    if (speedInfo) {
        statusText += ` • ${speedInfo.speedFormatted}`;
        if (percent < 100) {
            statusText += ` • ${speedInfo.etaFormatted} remaining`;
        }
    }
    updateStatus(id, statusText);
}

// Update status text
function updateStatus(id, text) {
    const statusEl = document.getElementById(`status-${id}`);
    if (statusEl) {
        statusEl.textContent = text;
    }
}

// Mark upload as successful
function markSuccess(id, response) {
    const item = document.getElementById(`queue-item-${id}`);
    if (item) {
        item.classList.add('success');
        updateProgress(id, 100);
        updateStatus(id, `✓ Uploaded successfully`);
        // Hide cancel button on success
        hideCancelButton(id);
    }
    fetchStatus(); // Refresh storage info
}

// Mark upload as failed
function markError(id, message) {
    const item = document.getElementById(`queue-item-${id}`);
    if (item) {
        item.classList.add('error');
        updateStatus(id, `✗ ${message}`);
        // Hide cancel button on error
        hideCancelButton(id);
    }
}

// Mark upload as cancelled
function markCancelled(id) {
    const item = document.getElementById(`queue-item-${id}`);
    if (item) {
        item.classList.add('cancelled');
        updateStatus(id, '⊘ Upload cancelled');
        hideCancelButton(id);
    }
}

// Hide cancel button for a queue item
function hideCancelButton(id) {
    const cancelBtn = document.getElementById(`cancel-${id}`);
    if (cancelBtn) {
        cancelBtn.style.display = 'none';
    }
}

/**
 * Cancel an active upload.
 * Aborts the TUS upload, cleans up localStorage, and requests server cleanup.
 * @param {number} id - The upload queue item ID
 */
function cancelUpload(id) {
    const upload = activeUploads.get(id);
    if (!upload) {
        console.log('No active upload found for id:', id);
        return;
    }

    // Abort the upload (stops network transfer)
    upload.abort();

    // Get the upload URL for server-side cleanup
    const uploadUrl = upload.url;

    // Clean up from our tracking maps
    activeUploads.delete(id);
    uploadMetadata.delete(id);

    // Clean up localStorage entries for this file
    if (upload.file) {
        cleanupLocalStorageForFile(upload.file);
    }

    // Update UI immediately
    markCancelled(id);
    showToast('Upload cancelled', 'info');

    // Request server-side cleanup via DELETE request
    // Use a small delay to allow any in-flight PATCH requests to complete/abort
    // This prevents a race condition where DELETE runs before PATCH finishes
    if (uploadUrl) {
        setTimeout(() => {
            console.log('Sending DELETE request after abort delay:', uploadUrl);
            fetch(uploadUrl, {
                method: 'DELETE',
                headers: {
                    'Tus-Resumable': '1.0.0',
                    'X-Upload-Pin': localStorage.getItem('uploadPin') || ''
                }
            }).then(response => {
                console.log('Server cleanup DELETE response:', response.status);
                if (response.ok) {
                    // Refresh storage info after successful deletion
                    fetchStatus();
                }
            }).catch(err => {
                console.log('Server cleanup request failed (may already be cleaned):', err);
            });
        }, 500); // 500ms delay to allow in-flight requests to complete
    }

    // Refresh previous uploads list
    findPreviousUploads();
}

// Show error toast notification
function showError(message) {
    // Create toast element if it doesn't exist
    let toast = document.getElementById('error-toast');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'error-toast';
        toast.style.cssText = `
            position: fixed;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: #d32f2f;
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            z-index: 1000;
            font-size: 14px;
            max-width: 80%;
            text-align: center;
            opacity: 0;
            transition: opacity 0.3s ease;
        `;
        document.body.appendChild(toast);
    }

    toast.textContent = message;
    toast.style.opacity = '1';

    // Auto-hide after 5 seconds
    clearTimeout(toast.hideTimeout);
    toast.hideTimeout = setTimeout(() => {
        toast.style.opacity = '0';
    }, 5000);
}

// Fetch server status
async function fetchStatus() {
    try {
        const response = await fetch('/api/status');
        const data = await response.json();

        // Check if PIN is required
        pinRequired = data.pinRequired === true;
        updatePinUI();

        if (data.storageAvailableFormatted) {
            storageAvailable.textContent = `💾 ${data.storageAvailableFormatted} available`;

            // Add warning classes for low storage
            const storageEl = storageAvailable.parentElement;
            storageEl.classList.remove('low', 'critical');

            const isCritical = data.storageAvailable < 500 * 1024 * 1024;
            const isLow = data.storageAvailable < 2 * 1024 * 1024 * 1024;

            if (isCritical) {
                storageEl.classList.add('critical');
                showStorageWarning('critical', data.storageAvailableFormatted);
            } else if (isLow) {
                storageEl.classList.add('low');
                showStorageWarning('low', data.storageAvailableFormatted);
            } else {
                hideStorageWarning();
            }
        }
    } catch (e) {
        storageAvailable.textContent = 'Unable to fetch storage info';
    }
}

// Show storage warning banner
function showStorageWarning(level, available) {
    let banner = document.getElementById('storage-warning-banner');
    if (!banner) {
        banner = document.createElement('div');
        banner.id = 'storage-warning-banner';
        banner.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            padding: 12px 20px;
            text-align: center;
            font-weight: bold;
            z-index: 1000;
            transition: all 0.3s ease;
        `;
        document.body.insertBefore(banner, document.body.firstChild);
        // Add padding to body to prevent content overlap
        document.body.style.paddingTop = '50px';
    }

    if (level === 'critical') {
        banner.style.background = '#d32f2f';
        banner.style.color = 'white';
        banner.innerHTML = `⚠️ Storage Critical (${available}) - Uploads are disabled until you free up space`;
    } else {
        banner.style.background = '#ff9800';
        banner.style.color = 'white';
        banner.innerHTML = `⚠️ Storage Low (${available}) - Consider freeing up space soon`;
    }
    banner.style.display = 'block';
}

// Hide storage warning banner
function hideStorageWarning() {
    const banner = document.getElementById('storage-warning-banner');
    if (banner) {
        banner.style.display = 'none';
        document.body.style.paddingTop = '0';
    }
}

// Fetch uploaded files list
async function fetchFileList() {
    try {
        const response = await fetch('/api/files');
        const data = await response.json();

        if (data.files && data.files.length > 0) {
            fileList.innerHTML = data.files.map(file => `
                <div class="file-item">
                    <span class="file-item-name">🎬 ${escapeHtml(file.name)}</span>
                    <div class="file-item-meta">
                        <span>${file.sizeFormatted}</span>
                        <span>${formatTime(file.uploadedAt)}</span>
                    </div>
                </div>
            `).join('');
        } else {
            fileList.innerHTML = '<p class="empty-state">No files uploaded yet</p>';
        }
    } catch (e) {
        fileList.innerHTML = '<p class="empty-state">Unable to fetch files</p>';
    }
}

// Clean up localStorage entries for a specific file (used on successful upload)
// This ensures the "Incomplete Uploads" section doesn't show completed uploads
function cleanupLocalStorageForFile(file) {
    if (!file) return;

    const keysToRemove = [];

    // Find all localStorage keys that match this file by checking stored metadata
    for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && key.startsWith('tus::')) {
            try {
                const storedValue = localStorage.getItem(key);
                if (storedValue) {
                    const uploadData = JSON.parse(storedValue);
                    // Match by filename and size from stored metadata
                    if (uploadData.metadata?.filename === file.name &&
                        uploadData.size === file.size) {
                        keysToRemove.push(key);
                    }
                }
            } catch (e) {
                // Ignore parse errors
            }
        }
    }

    // Remove all matching entries
    keysToRemove.forEach(key => {
        localStorage.removeItem(key);
        console.log('Cleaned up localStorage entry for completed upload:', key);
    });
}

// Clean up expired localStorage entries for TUS uploads
// TUS uploads expire after 24 hours on the server, so we should clean up
// client-side entries that are older than that
const TUS_EXPIRATION_MS = 24 * 60 * 60 * 1000; // 24 hours

function cleanupExpiredLocalStorageUploads() {
    const now = Date.now();
    const keysToRemove = [];

    for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && key.startsWith('tus::')) {
            try {
                const storedValue = localStorage.getItem(key);
                if (storedValue) {
                    const uploadData = JSON.parse(storedValue);

                    // Check if creationTime exists and is expired
                    if (uploadData.creationTime) {
                        const creationTime = new Date(uploadData.creationTime).getTime();
                        if (!isNaN(creationTime) && (now - creationTime) > TUS_EXPIRATION_MS) {
                            keysToRemove.push({
                                key: key,
                                uploadUrl: uploadData.uploadUrl
                            });
                        }
                    }
                }
            } catch (e) {
                // If we can't parse it, it might be corrupted - remove it
                console.log('Removing unparseable TUS localStorage entry:', key);
                keysToRemove.push({ key: key, uploadUrl: null });
            }
        }
    }

    // Remove expired entries and notify server
    keysToRemove.forEach(entry => {
        localStorage.removeItem(entry.key);
        console.log('Cleaned up expired TUS localStorage entry:', entry.key);

        // Try to clean up server-side as well (best effort)
        if (entry.uploadUrl) {
            fetch(entry.uploadUrl, {
                method: 'DELETE',
                headers: { 'Tus-Resumable': '1.0.0' }
            }).catch(() => {
                // Ignore errors - server may have already cleaned up
            });
        }
    });

    if (keysToRemove.length > 0) {
        console.log(`Cleaned up ${keysToRemove.length} expired TUS upload(s) from localStorage`);
    }
}

// Find previous uploads from localStorage using tus-js-client
// This checks localStorage for any incomplete TUS uploads that can be resumed
function findPreviousUploads() {
    previousUploads = [];

    // tus-js-client stores upload URLs in localStorage with keys like "tus::{fingerprint}"
    // The fingerprint is based on file properties, so same file can be matched
    const tusKeys = [];
    for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && key.startsWith('tus::')) {
            tusKeys.push(key);
        }
    }

    // Parse each stored upload
    // tus-js-client stores upload data as JSON with format:
    // { size, metadata: { filename, filetype }, creationTime, uploadUrl }
    tusKeys.forEach(key => {
        try {
            const storedValue = localStorage.getItem(key);
            if (storedValue) {
                // tus-js-client stores a JSON object, not just the URL
                let uploadData;
                try {
                    uploadData = JSON.parse(storedValue);
                } catch (parseError) {
                    // If it's not JSON, assume it's a plain URL (legacy format)
                    uploadData = { uploadUrl: storedValue };
                }

                // Extract upload URL from the stored data
                const uploadUrl = uploadData.uploadUrl;
                if (!uploadUrl) {
                    console.log('No uploadUrl in TUS localStorage entry:', key);
                    return;
                }

                // Extract filename and size from metadata
                const filename = uploadData.metadata?.filename;
                const size = uploadData.size;

                // Skip entries without proper metadata
                if (!filename || !size || size <= 0) {
                    console.log('Skipping TUS entry without proper metadata:', key);
                    return;
                }

                previousUploads.push({
                    key: key,
                    uploadUrl: uploadUrl,
                    filename: filename,
                    size: size
                });
            }
        } catch (e) {
            console.log('Error parsing TUS localStorage entry:', key, e);
        }
    });

    // Filter out uploads that are currently active
    // Get all active upload URLs
    const activeUploadUrls = new Set();
    activeUploads.forEach(upload => {
        if (upload.url) {
            activeUploadUrls.add(upload.url);
        }
    });

    // Remove entries that match active uploads
    previousUploads = previousUploads.filter(upload => !activeUploadUrls.has(upload.uploadUrl));

    // Show UI if we found previous uploads (that aren't currently active)
    if (previousUploads.length > 0) {
        showPreviousUploadsUI();
    } else {
        hidePreviousUploadsUI();
    }

    console.log('Found', previousUploads.length, 'resumable uploads in localStorage (excluding active)');
}

// Show UI for previous uploads that can be resumed
function showPreviousUploadsUI() {
    let section = document.getElementById('previous-uploads-section');

    if (!section) {
        section = document.createElement('div');
        section.id = 'previous-uploads-section';
        section.style.cssText = `
            background: linear-gradient(135deg, #1a237e 0%, #311b92 100%);
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 24px;
            border: 1px solid rgba(255, 255, 255, 0.1);
        `;

        // Insert before the drop zone
        const container = dropZone.parentElement;
        container.insertBefore(section, dropZone);
    }

    section.innerHTML = `
        <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px;">
            <span style="font-size: 24px;">⏸️</span>
            <div>
                <h3 style="color: white; margin: 0; font-size: 18px;">Incomplete Uploads Found</h3>
                <p style="color: rgba(255,255,255,0.7); margin: 4px 0 0 0; font-size: 14px;">
                    ${previousUploads.length} upload${previousUploads.length > 1 ? 's' : ''} can be resumed
                </p>
            </div>
        </div>
        <div id="previous-uploads-list" style="display: flex; flex-direction: column; gap: 12px;">
            ${previousUploads.map((upload, index) => `
                <div class="previous-upload-item" data-index="${index}" style="
                    background: rgba(255,255,255,0.1);
                    border-radius: 8px;
                    padding: 12px 16px;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 12px;
                ">
                    <div style="flex: 1; min-width: 0;">
                        <div style="color: white; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                            ${escapeHtml(upload.filename)}
                        </div>
                        <div style="color: rgba(255,255,255,0.6); font-size: 12px; margin-top: 4px;">
                            File size: ${formatBytes(upload.size)}
                        </div>
                    </div>
                    <div style="display: flex; gap: 8px;">
                        <button onclick="resumePreviousUpload(${index})" style="
                            background: #7c4dff;
                            color: white;
                            border: none;
                            border-radius: 6px;
                            padding: 8px 16px;
                            cursor: pointer;
                            font-weight: 500;
                            white-space: nowrap;
                        ">Resume</button>
                        <button onclick="discardPreviousUpload(${index})" style="
                            background: rgba(255,255,255,0.2);
                            color: white;
                            border: none;
                            border-radius: 6px;
                            padding: 8px 12px;
                            cursor: pointer;
                            font-weight: 500;
                            white-space: nowrap;
                        " title="Discard this upload">✕</button>
                    </div>
                </div>
            `).join('')}
        </div>
        <p style="color: rgba(255,255,255,0.5); font-size: 12px; margin-top: 12px; text-align: center;">
            💡 Select the same file from your computer to resume upload
        </p>
    `;
}

// Hide previous uploads UI
function hidePreviousUploadsUI() {
    const section = document.getElementById('previous-uploads-section');
    if (section) {
        section.remove();
    }
}

// Resume a previous upload - user needs to select the same file
function resumePreviousUpload(index) {
    const upload = previousUploads[index];
    if (!upload) {
        showError('Upload session not found');
        return;
    }

    // Store the upload info for when user selects a file
    window.pendingResumeUpload = upload;

    // Show instructions to user
    showToast(`Select "${upload.filename}" to resume upload`, 'info');

    // Trigger file picker
    fileInput.click();
}

// Discard a previous upload from localStorage
function discardPreviousUpload(index) {
    const upload = previousUploads[index];
    if (!upload) {
        return;
    }

    // Remove from localStorage
    localStorage.removeItem(upload.key);

    // Also try to tell server to cleanup (optional, may fail if server cleaned up already)
    if (upload.uploadUrl) {
        fetch(upload.uploadUrl, {
            method: 'DELETE',
            headers: { 'Tus-Resumable': '1.0.0' }
        }).catch(() => {
            // Ignore errors - server may have already cleaned up
        });
    }

    // Refresh UI
    findPreviousUploads();
    showToast('Upload discarded', 'info');
}

// Utility: Format bytes to human readable
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

// Utility: Format timestamp to relative time
function formatTime(timestamp) {
    const now = Date.now();
    const diff = now - timestamp;
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);

    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes} min ago`;
    if (hours < 24) return `${hours} hr ago`;
    return new Date(timestamp).toLocaleDateString();
}

// Utility: Escape HTML to prevent XSS
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// PIN Protection UI
function updatePinUI() {
    let pinSection = document.getElementById('pin-section');

    if (pinRequired && !verifiedPin) {
        // Show PIN entry UI
        if (!pinSection) {
            pinSection = document.createElement('div');
            pinSection.id = 'pin-section';
            pinSection.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background: rgba(0, 0, 0, 0.9);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 2000;
            `;
            pinSection.innerHTML = `
                <div style="background: #1e1e1e; padding: 40px; border-radius: 16px; text-align: center; max-width: 400px;">
                    <h2 style="color: white; margin-bottom: 8px;">🔒 PIN Required</h2>
                    <p style="color: #aaa; margin-bottom: 24px;">Enter the 4-digit PIN shown on the VR headset</p>
                    <input type="text" id="pin-input" maxlength="4" pattern="[0-9]*" inputmode="numeric"
                        style="font-size: 32px; text-align: center; letter-spacing: 12px; padding: 16px; width: 180px;
                               border: 2px solid #6200ee; border-radius: 8px; background: #2d2d2d; color: white;"
                        placeholder="----">
                    <div id="pin-error" style="color: #ff5252; margin-top: 12px; min-height: 20px;"></div>
                    <button id="pin-submit" style="margin-top: 16px; padding: 12px 32px; font-size: 16px;
                                                   background: #6200ee; color: white; border: none; border-radius: 8px; cursor: pointer;">
                        Verify PIN
                    </button>
                </div>
            `;
            document.body.appendChild(pinSection);

            // Setup PIN input handlers
            const pinInput = document.getElementById('pin-input');
            const pinSubmit = document.getElementById('pin-submit');

            pinInput.addEventListener('keyup', (e) => {
                if (e.key === 'Enter' && pinInput.value.length === 4) {
                    verifyPin(pinInput.value);
                }
            });

            pinSubmit.addEventListener('click', () => {
                if (pinInput.value.length === 4) {
                    verifyPin(pinInput.value);
                }
            });

            pinInput.focus();
        }
    } else if (pinSection) {
        // Hide PIN entry UI
        pinSection.remove();
    }
}

async function verifyPin(pin) {
    const pinError = document.getElementById('pin-error');
    const pinSubmit = document.getElementById('pin-submit');

    pinSubmit.disabled = true;
    pinSubmit.textContent = 'Verifying...';

    try {
        const response = await fetch('/api/verify-pin', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `pin=${encodeURIComponent(pin)}`
        });

        const data = await response.json();

        if (data.success) {
            verifiedPin = pin;
            updatePinUI();
            showToast('PIN verified successfully!', 'success');
        } else {
            pinError.textContent = 'Invalid PIN. Please try again.';
            document.getElementById('pin-input').value = '';
            document.getElementById('pin-input').focus();
        }
    } catch (e) {
        pinError.textContent = 'Failed to verify PIN. Please try again.';
    } finally {
        pinSubmit.disabled = false;
        pinSubmit.textContent = 'Verify PIN';
    }
}

function showToast(message, type = 'info') {
    let toast = document.getElementById('toast-notification');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'toast-notification';
        toast.style.cssText = `
            position: fixed;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            padding: 12px 24px;
            border-radius: 8px;
            color: white;
            font-weight: bold;
            z-index: 3000;
            transition: opacity 0.3s ease;
        `;
        document.body.appendChild(toast);
    }

    toast.style.background = type === 'success' ? '#4caf50' : type === 'error' ? '#f44336' : '#2196f3';
    toast.textContent = message;
    toast.style.opacity = '1';

    setTimeout(() => {
        toast.style.opacity = '0';
    }, 3000);
}

