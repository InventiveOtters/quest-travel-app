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
    Array.from(files).forEach(file => {
        // Validate file type
        const ext = file.name.split('.').pop().toLowerCase();
        if (!['mp4', 'mkv'].includes(ext)) {
            showError(`"${file.name}" is not supported. Only MP4 and MKV files allowed.`);
            return;
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
    
    // Progress handler
    xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
            const percent = Math.round((e.loaded / e.total) * 100);
            updateProgress(id, percent);
        }
    });
    
    // Completion handlers
    xhr.addEventListener('load', () => {
        activeUploads.delete(id);
        if (xhr.status === 200) {
            try {
                const response = JSON.parse(xhr.responseText);
                if (response.success) {
                    markSuccess(id, response);
                    fetchFileList(); // Refresh file list
                } else {
                    markError(id, response.error || 'Upload failed');
                }
            } catch (e) {
                markError(id, 'Invalid server response');
            }
        } else {
            markError(id, `Server error (${xhr.status})`);
        }
    });
    
    xhr.addEventListener('error', () => {
        activeUploads.delete(id);
        markError(id, 'Network error');
    });
    
    xhr.addEventListener('abort', () => {
        activeUploads.delete(id);
        markError(id, 'Upload cancelled');
    });
    
    xhr.open('POST', '/api/upload');
    xhr.send(formData);

    updateStatus(id, 'Uploading...');
}

// Update progress bar
function updateProgress(id, percent) {
    const progressEl = document.getElementById(`progress-${id}`);
    if (progressEl) {
        progressEl.style.width = `${percent}%`;
    }
    updateStatus(id, `Uploading... ${percent}%`);
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

// Show error toast/alert
function showError(message) {
    // Simple alert for now, could be enhanced with toast UI
    alert(message);
}

// Fetch server status
async function fetchStatus() {
    try {
        const response = await fetch('/api/status');
        const data = await response.json();

        if (data.storageAvailableFormatted) {
            storageAvailable.textContent = `💾 ${data.storageAvailableFormatted} available`;

            // Add warning classes for low storage
            const storageEl = storageAvailable.parentElement;
            storageEl.classList.remove('low', 'critical');
            if (data.storageAvailable < 500 * 1024 * 1024) {
                storageEl.classList.add('critical');
            } else if (data.storageAvailable < 2 * 1024 * 1024 * 1024) {
                storageEl.classList.add('low');
            }
        }
    } catch (e) {
        storageAvailable.textContent = 'Unable to fetch storage info';
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

