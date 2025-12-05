# Research: Resume Incomplete Movie Uploads

**Created**: 2024-12-04  
**Feature Branch**: `001-resume-movie-uploads`

## Research Tasks

| Unknown | Research Task | Status |
|---------|--------------|--------|
| Browser navigation warning | Best practices for `beforeunload` event | ✅ Resolved |
| File identity matching | Pattern for matching resumed files | ✅ Resolved |
| Upload state persistence | Where/how to store client-side upload state | ✅ Resolved |
| Delete endpoint | API design for deleting incomplete uploads | ✅ Resolved |
| Expiration handling | 7-day auto-cleanup implementation | ✅ Resolved |

---

## Decision 1: Browser Navigation Warning (`beforeunload`)

**Decision**: Use browser-native `beforeunload` event with `event.preventDefault()` as the primary mechanism, with `event.returnValue = true` for legacy browser support.

**Rationale**:
- MDN recommends `preventDefault()` as the modern best practice
- Browser shows standardized "Leave site?" dialog (cannot be customized per spec clarification)
- Only attach listener when uploads are active (performance best practice)
- Requires "sticky activation" (user interaction) before browser shows dialog

**Implementation Pattern**:
```javascript
const beforeUnloadHandler = (event) => {
  event.preventDefault();
  event.returnValue = true; // Legacy support
};

// Add when upload starts
window.addEventListener("beforeunload", beforeUnloadHandler);

// Remove when all uploads complete
window.removeEventListener("beforeunload", beforeUnloadHandler);
```

**Alternatives Considered**:
- Custom modal dialog: Rejected—cannot prevent actual navigation, only browser-native dialog works
- `onunload` event: Rejected—fires after navigation decision, too late for warning

---

## Decision 2: File Identity Matching for Resume

**Decision**: Match files using `filename + expectedSize` combination (already stored in `UploadSession`).

**Rationale**:
- Spec clarification confirms this approach (no new fields needed)
- Simple and reliable for single-device use case
- File hash/checksum rejected due to performance cost for large files (up to 10GB)

**Implementation Pattern**:
```javascript
// On file selection for resume
function matchFileToSession(file, incompleteUploads) {
  return incompleteUploads.find(
    upload => upload.filename === file.name && upload.expectedSize === file.size
  );
}
```

**Edge Case - Mismatch Handling** (per spec):
- Show warning with details: "Expected: movie.mp4 (2.5 GB), Selected: other.mp4 (1.2 GB)"
- Offer options: "Start New Upload" or "Cancel"

---

## Decision 3: Client-Side Upload State

**Decision**: No additional client-side persistence needed; rely on server-side `UploadSession` table and `/api/incomplete-uploads` endpoint.

**Rationale**:
- Server already tracks upload sessions in Room database (`upload_sessions` table)
- Frontend fetches incomplete uploads on page load via existing endpoint
- Avoids localStorage quota issues for large file metadata
- Cross-session resume works automatically via server state

**Alternatives Considered**:
- localStorage: Rejected—not needed since server tracks state; would duplicate data
- IndexedDB: Rejected—overkill for session metadata already on server

---

## Decision 4: Delete Incomplete Upload API

**Decision**: Add `DELETE /api/incomplete-uploads/{sessionId}` endpoint to UploadServer.

**Rationale**:
- RESTful design (DELETE verb for removal)
- Session ID as path parameter (already exposed in `/api/incomplete-uploads` response)
- Backend cleans up both database record AND MediaStore pending entry

**Implementation Requirements**:
1. Validate session exists and is in `IN_PROGRESS` status
2. Delete MediaStore pending entry via `contentResolver.delete()`
3. Delete database record via `UploadSessionRepository.deleteSession()`
4. Return JSON response with success/failure status

---

## Decision 5: 7-Day Expiration Policy

**Decision**: Implement expiration at two levels:
1. **Server-side**: Cleanup worker deletes sessions older than 7 days
2. **Client-side**: UI marks expired uploads with warning, recommends deletion

**Rationale**:
- Per spec clarification: 7 days covers weekend gaps and typical usage patterns
- Existing `UploadSession.isOrphaned()` method already supports age check
- MediaStore pending entries with `IS_PENDING=1` may be auto-cleaned by system

**Implementation Pattern**:
```kotlin
// Already exists - extend for 7-day check
fun isOrphaned(maxAgeMillis: Long = 7 * 24 * 60 * 60 * 1000L): Boolean
```

---

## Technology Best Practices

### NanoHTTPD Route Handling

Per existing codebase patterns in `UploadServer.kt`:
- Routes parsed via `uri` and `method` in `serve()` override
- JSON responses use `MIME_JSON` constant
- Error handling returns appropriate HTTP status codes

### JavaScript Error Handling

Per existing `upload.js` patterns:
- Use `showError()` for user-facing error toasts
- Use `showToast()` for info/success messages
- Handle network errors gracefully with "check WiFi" messaging

