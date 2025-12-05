# Implementation Plan: TUS Protocol Upload Refactor

**Branch**: `001-tus-upload-refactor` | **Date**: 2025-12-04 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-tus-upload-refactor/spec.md`

## Summary

Refactor the WiFi file upload system from the current NanoHTTPD multipart form implementation to use the TUS (tus.io) resumable upload protocol. This enables true byte-level resume capability for interrupted uploads, replacing the current non-functional resume mechanism. The implementation involves:

1. **Server-side**: Replace the `/api/upload` multipart handler with TUS protocol endpoints in the existing UploadServer
2. **Client-side**: Replace XMLHttpRequest-based uploads in upload.js with tus-js-client library
3. **Session tracking**: Simplify the UploadSession entity to TUS-focused schema (no migration needed - app reinstall acceptable)

## Technical Context

**Language/Version**: Kotlin 1.9+ (Android), JavaScript ES6+ (browser client)
**Primary Dependencies**: NanoHTTPD (existing HTTP server), tus-js-client (new - browser), Room (existing - session persistence)
**Storage**: Room Database (session metadata), MediaStore (video files via IS_PENDING pattern)
**Testing**: JUnit + Android instrumented tests (existing patterns)
**Target Platform**: Meta Quest / HorizonOS (Android 14, API 34)
**Project Type**: Mobile (Android) with embedded web client
**Performance Goals**: 1GB upload in <3 min over 50Mbps WiFi, 95% resume success rate
**Constraints**: Single upload at a time, 24-hour session expiration, local WiFi only
**Scale/Scope**: Single-user local transfers, files up to device storage capacity

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. MVVM Architecture** | ✅ PASS | Upload logic remains in TransferService/UploadServer (not ViewModel); UI updates flow through existing StateFlow |
| **II. Layered Data Architecture** | ✅ PASS | Uses existing layers: UploadSessionRepository (repository), VideoLibraryDataSource (datasource), TransferManager (manager) |
| **III. Documentation Standards** | ✅ PASS | All new classes will have KDoc; TUS protocol integration documented |
| **IV. Hilt Dependency Injection** | ✅ PASS | New components will be provided via existing DI modules |
| **V. Simplicity and Maintainability** | ✅ PASS | Refactors existing code rather than adding new layers; TUS protocol is well-established standard |

**Gate Result**: PASS - No constitution violations. Design may proceed.

## Project Structure

### Documentation (this feature)

```text
specs/001-tus-upload-refactor/
├── plan.md              # This file
├── research.md          # Phase 0: TUS protocol research
├── data-model.md        # Phase 1: Extended UploadSession schema
├── quickstart.md        # Phase 1: Development setup guide
├── contracts/           # Phase 1: TUS endpoint specifications
│   └── tus-api.md       # TUS protocol endpoint contracts
└── tasks.md             # Phase 2: Implementation tasks (created by /speckit.tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/inotter/travelcompanion/
├── TransferService.kt                    # UPDATE: Wire TUS server callbacks
├── data/
│   ├── datasources/videolibrary/
│   │   ├── models/
│   │   │   └── UploadSession.kt          # SIMPLIFY: TUS-focused schema
│   │   └── VideoLibraryDao.kt            # UPDATE: Add TUS queries
│   ├── repositories/UploadSessionRepository/
│   │   ├── UploadSessionRepository.kt    # UPDATE: New TUS methods
│   │   └── UploadSessionRepositoryImpl.kt
│   └── managers/TransferManager/
│       ├── UploadServer.kt               # UPDATE: Add TUS protocol handlers
│       ├── TusProtocolHandler.kt         # NEW: TUS protocol implementation
│       └── MediaStoreUploader.kt         # Minimal changes (append already supported)
│
app/src/main/assets/transfer/
├── upload.js                             # UPDATE: Replace with tus-js-client
├── tus.min.js                            # NEW: tus-js-client library
├── index.html                            # UPDATE: Include tus.min.js
└── style.css                             # No changes expected
```

**Structure Decision**: Mobile + embedded web client. Follows existing Android architecture conventions with TransferManager folder containing upload-related components. New TusProtocolHandler class encapsulates TUS-specific logic while UploadServer remains the HTTP routing layer.

## Complexity Tracking

No constitution violations requiring justification.

## Post-Phase 1 Constitution Re-Check

*Performed after design artifacts completed.*

| Principle | Status | Verification |
|-----------|--------|--------------|
| **I. MVVM Architecture** | ✅ PASS | TusProtocolHandler is utility class, not ViewModel; no UI code in data layer |
| **II. Layered Data Architecture** | ✅ PASS | Data model changes stay in datasources/models; new queries in DAO; repository methods added |
| **III. Documentation Standards** | ✅ PASS | contracts/tus-api.md documents all endpoints; data-model.md documents schema |
| **IV. Hilt Dependency Injection** | ✅ PASS | TusProtocolHandler will be injected into UploadServer; no manual construction |
| **V. Simplicity and Maintainability** | ✅ PASS | Single new class (TusProtocolHandler); reuses existing patterns; standard protocol |

**Final Gate Result**: PASS - Design is constitution-compliant. Ready for task generation.

## Generated Artifacts

| Artifact | Path | Purpose |
|----------|------|---------|
| Research | `research.md` | TUS protocol research, library decisions |
| Data Model | `data-model.md` | Simplified UploadSession schema (no migration) |
| API Contract | `contracts/tus-api.md` | TUS endpoint specifications |
| Quickstart | `quickstart.md` | Development setup guide |
