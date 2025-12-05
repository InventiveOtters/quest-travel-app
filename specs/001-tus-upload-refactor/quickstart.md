# Quickstart: TUS Protocol Upload Refactor

**Branch**: `001-tus-upload-refactor` | **Date**: 2025-12-05

## Prerequisites

- Android Studio Hedgehog or newer
- Meta Quest device with developer mode enabled
- ADB connection to device
- Node.js (for downloading tus-js-client)

## Setup Steps

### 1. Switch to Feature Branch

```bash
git checkout 001-tus-upload-refactor
```

### 2. Add Dependencies (build.gradle.kts)

```kotlin
dependencies {
    // Jetty Embedded (replaces NanoHTTPD)
    implementation("org.eclipse.jetty:jetty-server:11.0.18")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.18")
    
    // TUS Server Library (handles all TUS protocol)
    implementation("me.desair.tus:tus-java-server:1.0.0-2.5")
    
    // Remove NanoHTTPD
    // implementation("org.nanohttpd:nanohttpd:2.3.1")  // DELETE THIS
}
```

### 3. Get tus-js-client Library

```bash
# Download from CDN
curl -o app/src/main/assets/transfer/tus.min.js \
  https://cdn.jsdelivr.net/npm/tus-js-client@4/dist/tus.min.js
```

### 4. Build and Deploy

```bash
./gradlew :app:installDebug
```

### 5. Test Upload Flow

1. Enable WiFi transfer in app settings
2. Connect computer to same WiFi network
3. Open browser to `http://{quest-ip}:8080`
4. Select a test video file (recommend 100MB+ to test resume)
5. Start upload, verify progress
6. Interrupt WiFi mid-upload (toggle airplane mode on Quest)
7. Restore WiFi, verify resume works automatically
8. Complete upload, verify file in library

## Key Files to Modify

| File | Changes |
|------|---------|
| `build.gradle.kts` | Add Jetty + tus-java-server dependencies |
| `UploadServer.kt` | REWRITE: Replace NanoHTTPD with Jetty |
| `TusUploadServlet.kt` | NEW: Servlet wrapper for TusFileUploadService |
| `MediaStoreUploadStorageService.kt` | NEW: Custom storage adapter |
| `UploadSession.kt` | UPDATE: Add uploadUrl field |
| `upload.js` | UPDATE: Use tus-js-client |
| `index.html` | UPDATE: Include tus.min.js |

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

### View server logs
```bash
adb logcat -s JettyServer:V TusUploadServlet:V TransferService:V
```

### Check session database
```bash
adb shell "run-as com.inotter.travelcompanion cat databases/video_library.db" | sqlite3
sqlite> SELECT * FROM upload_sessions WHERE status = 'IN_PROGRESS';
```

### Clear pending uploads
```bash
adb shell am broadcast -a com.inotter.travelcompanion.CLEAR_UPLOADS
```

## Common Issues

| Issue | Solution |
|-------|----------|
| "Port in use" error | Kill other apps using port 8080, or check fallback ports |
| Resume not working | Check localStorage in browser DevTools, verify fingerprint matches |
| Jetty startup failure | Ensure no ProGuard rules strip Jetty classes |
| MediaStore entry stuck | Session expired; run cleanup via WorkManager |
| 409 Conflict on PATCH | Client offset mismatch; tus-js-client handles this automatically |

## Architecture Overview

```
Browser                          Meta Quest Device
┌──────────────┐                ┌───────────────────────────────────┐
│ tus-js-client│  ─────────────>│  Jetty Embedded Server            │
│ (upload.js)  │     HTTP       │    ├── TusUploadServlet           │
└──────────────┘                │    │     └── TusFileUploadService │
                                │    │           (library code)      │
                                │    │              │                │
                                │    │    MediaStoreUploadStorage    │
                                │    │              │                │
                                │    │    ┌────────┴────────┐       │
                                │    │    ▼                 ▼       │
                                │  Room DB          MediaStore      │
                                │ (metadata)        (video bytes)   │
                                └───────────────────────────────────┘
```

