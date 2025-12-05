# Data Model: Resume Incomplete Movie Uploads

**Created**: 2024-12-04  
**Feature Branch**: `001-resume-movie-uploads`

## Entities Overview

```
┌─────────────────────┐     ┌─────────────────────┐
│    UploadSession    │     │   IncompleteUpload  │
│     (Database)      │────▶│  (Runtime/DTO)      │
└─────────────────────┘     └─────────────────────┘
          │                           │
          │                           │
          ▼                           ▼
┌─────────────────────┐     ┌─────────────────────┐
│   MediaStore Entry  │     │   ResumableUpload   │
│   (Android System)  │     │    (API Response)   │
└─────────────────────┘     └─────────────────────┘
```

---

## Entity: UploadSession (Existing - No Changes Required)

**Location**: `app/src/main/java/com/inotter/travelcompanion/data/datasources/videolibrary/models/UploadSession.kt`

| Field | Type | Description | Validation |
|-------|------|-------------|------------|
| `id` | `Long` | Auto-generated PK | Auto-increment |
| `filename` | `String` | Original file name | Non-empty, ≤255 chars |
| `expectedSize` | `Long` | Total file size (bytes) | ≥0 |
| `bytesReceived` | `Long` | Bytes uploaded so far | 0 ≤ value ≤ expectedSize |
| `mediaStoreUri` | `String` | Content URI of pending entry | Valid URI format, unique |
| `mimeType` | `String` | MIME type (video/mp4, video/x-matroska) | Non-empty |
| `createdAt` | `Long` | Creation timestamp (epoch ms) | Auto-set |
| `lastUpdatedAt` | `Long` | Last progress update (epoch ms) | Auto-updated |
| `status` | `UploadSessionStatus` | Current status | Enum value |

**Computed Properties**:
- `progressPercent: Int` — `(bytesReceived * 100 / expectedSize).coerceIn(0, 100)`
- `isOrphaned(maxAgeMillis): Boolean` — True if `IN_PROGRESS` and stale

---

## Enum: UploadSessionStatus (Existing - No Changes Required)

| Value | Description | Transitions To |
|-------|-------------|----------------|
| `IN_PROGRESS` | Upload active or interrupted | `COMPLETED`, `FAILED`, `CANCELLED` |
| `COMPLETED` | Upload finished successfully | — (terminal) |
| `FAILED` | Upload failed (error/storage) | — (terminal) |
| `CANCELLED` | User deleted incomplete upload | — (terminal) |

---

## Entity: IncompleteUpload (Existing Runtime DTO)

**Location**: `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/models/TransferModels.kt`

| Field | Type | Description |
|-------|------|-------------|
| `session` | `UploadSession` | The underlying database entity |
| `mediaStoreExists` | `Boolean` | Whether MediaStore entry is still valid |
| `currentSize` | `Long` | Current file size in MediaStore |

**Computed Properties**:
- `canResume: Boolean` — `mediaStoreExists && session.bytesReceived > 0`
- `progressText: String` — Formatted progress (e.g., "45%")
- `sizeText: String` — Formatted expected size
- `receivedText: String` — Formatted received bytes

---

## Entity: ResumableUpload (Existing API DTO)

**Location**: `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/models/TransferModels.kt`

Used for JSON response in `/api/incomplete-uploads`.

| Field | Type | JSON Key | Description |
|-------|------|----------|-------------|
| `sessionId` | `Long` | `sessionId` | Upload session ID |
| `filename` | `String` | `filename` | Original file name |
| `expectedSize` | `Long` | `expectedSize` | Total expected bytes |
| `bytesReceived` | `Long` | `bytesReceived` | Bytes uploaded |
| `mediaStoreUri` | `String` | `mediaStoreUri` | MediaStore URI |
| `progressPercent` | `Int` | `progressPercent` | Upload progress (0-100) |

---

## State Transitions

```
┌───────────────┐
│  (no session) │
└───────┬───────┘
        │ POST /api/upload (starts)
        ▼
┌───────────────┐
│  IN_PROGRESS  │◀────────────────┐
└───────┬───────┘                 │
        │                         │
   ┌────┼────┬────────────┐       │
   │    │    │            │       │
   │    │    │ Page close │       │ Resume
   │    │    │ (interrupted)      │
   │    │    ▼            │       │
   │    │ ┌────────┐      │       │
   │    │ │ Stale  │──────┼───────┘
   │    │ └────────┘      │
   │    │                 │
   │    │ Error           │ DELETE /api/incomplete-uploads/{id}
   │    ▼                 ▼
   │ ┌────────┐     ┌───────────┐
   │ │ FAILED │     │ CANCELLED │
   │ └────────┘     └───────────┘
   │
   │ Upload complete
   ▼
┌───────────────┐
│   COMPLETED   │
└───────────────┘
```

