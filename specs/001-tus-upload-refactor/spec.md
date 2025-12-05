# Feature Specification: TUS Protocol Upload Refactor

**Feature Branch**: `001-tus-upload-refactor`
**Created**: 2025-12-04
**Status**: Draft
**Input**: User description: "The current uploading experience is buggy, resuming doesn't work properly. Make a refactoring plan to switch to using the tus protocol."

## Clarifications

### Session 2025-12-04

- Q: How long should incomplete upload sessions be retained before automatic cleanup? → A: 24 hours
- Q: How should the system handle simultaneous uploads from multiple browser tabs/windows? → A: Single upload only - queue additional files, block new tabs
- Q: What happens when a user closes the browser tab/window during an upload? → A: Warn user before closing, retain partial data for resume

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Resume Interrupted Upload (Priority: P1)

A user is uploading a large video file from their computer to the Travel Companion app via WiFi. During the upload, the network connection drops unexpectedly (e.g., WiFi signal loss, router restart, or user leaving the network range). When the user reconnects to the network and returns to the upload interface, they should be able to resume the upload from where it left off without re-uploading the entire file.

**Why this priority**: This is the core problem the feature solves. Users experience significant frustration and wasted time when large file uploads fail and must restart from scratch. Reliable resume capability is the primary value proposition of adopting the TUS protocol.

**Independent Test**: Can be fully tested by initiating a file upload, simulating network interruption, reconnecting, and verifying the upload resumes from the last successful byte position.

**Acceptance Scenarios**:

1. **Given** a user has started uploading a 2GB video file and 500MB has been transferred, **When** the network connection is lost and then restored, **Then** the user sees an option to resume the upload from the 500MB mark.

2. **Given** a user has an incomplete upload from a previous session, **When** they return to the upload interface, **Then** they see the incomplete upload with accurate progress information and a "Resume" option.

3. **Given** a user chooses to resume an interrupted upload, **When** the resume begins, **Then** only the remaining bytes are transferred (not the entire file again).

4. **Given** an upload is in progress, **When** the user attempts to close the browser tab or navigate away, **Then** they see a confirmation dialog warning that the upload is in progress.

5. **Given** a user closes the browser despite the warning, **When** they return to the upload interface within 24 hours, **Then** they can resume the upload from where it left off.

---

### User Story 2 - Basic File Upload (Priority: P1)

A user wants to upload video files from their computer to the Travel Companion app via the local WiFi transfer feature. They select one or more files, and the files upload successfully with real-time progress indication.

**Why this priority**: Basic upload functionality must work correctly as the foundation for all other features. This ensures the core upload path works before adding resume complexity.

**Independent Test**: Can be tested by selecting files and uploading them successfully, verifying files appear in the app's video library.

**Acceptance Scenarios**:

1. **Given** a user has connected to the upload interface, **When** they select a video file and initiate upload, **Then** the file uploads completely and appears in the app's video library.

2. **Given** an upload is in progress, **When** the user observes the upload interface, **Then** they see accurate progress percentage, transfer speed, and estimated time remaining.

3. **Given** multiple files are queued for upload, **When** the user monitors the queue, **Then** they see the status of each file (waiting, uploading, completed, or failed).

---

### User Story 3 - Cancel and Clean Up Upload (Priority: P2)

A user realizes they selected the wrong file or no longer wants to continue an upload. They should be able to cancel the upload and have any partially uploaded data cleaned up properly, without leaving orphaned files on the device.

**Why this priority**: Users need control over their uploads. Without proper cancellation and cleanup, storage space gets wasted and the user experience degrades.

**Independent Test**: Can be tested by starting an upload, cancelling it, and verifying no partial files remain on the device storage.

**Acceptance Scenarios**:

1. **Given** an upload is in progress, **When** the user clicks "Cancel", **Then** the upload stops immediately and the user is notified of the cancellation.

2. **Given** a user has cancelled an upload, **When** they check the device storage, **Then** no orphaned partial files from that upload exist.

3. **Given** an incomplete upload exists from a previous session, **When** the user chooses to "Discard" instead of "Resume", **Then** the partial file is deleted and the upload is removed from the incomplete list.

---

### User Story 4 - Upload with PIN Protection (Priority: P2)

A user has enabled PIN protection for the upload interface. When accessing the upload feature or attempting to upload files, they must provide the correct PIN before the upload can proceed.

**Why this priority**: Security is important for users who don't want unauthorized uploads to their device. This existing functionality must continue to work with the new upload mechanism.

**Independent Test**: Can be tested by enabling PIN, attempting upload without PIN (fails), then with correct PIN (succeeds).

**Acceptance Scenarios**:

1. **Given** PIN protection is enabled, **When** a user attempts to upload a file without providing the PIN, **Then** the upload is rejected with an appropriate error message.

2. **Given** PIN protection is enabled, **When** a user provides the correct PIN before uploading, **Then** the upload proceeds normally.

---

### Edge Cases

- What happens when the user attempts to resume an upload but the source file has been modified since the original upload started?
- How does the system handle resume attempts when the partial file on the device has been corrupted or deleted?
- What happens when storage space runs out during an upload?
- How does the system behave when the server restarts while uploads are in progress?
- What happens when a user tries to upload a file that exceeds available storage space?
- How does the system handle simultaneous uploads from multiple browser tabs/windows? **→ Resolved: Single upload only; additional tabs blocked with message**
- What happens when the device goes to sleep during an upload?

## Requirements *(mandatory)*

### Functional Requirements

**Core Upload Functionality:**

- **FR-001**: System MUST accept file uploads using the TUS resumable upload protocol
- **FR-002**: System MUST track upload progress and provide real-time feedback to the user
- **FR-003**: System MUST support uploading video files up to the device's available storage capacity
- **FR-004**: System MUST save uploaded files to the device's public video storage (visible to other apps)

**Resume Capability:**

- **FR-005**: System MUST persist upload session state (file name, expected size, bytes received, storage location) to survive app restarts and device reboots
- **FR-005a**: System MUST automatically clean up incomplete upload sessions that are older than 24 hours, removing both the session record and any associated partial files
- **FR-006**: System MUST allow users to resume interrupted uploads from the last successfully received byte position
- **FR-007**: System MUST verify file integrity before allowing resume (detect if source file has changed)
- **FR-008**: System MUST display incomplete uploads to the user with accurate progress information when they return to the upload interface

**Cleanup and Cancellation:**

- **FR-009**: System MUST allow users to cancel uploads in progress
- **FR-010**: System MUST clean up partial files when an upload is cancelled or discarded
- **FR-011**: System MUST detect and offer cleanup for orphaned partial uploads (uploads that were interrupted without proper session tracking)
- **FR-012**: System MUST finalize uploaded files (remove "pending" status) only after the entire file is successfully received

**Security:**

- **FR-013**: System MUST enforce PIN verification when PIN protection is enabled
- **FR-014**: System MUST validate all TUS protocol requests to prevent malicious uploads

**User Experience:**

- **FR-015**: System MUST display upload speed and estimated time remaining during transfers
- **FR-016**: System MUST support queueing multiple files for sequential upload
- **FR-017**: System MUST provide clear error messages when uploads fail
- **FR-018**: System MUST process only one upload at a time; additional files are queued automatically
- **FR-019**: System MUST reject upload attempts from additional browser tabs/windows while an upload is in progress, displaying a message that an upload is already active
- **FR-020**: System MUST display a browser confirmation dialog ("Upload in progress, are you sure you want to leave?") when user attempts to close or navigate away from the upload page during an active upload
- **FR-021**: System MUST retain partial upload data server-side when user closes browser, allowing resume when they return (within 24-hour session expiration)

### Key Entities

- **Upload Session**: Represents an in-progress or interrupted upload. Includes file name, expected total size, bytes received so far, storage location reference, file checksum/fingerprint for integrity verification, creation timestamp, last activity timestamp, and status (uploading, paused, completed, failed, cancelled). Sessions expire automatically after 24 hours of inactivity.

- **Upload Chunk**: A portion of a file being transferred. Includes the byte offset, chunk size, and data payload. Used for resumable transfers where the file is sent in pieces.

- **Resumable Upload Metadata**: Information exchanged between client and server to coordinate resumable uploads. Includes upload URL/identifier, supported protocol version, and chunk size preferences.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can successfully resume interrupted uploads at least 95% of the time when the source file is unchanged
- **SC-002**: Resume transfers only the remaining bytes - users see at least 90% time savings when resuming a 50%+ completed upload versus starting over
- **SC-003**: Upload progress indication is accurate within 5% of actual transfer completion
- **SC-004**: Users can complete a 1GB file upload over a typical home WiFi connection (50 Mbps) within 3 minutes under normal conditions
- **SC-005**: After cancellation or failed upload cleanup, no orphaned partial files remain on the device
- **SC-006**: 100% of uploads that complete successfully result in playable video files in the app's library
- **SC-007**: Users experience zero data corruption during resume scenarios (verified by file checksum matching)

## Assumptions

- The TUS protocol (tus.io) is an appropriate choice for this use case based on its widespread adoption for resumable uploads and availability of client/server implementations
- Users access the upload interface from modern web browsers that support the necessary APIs (Fetch API, File API, local storage)
- The WiFi connection is local (same network), so latency is low but connection stability may vary
- Video files are the primary use case, but the implementation should not artificially restrict file types
- The existing PIN protection mechanism will be adapted to work with TUS protocol endpoints
- Chunk size for TUS uploads will be configurable but default to a reasonable size (e.g., 5MB) balancing resume granularity with overhead
