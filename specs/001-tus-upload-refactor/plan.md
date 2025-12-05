# Implementation Plan: TUS Protocol Upload Refactor

**Branch**: `001-tus-upload-refactor` | **Date**: 2025-12-05 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-tus-upload-refactor/spec.md`

## Summary

Refactor the WiFi file upload system to use the TUS (tus.io) resumable upload protocol with **existing, battle-tested libraries** - NO custom TUS protocol implementation. This enables true byte-level resume capability for interrupted uploads.

### Key Architectural Decision

> ⚠️ **CRITICAL: Use existing TUS libraries only. Do NOT implement TUS protocol from scratch.**

The implementation involves:

1. **HTTP Server**: Replace NanoHTTPD with **Jetty Embedded** (provides Servlet API required by tus-java-server)
2. **TUS Server**: Use **tus-java-server** library (MIT licensed, production-ready, all TUS extensions included)
3. **TUS Client**: Use **tus-js-client** library in browser (official tus.io client)
4. **Storage**: Integrate tus-java-server with existing MediaStore-based file storage

### Why This Approach

| Approach | Effort | Risk | Chosen |
|----------|--------|------|--------|
| Implement TUS from scratch | 5-7 days | HIGH - protocol complexity, edge cases | ❌ |
| NanoHTTPD + Servlet adapter | 2-3 days | MEDIUM - hacky wrapper code | ❌ |
| **Jetty + tus-java-server** | 2-3 days | LOW - proven libraries | ✅ |

## Technical Context

**Language/Version**: Kotlin 1.9+ (Android), JavaScript ES6+ (browser client)
**Primary Dependencies**:
- Jetty Embedded 11.x (NEW - replaces NanoHTTPD, provides Servlet API)
- tus-java-server 1.0.0-2.x (NEW - TUS protocol implementation, Java 11 compatible)
- tus-js-client 4.x (NEW - browser TUS client)
- Room (existing - session persistence)

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
| **I. MVVM Architecture** | ✅ PASS | Upload logic remains in TransferService (not ViewModel); UI updates flow through existing StateFlow |
| **II. Layered Data Architecture** | ✅ PASS | Uses existing layers: UploadSessionRepository, VideoLibraryDataSource, TransferManager |
| **III. Documentation Standards** | ✅ PASS | All new classes will have KDoc; library integration documented |
| **IV. Hilt Dependency Injection** | ✅ PASS | New components will be provided via existing DI modules |
| **V. Simplicity and Maintainability** | ✅ PASS | Uses existing libraries instead of custom implementation; reduces maintenance burden |

**Gate Result**: PASS - No constitution violations. Design may proceed.

## Project Structure

### Documentation (this feature)

```text
specs/001-tus-upload-refactor/
├── plan.md              # This file
├── research.md          # Phase 0: Library research and decisions
├── data-model.md        # Phase 1: TUS storage integration
├── quickstart.md        # Phase 1: Development setup guide
├── contracts/           # Phase 1: API endpoint specifications
│   └── tus-api.md       # TUS endpoint documentation (library-provided)
└── tasks.md             # Phase 2: Implementation tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/inotter/travelcompanion/
├── TransferService.kt                    # UPDATE: Wire Jetty server lifecycle
├── data/
│   ├── datasources/videolibrary/
│   │   ├── models/
│   │   │   └── UploadSession.kt          # UPDATE: Sync with TUS library state
│   │   └── VideoLibraryDao.kt            # UPDATE: Add TUS-related queries
│   ├── repositories/UploadSessionRepository/
│   │   ├── UploadSessionRepository.kt    # UPDATE: New TUS methods
│   │   └── UploadSessionRepositoryImpl.kt
│   └── managers/TransferManager/
│       ├── UploadServer.kt               # REWRITE: Jetty-based server
│       ├── TusUploadServlet.kt           # NEW: Servlet wrapping TusFileUploadService
│       ├── MediaStoreUploadStorageService.kt  # NEW: tus-java-server storage adapter
│       └── MediaStoreUploader.kt         # KEEP: Existing MediaStore operations
│
app/src/main/assets/transfer/
├── upload.js                             # UPDATE: Use tus-js-client
├── tus.min.js                            # NEW: tus-js-client library
├── index.html                            # UPDATE: Include tus.min.js
└── style.css                             # No changes expected
```

**Structure Decision**: Mobile + embedded web client. Jetty replaces NanoHTTPD as the HTTP server. The `tus-java-server` library handles all TUS protocol logic; we only provide a custom `UploadStorageService` to write files to MediaStore instead of filesystem.

## Complexity Tracking

No constitution violations requiring justification.

## Post-Phase 1 Constitution Re-Check

*Performed after design artifacts completed.*

| Principle | Status | Verification |
|-----------|--------|--------------|
| **I. MVVM Architecture** | ✅ PASS | TusUploadServlet is HTTP layer; no UI code in data layer |
| **II. Layered Data Architecture** | ✅ PASS | MediaStoreUploadStorageService adapts library to our storage; follows existing patterns |
| **III. Documentation Standards** | ✅ PASS | contracts/tus-api.md documents endpoints; data-model.md documents integration |
| **IV. Hilt Dependency Injection** | ✅ PASS | Jetty server and TUS service provided via Hilt modules |
| **V. Simplicity and Maintainability** | ✅ PASS | Uses proven libraries; minimal custom code; standard protocol |

**Final Gate Result**: PASS - Design is constitution-compliant. Ready for task generation.

## Generated Artifacts

| Artifact | Path | Purpose |
|----------|------|---------|
| Research | `research.md` | Library research, Jetty + tus-java-server decisions |
| Data Model | `data-model.md` | MediaStore storage adapter design |
| API Contract | `contracts/tus-api.md` | TUS endpoint documentation (library-provided) |
| Quickstart | `quickstart.md` | Development setup guide |

