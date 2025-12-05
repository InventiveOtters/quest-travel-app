# Research: TUS Protocol Upload Refactor

**Branch**: `001-tus-upload-refactor` | **Date**: 2025-12-04

## Research Tasks

### 1. TUS Protocol Server Implementation for NanoHTTPD

**Task**: Research how to implement TUS protocol server-side within NanoHTTPD (existing HTTP server)

**Decision**: Implement TUS protocol handlers directly in UploadServer.kt as additional route handlers

**Rationale**: 
- TUS protocol is HTTP-based with specific headers and endpoints; NanoHTTPD can handle these
- No need for external TUS server library - protocol is simple enough to implement directly
- Keeps consistency with existing codebase architecture

**Alternatives Considered**:
- Using a full TUS server library (e.g., tusd): Rejected - would require replacing NanoHTTPD entirely, too heavy for embedded use case
- Using Ktor or another framework: Rejected - unnecessary complexity, NanoHTTPD works well

**Key TUS Protocol Requirements** (from tus.io/protocols/resumable-upload):
1. `POST /files` - Create upload, returns `Location` header with upload URL
2. `HEAD /files/{id}` - Get upload offset (bytes received)
3. `PATCH /files/{id}` - Upload chunk at offset
4. `OPTIONS /files` - Capability discovery (extensions supported)
5. `DELETE /files/{id}` - Cancel/cleanup upload (optional)

**Required Headers**:
- `Tus-Resumable: 1.0.0` - Protocol version
- `Upload-Offset` - Current byte offset
- `Upload-Length` - Total file size
- `Upload-Metadata` - Base64-encoded filename and metadata

---

### 2. tus-js-client Integration

**Task**: Research best practices for integrating tus-js-client in embedded web assets

**Decision**: Use tus-js-client v4.x minified build, bundled in assets/transfer/

**Rationale**:
- Official client library with excellent browser support
- Handles chunking, retries, and fingerprinting automatically
- Well-documented API for progress callbacks

**Alternatives Considered**:
- Uppy (tus-based): Rejected - too heavy, includes UI components we don't need
- Custom implementation: Rejected - reinventing the wheel, tus-js-client is battle-tested

**Integration Approach**:
1. Download `tus.min.js` from CDN or npm package
2. Include via `<script>` tag in index.html
3. Configure with server endpoint `/tus/` 
4. Use fingerprint for resume identification

**Key Configuration**:
```javascript
const upload = new tus.Upload(file, {
    endpoint: "/tus/",
    retryDelays: [0, 1000, 3000, 5000],
    chunkSize: 5 * 1024 * 1024, // 5MB chunks
    metadata: {
        filename: file.name,
        filetype: file.type
    },
    onProgress: (bytesUploaded, bytesTotal) => { ... },
    onSuccess: () => { ... },
    onError: (error) => { ... }
});
upload.start();
```

---

### 3. Resume Identification Strategy

**Task**: Research how to identify uploads for resume (file fingerprinting)

**Decision**: Use tus-js-client's built-in fingerprinting + server-generated upload ID

**Rationale**:
- tus-js-client handles all client-side resume state automatically
- Built-in fingerprint = `[filename, type, size, lastModified, endpoint].join('-')`
- Server stores fingerprint in UploadSession for validation
- No custom localStorage management needed

**Alternatives Considered**:
- Custom fingerprinting: Rejected - tus-js-client's default is sufficient
- SHA-256 hash: Rejected - overkill for local WiFi transfers

**Implementation**:
- Client: tus-js-client auto-stores resume URL in localStorage
- Server: Stores fingerprint from `Upload-Metadata` header
- Resume: Client finds previous upload URL, issues HEAD to get offset, then PATCH

---

### 4. MediaStore Append Pattern

**Task**: Research appending to existing MediaStore entries for resume

**Decision**: Use ContentResolver `openOutputStream(uri, "wa")` (write-append mode)

**Rationale**:
- Existing `MediaStoreUploader.getAppendOutputStream()` already supports this
- IS_PENDING flag keeps file hidden until finalized
- No changes needed to MediaStore integration pattern

**Verification**: Current code already has:
```kotlin
fun getAppendOutputStream(uri: Uri): OutputStream? {
    return contentResolver.openOutputStream(uri, "wa")?.let { ... }
}
```

---

### 5. Session Expiration and Cleanup

**Task**: Research automatic cleanup of expired sessions (24-hour expiration per spec)

**Decision**: Use WorkManager periodic task for cleanup

**Rationale**:
- WorkManager already used in project (MediaStoreScanWorker, IndexWorker)
- Periodic cleanup runs even if app not actively used
- Room query can delete sessions older than 24 hours

**Implementation**:
- Add `cleanupOldSessions(maxAgeMillis: Long)` to UploadSessionRepository (already exists)
- Schedule WorkManager task to run daily
- On session cleanup: delete Room record AND MediaStore pending entry

---

## Resolved Clarifications

All technical unknowns from Technical Context have been resolved:

| Unknown | Resolution |
|---------|------------|
| TUS server library | Implement directly in NanoHTTPD |
| TUS client library | tus-js-client v4.x |
| Resume fingerprinting | Server-generated ID + client localStorage |
| MediaStore append | Existing `getAppendOutputStream("wa")` |
| Session cleanup | WorkManager periodic task |

