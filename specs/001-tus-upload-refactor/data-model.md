# Data Model: TUS Protocol Upload Refactor

**Branch**: `001-tus-upload-refactor` | **Date**: 2025-12-04

> **Note**: No migration required - app reinstall is acceptable for schema changes.

## Entity: UploadSession (Simplified)

**Location**: `app/src/main/java/com/inotter/travelcompanion/data/datasources/videolibrary/models/UploadSession.kt`

### Final Schema

| Field | Type | Description |
|-------|------|-------------|
| `id` | `Long` | Auto-generated primary key |
| `tusUploadId` | `String` | **PRIMARY IDENTIFIER** - Unique TUS upload ID (UUID), indexed |
| `filename` | `String` | Original filename |
| `expectedSize` | `Long` | Total expected file size in bytes |
| `bytesReceived` | `Long` | Bytes successfully written to MediaStore |
| `mediaStoreUri` | `String` | Content URI of pending MediaStore entry |
| `mimeType` | `String` | File MIME type (e.g., "video/mp4") |
| `fileFingerprint` | `String` | Client-generated hash for resume matching |
| `createdAt` | `Long` | Creation timestamp (epoch millis) |
| `lastUpdatedAt` | `Long` | Last progress update timestamp |
| `status` | `UploadSessionStatus` | ENUM: IN_PROGRESS, COMPLETED, FAILED, CANCELLED |

### Removed Fields (cleanup from old implementation)

The following fields from the current implementation can be removed as TUS handles these concerns:
- `uploadMetadata` - Not needed; metadata is in filename/mimeType
- `chunkSize` - Client-determined, not stored server-side
- `expiresAt` - Calculate dynamically: `createdAt + 24h`

### Entity Definition

```kotlin
@Entity(
    tableName = "upload_sessions",
    indices = [
        Index(value = ["tusUploadId"], unique = true),
        Index(value = ["mediaStoreUri"], unique = true)
    ]
)
data class UploadSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tusUploadId: String,           // UUID generated on POST /tus/
    val filename: String,
    val expectedSize: Long,
    val bytesReceived: Long = 0,
    val mediaStoreUri: String,
    val mimeType: String,
    val fileFingerprint: String,       // For client-side resume matching
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val status: UploadSessionStatus = UploadSessionStatus.IN_PROGRESS
) {
    /** Returns true if session has expired (older than 24 hours) */
    fun isExpired(): Boolean =
        System.currentTimeMillis() - createdAt > 24 * 60 * 60 * 1000

    val progressPercent: Int
        get() = if (expectedSize > 0) {
            ((bytesReceived * 100) / expectedSize).toInt().coerceIn(0, 100)
        } else 0
}
```

### State Transitions

```
[NEW] --> IN_PROGRESS (POST /tus/)
         |
         +--> COMPLETED (final PATCH completes file)
         |
         +--> FAILED (error during upload)
         |
         +--> CANCELLED (user cancels or session expires)
```

---

## DAO Queries

**Location**: `VideoLibraryDao.kt` (existing)

```kotlin
// Find session by TUS upload ID (primary lookup for PATCH/HEAD/DELETE)
@Query("SELECT * FROM upload_sessions WHERE tusUploadId = :tusId")
suspend fun getUploadSessionByTusId(tusId: String): UploadSession?

// Find active session by file fingerprint (for client resume matching)
@Query("SELECT * FROM upload_sessions WHERE fileFingerprint = :fingerprint AND status = 'IN_PROGRESS'")
suspend fun getUploadSessionByFingerprint(fingerprint: String): UploadSession?

// Get all in-progress sessions (for /api/incomplete-uploads endpoint)
@Query("SELECT * FROM upload_sessions WHERE status = 'IN_PROGRESS' ORDER BY createdAt DESC")
suspend fun getIncompleteUploadSessions(): List<UploadSession>

// Delete expired sessions (24 hours old)
@Query("DELETE FROM upload_sessions WHERE createdAt < :cutoff AND status = 'IN_PROGRESS'")
suspend fun deleteExpiredSessions(cutoff: Long): Int

// Get expired session URIs for MediaStore cleanup before deletion
@Query("SELECT mediaStoreUri FROM upload_sessions WHERE createdAt < :cutoff AND status = 'IN_PROGRESS'")
suspend fun getExpiredSessionUris(cutoff: Long): List<String>

// Update bytes received (called on each PATCH)
@Query("UPDATE upload_sessions SET bytesReceived = :bytes, lastUpdatedAt = :now WHERE tusUploadId = :tusId")
suspend fun updateUploadProgress(tusId: String, bytes: Long, now: Long = System.currentTimeMillis())
```

---

## Client-Side Storage

**localStorage Schema** (JavaScript):

tus-js-client handles fingerprinting and resume URLs automatically. No custom localStorage schema needed.

The library stores resume state using a fingerprint based on:
- Filename
- File size
- Last modified timestamp
- Endpoint URL

**Default tus-js-client behavior**:
```javascript
// tus-js-client automatically stores:
// Key: tus::${endpoint}::${fingerprint}
// Value: URL to resume upload

// Fingerprint algorithm (default):
fingerprint = [file.name, file.type, file.size, file.lastModified, endpoint].join('-')
```

**Cleanup**: tus-js-client has built-in `removeFingerprintOnSuccess` option (enabled by default).

