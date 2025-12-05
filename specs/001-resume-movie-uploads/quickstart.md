# Quickstart: Resume Incomplete Movie Uploads

**Feature Branch**: `001-resume-movie-uploads`

## Prerequisites

- Android Studio (Arctic Fox or later)
- Meta Quest device connected via ADB
- WiFi connection between development machine and Quest

## Development Setup

```bash
# Clone and checkout feature branch
git checkout 001-resume-movie-uploads

# Build the app
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Key Files to Modify

### Frontend (JavaScript)

| File | Changes Required |
|------|-----------------|
| `app/src/main/assets/transfer/upload.js` | Add `beforeunload` handler, file matching, delete UI |

### Backend (Kotlin)

| File | Changes Required |
|------|-----------------|
| `app/src/main/java/.../UploadServer.kt` | Add `DELETE /api/incomplete-uploads/{id}` endpoint |

## Implementation Checklist

### P1: Upload In-Progress Warning
- [ ] Add `beforeUnloadHandler` function in upload.js
- [ ] Track active uploads with `hasActiveUploads()` helper
- [ ] Add/remove listener when uploads start/complete
- [ ] Test: Start upload → refresh page → verify browser warning

### P2: Incomplete Upload Detection & Recovery  
- [ ] Extend `showIncompleteUploadsUI()` with delete buttons
- [ ] Add `deleteIncompleteUpload(sessionId)` function
- [ ] Add `matchFileToSession(file)` for file identity verification
- [ ] Add file mismatch warning dialog
- [ ] Add DELETE endpoint in UploadServer.kt
- [ ] Test: Interrupt upload → reopen page → verify notification

### P3: Multiple Upload Management
- [ ] Add "Resume All" button
- [ ] Add "Delete All" with confirmation dialog
- [ ] Queue multiple resume operations
- [ ] Test: Create multiple incomplete → verify batch operations

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/incomplete-uploads` | List resumable uploads (exists) |
| DELETE | `/api/incomplete-uploads/{sessionId}` | Delete incomplete upload (new) |

## Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

### Manual Testing Scenarios

1. **beforeunload Test**
   - Start large file upload
   - Click browser refresh/close
   - Verify "Leave site?" dialog appears

2. **Resume Flow Test**
   - Start upload, close browser at ~50%
   - Reopen page
   - Verify incomplete upload notification
   - Select same file → verify resume from 50%

3. **File Mismatch Test**
   - Create incomplete upload for `movie.mp4 (2GB)`
   - Click Resume, select `other.mp4 (1GB)`
   - Verify mismatch warning dialog

4. **Delete Flow Test**
   - Create incomplete upload
   - Click Delete button
   - Verify removed from list and MediaStore

## Architecture Notes

```
Browser (upload.js)                    Quest Device (UploadServer.kt)
─────────────────                      ─────────────────────────────
                                       
    beforeunload ──┐                   
         │         │                   
    File Select ───┤                   
         │         │                   
   ┌─────▼─────┐   │    POST           ┌─────────────────┐
   │ Match to  │───┼────/api/upload───▶│  UploadSession  │
   │ Session?  │   │                   │    (Room DB)    │
   └───────────┘   │                   └─────────────────┘
                   │                            │
   ┌───────────┐   │    GET                     │
   │ Show      │◀──┼────/api/incomplete-────────┘
   │ Incomplete│   │    uploads
   └───────────┘   │
         │         │    DELETE
   Delete ─────────┴────/api/incomplete-uploads/{id}
```

## Common Issues

| Issue | Solution |
|-------|----------|
| beforeunload not firing | Ensure user has interacted with page first |
| Incomplete list empty | Check Room database: `adb shell` → inspect SQLite |
| Delete fails 500 | MediaStore entry may already be cleaned by system |

