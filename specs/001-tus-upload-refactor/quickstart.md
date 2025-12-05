# Quickstart: TUS Protocol Upload Refactor

**Branch**: `001-tus-upload-refactor` | **Date**: 2025-12-04

## Prerequisites

- Android Studio Hedgehog or newer
- Meta Quest device with developer mode enabled
- ADB connection to device
- Node.js (for downloading tus-js-client if needed)

## Setup Steps

### 1. Switch to Feature Branch

```bash
git checkout 001-tus-upload-refactor
```

### 2. Get tus-js-client Library

Download the minified build:

```bash
# Option A: From CDN
curl -o app/src/main/assets/transfer/tus.min.js \
  https://cdn.jsdelivr.net/npm/tus-js-client@4/dist/tus.min.js

# Option B: From npm
npm pack tus-js-client
tar -xzf tus-js-client-*.tgz
cp package/dist/tus.min.js app/src/main/assets/transfer/
rm -rf package tus-js-client-*.tgz
```

### 3. Build and Deploy

```bash
./gradlew :app:installDebug
```

### 4. Test Upload Flow

1. Enable WiFi transfer in app settings
2. Connect computer to same WiFi network
3. Open browser to `http://{quest-ip}:8080`
4. Select a test video file (recommend 100MB+ to test resume)
5. Start upload, verify progress
6. Interrupt WiFi mid-upload (toggle airplane mode on Quest)
7. Restore WiFi, verify resume prompt appears
8. Complete upload, verify file in library

## Key Files to Modify

| File | Changes |
|------|---------|
| `UploadSession.kt` | Simplify to TUS-focused schema |
| `VideoLibraryDao.kt` | Add TUS queries |
| `UploadServer.kt` | Add TUS route handlers |
| `TusProtocolHandler.kt` | NEW: TUS protocol logic |
| `upload.js` | Replace XHR with tus-js-client |
| `index.html` | Include tus.min.js |

## Testing TUS Endpoints Manually

### Create Upload
```bash
curl -X POST http://{quest-ip}:8080/tus/ \
  -H "Tus-Resumable: 1.0.0" \
  -H "Upload-Length: 1000000" \
  -H "Upload-Metadata: filename dGVzdC5tcDQ=,filetype dmlkZW8vbXA0" \
  -H "Content-Length: 0" \
  -v
```

### Check Offset
```bash
curl -X HEAD http://{quest-ip}:8080/tus/{upload-id} \
  -H "Tus-Resumable: 1.0.0" \
  -v
```

### Upload Chunk
```bash
curl -X PATCH http://{quest-ip}:8080/tus/{upload-id} \
  -H "Tus-Resumable: 1.0.0" \
  -H "Upload-Offset: 0" \
  -H "Content-Type: application/offset+octet-stream" \
  --data-binary @test-chunk.bin \
  -v
```

## Debugging Tips

### View TUS-related logs
```bash
adb logcat -s TusProtocolHandler:V UploadServer:V TransferService:V
```

### Check session database
```bash
adb shell "run-as com.inotter.travelcompanion cat databases/video_library.db" | sqlite3
sqlite> SELECT * FROM upload_sessions WHERE status = 'IN_PROGRESS';
```

### Clear pending uploads for fresh test
```bash
adb shell am broadcast -a com.inotter.travelcompanion.CLEAR_UPLOADS
```

## Common Issues

| Issue | Solution |
|-------|----------|
| "Port in use" error | Kill other apps using port 8080, or check fallback ports |
| Resume not working | Check localStorage in browser DevTools, verify fingerprint matches |
| MediaStore entry stuck as pending | Session may have expired; run cleanup |
| 409 Conflict on PATCH | Client offset doesn't match server; issue HEAD first to sync |

