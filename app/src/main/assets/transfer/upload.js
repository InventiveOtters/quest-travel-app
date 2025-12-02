/**
 * WiFi Transfer - Upload Logic
 * Handles drag-and-drop, file selection, upload queue with progress
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
const activeUploads = new Map();
const uploadMetadata = new Map(); // Track start time, speed calculations
let pinRequired = false;
let verifiedPin = null;

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    setupEventListeners();
    fetchStatus();
    fetchFileList();
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

        queueUpload(file);
    });
}

// Add file to upload queue
function queueUpload(file) {
    const id = ++uploadCounter;
    
    // Create queue item UI
    const item = document.createElement('div');
    item.className = 'queue-item';
    item.id = `queue-item-${id}`;
    item.innerHTML = `
        <div class="queue-item-header">
            <span class="queue-item-name">${escapeHtml(file.name)}</span>
            <span class="queue-item-size">${formatBytes(file.size)}</span>
        </div>
        <div class="progress-bar">
            <div class="progress-fill" id="progress-${id}"></div>
        </div>
        <div class="queue-status" id="status-${id}">Preparing...</div>
    `;
    
    queueList.appendChild(item);
    uploadQueue.classList.add('has-items');
    
    // Start upload
    uploadFile(id, file);
}

// Upload a single file
function uploadFile(id, file) {
    const xhr = new XMLHttpRequest();
    const formData = new FormData();
    formData.append('file', file, file.name);

    activeUploads.set(id, xhr);

    // Initialize upload metadata for speed tracking
    uploadMetadata.set(id, {
        startTime: Date.now(),
        fileSize: file.size,
        lastDisplayUpdate: null,
        lastSpeedInfo: null
    });

    // Progress handler
    xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
            const percent = Math.round((e.loaded / e.total) * 100);
            const speedInfo = calculateSpeed(id, e.loaded, e.total);
            updateProgress(id, percent, speedInfo);
        }
    });

    // Completion handlers
    xhr.addEventListener('load', () => {
        activeUploads.delete(id);
        uploadMetadata.delete(id);
        if (xhr.status === 200) {
            try {
                const response = JSON.parse(xhr.responseText);
                if (response.success) {
                    markSuccess(id, response);
                    fetchFileList(); // Refresh file list
                    fetchStatus(); // Refresh storage info
                } else {
                    // Parse specific error types
                    const errorMsg = parseErrorMessage(response.error);
                    markError(id, errorMsg);
                }
            } catch (e) {
                markError(id, 'Invalid server response');
            }
        } else if (xhr.status === 401) {
            // PIN required or invalid
            markError(id, 'PIN required - please enter the PIN');
            verifiedPin = null; // Reset PIN
            fetchStatus(); // This will show PIN dialog
        } else if (xhr.status === 413) {
            markError(id, 'File too large for server');
        } else if (xhr.status === 507) {
            markError(id, 'Not enough storage space on device');
        } else if (xhr.status === 415) {
            markError(id, 'File type not supported');
        } else if (xhr.status === 0) {
            markError(id, 'Connection lost - check WiFi');
        } else {
            markError(id, `Server error (${xhr.status})`);
        }
    });

    xhr.addEventListener('error', () => {
        activeUploads.delete(id);
        uploadMetadata.delete(id);
        markError(id, 'Network error - check WiFi connection');
    });

    xhr.addEventListener('abort', () => {
        activeUploads.delete(id);
        uploadMetadata.delete(id);
        markError(id, 'Upload cancelled');
    });

    xhr.addEventListener('timeout', () => {
        activeUploads.delete(id);
        uploadMetadata.delete(id);
        markError(id, 'Upload timed out - connection too slow');
    });

    xhr.timeout = 0; // No timeout for large files
    xhr.open('POST', '/api/upload');

    // Add PIN header if we have a verified PIN
    if (verifiedPin) {
        xhr.setRequestHeader('X-Upload-Pin', verifiedPin);
    }

    xhr.send(formData);

    updateStatus(id, 'Uploading...');
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

    // Initialize display update time if not set
    if (!meta.lastDisplayUpdate) {
        meta.lastDisplayUpdate = now;
    }

    // Calculate overall average speed from start (more stable)
    const totalElapsed = now - meta.startTime;
    if (totalElapsed < 100) return meta.lastSpeedInfo || null;

    const overallSpeed = (loaded / totalElapsed) * 1000; // bytes per second

    // Calculate remaining time based on overall average
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
    }
    fetchStatus(); // Refresh storage info
}

// Mark upload as failed
function markError(id, message) {
    const item = document.getElementById(`queue-item-${id}`);
    if (item) {
        item.classList.add('error');
        updateStatus(id, `✗ ${message}`);
    }
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

