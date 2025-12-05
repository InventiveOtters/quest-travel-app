# Data Model: TUS Protocol Upload Refactor

**Branch**: `001-tus-upload-refactor` | **Date**: 2025-12-05

> **Note**: No migration required - app reinstall is acceptable for schema changes.

## Architecture Overview

The **tus-java-server** library manages its own upload state via the `UploadStorageService` interface. We implement a custom storage service that:

1. Stores upload metadata in **Room Database** (`UploadSession` entity)
2. Writes file bytes to **MediaStore** via existing `MediaStoreUploader`

```
┌─────────────────────────────────────────────────────────────────┐
│                      tus-java-server                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │          TusFileUploadService (library)                  │   │
│  │   - Parses TUS protocol headers                          │   │
│  │   - Validates requests                                   │   │
│  │   - Manages upload lifecycle                             │   │
│  └──────────────────────┬──────────────────────────────────┘   │
└─────────────────────────┼───────────────────────────────────────┘
                          │ implements
          ┌───────────────▼───────────────┐
          │ MediaStoreUploadStorageService │  ← OUR CODE (custom)
          │   - UploadInfo → Room DB       │
          │   - Bytes → MediaStore         │
          └───────────────────────────────┘
```

---

## Entity: UploadSession

**Location**: `app/src/main/java/com/inotter/travelcompanion/data/datasources/videolibrary/models/UploadSession.kt`

### Schema (maps to tus-java-server UploadInfo)

| Field | Type | TUS Mapping | Description |
|-------|------|-------------|-------------|
| `id` | `Long` | - | Auto-generated primary key |
| `tusUploadId` | `String` | `uploadInfo.id` | Unique TUS upload ID (UUID) |
| `uploadUrl` | `String` | `uploadInfo.uploadUrl` | Full TUS upload URL for resume |
| `filename` | `String` | `uploadInfo.metadata["filename"]` | Original filename |
| `expectedSize` | `Long` | `uploadInfo.length` | Total expected file size |
| `bytesReceived` | `Long` | `uploadInfo.offset` | Bytes successfully written |
| `mediaStoreUri` | `String` | - | Content URI of pending MediaStore entry |
| `mimeType` | `String` | `uploadInfo.metadata["filetype"]` | File MIME type |
| `createdAt` | `Long` | `uploadInfo.creationTimestamp` | Creation timestamp |
| `lastUpdatedAt` | `Long` | - | Last progress update timestamp |
| `status` | `UploadSessionStatus` | - | ENUM: IN_PROGRESS, COMPLETED, FAILED, CANCELLED |

### Entity Definition

```kotlin
@Entity(
    tableName = "upload_sessions",
    indices = [
        Index(value = ["tusUploadId"], unique = true),
        Index(value = ["uploadUrl"], unique = true),
        Index(value = ["mediaStoreUri"], unique = true)
    ]
)
data class UploadSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tusUploadId: String,              // Maps to UploadInfo.id
    val uploadUrl: String,                // Maps to UploadInfo.uploadUrl (for lookup)
    val filename: String,
    val expectedSize: Long,
    val bytesReceived: Long = 0,
    val mediaStoreUri: String,
    val mimeType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val status: UploadSessionStatus = UploadSessionStatus.IN_PROGRESS
) {
    fun isExpired(): Boolean = 
        System.currentTimeMillis() - createdAt > 24 * 60 * 60 * 1000
        
    val progressPercent: Int
        get() = if (expectedSize > 0) ((bytesReceived * 100) / expectedSize).toInt().coerceIn(0, 100) else 0
}
```

---

## MediaStoreUploadStorageService

**Location**: `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/MediaStoreUploadStorageService.kt`

This class implements `UploadStorageService` from tus-java-server:

```kotlin
class MediaStoreUploadStorageService(
    private val uploadSessionRepository: UploadSessionRepository,
    private val mediaStoreUploader: MediaStoreUploader,
    private val coroutineScope: CoroutineScope
) : UploadStorageService {

    override fun getUploadInfo(uploadUrl: String?, ownerKey: String?): UploadInfo? {
        // Lookup in Room DB by uploadUrl, convert to UploadInfo
    }

    override fun create(uploadInfo: UploadInfo, ownerKey: String?): UploadInfo {
        // 1. Create pending MediaStore entry
        // 2. Store UploadSession in Room
        // 3. Return UploadInfo with assigned ID
    }

    override fun append(uploadInfo: UploadInfo, inputStream: InputStream): UploadInfo {
        // 1. Get MediaStore URI from Room
        // 2. Append bytes via MediaStoreUploader.getAppendOutputStream()
        // 3. Update bytesReceived in Room
        // 4. Return updated UploadInfo
    }

    override fun terminateUpload(uploadInfo: UploadInfo, ownerKey: String?) {
        // 1. Delete pending MediaStore entry
        // 2. Delete UploadSession from Room
    }

    override fun cleanupExpiredUploads(lockingService: UploadLockingService?) {
        // 1. Query expired sessions (>24h)
        // 2. Delete MediaStore entries
        // 3. Delete Room records
    }
}
```

---

## DAO Queries

```kotlin
@Query("SELECT * FROM upload_sessions WHERE uploadUrl = :url")
suspend fun getUploadSessionByUrl(url: String): UploadSession?

@Query("SELECT * FROM upload_sessions WHERE tusUploadId = :tusId")
suspend fun getUploadSessionByTusId(tusId: String): UploadSession?

@Query("SELECT * FROM upload_sessions WHERE status = 'IN_PROGRESS' ORDER BY createdAt DESC")
suspend fun getIncompleteUploadSessions(): List<UploadSession>

@Query("DELETE FROM upload_sessions WHERE createdAt < :cutoff AND status = 'IN_PROGRESS'")
suspend fun deleteExpiredSessions(cutoff: Long): Int

@Query("UPDATE upload_sessions SET bytesReceived = :bytes, lastUpdatedAt = :now WHERE tusUploadId = :tusId")
suspend fun updateUploadProgress(tusId: String, bytes: Long, now: Long = System.currentTimeMillis())
```

---

## Client-Side Storage

tus-js-client handles resume state automatically via localStorage. No custom schema needed.

