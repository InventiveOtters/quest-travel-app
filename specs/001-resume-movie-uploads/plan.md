# Implementation Plan: Resume Incomplete Movie Uploads

**Branch**: `001-resume-movie-uploads` | **Date**: 2024-12-04 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-resume-movie-uploads/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Implement resumable movie uploads with browser navigation protection. When users attempt to leave the page during an active upload, a browser-native `beforeunload` warning prevents accidental data loss. On return, users are informed of incomplete uploads and can resume (reselecting the same file continues from where it left off) or delete them. The 7-day expiration policy ensures stale uploads are auto-cleaned.

**Technical Approach**: Leverage existing `UploadSession` entity and `IncompleteUploadDetector` on the Android backend. Extend frontend `upload.js` with `beforeunload` handlers, file-matching logic (filename + size), and delete functionality. Add backend endpoint for deleting incomplete uploads.

## Technical Context

**Language/Version**: Kotlin 1.9+ (Android backend), JavaScript ES6 (web frontend)
**Primary Dependencies**: NanoHTTPD (embedded HTTP server), Room (database), Hilt (DI), Jetpack Compose (UI)
**Storage**: Room SQLite database (`upload_sessions` table), Android MediaStore (video files)
**Testing**: JUnit 4 (unit tests), AndroidJUnitRunner + Espresso (instrumented tests)
**Target Platform**: Meta Quest / HorizonOS (Android 14, API 34)
**Project Type**: VR/Android mobile app with embedded web interface for file transfer
**Performance Goals**: Large file uploads (up to 10GB), smooth resumption with <5s detection time
**Constraints**: Browser-based frontend (limited by `beforeunload` browser behavior), single-device upload sessions (no cross-device resume)
**Scale/Scope**: Single-user device, local WiFi transfer, moderate file count (10-50 movies typically)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The project constitution (`.specify/memory/constitution.md`) is a template with placeholder content. The following principles are applied based on common best practices:

| Gate | Status | Notes |
|------|--------|-------|
| **Test-First** | ✅ PASS | Feature can be developed with TDD approach; existing test infrastructure available |
| **Simplicity** | ✅ PASS | Leverages existing infrastructure (UploadSession, IncompleteUploadDetector); minimal new entities |
| **Observability** | ✅ PASS | Existing logging in TransferService; new upload states will be logged |
| **Integration Testing** | ✅ PASS | Will require integration tests for upload flow; existing patterns available |

**Pre-Phase 0 Gate Result**: ✅ **PASS** - No violations detected. Proceed to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/001-resume-movie-uploads/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
app/
├── src/main/
│   ├── java/com/inotter/travelcompanion/
│   │   ├── data/
│   │   │   ├── datasources/videolibrary/
│   │   │   │   ├── dao/UploadSessionDao.kt        # Existing - CRUD operations
│   │   │   │   └── models/UploadSession.kt        # Existing - Upload session entity
│   │   │   ├── managers/TransferManager/
│   │   │   │   ├── UploadServer.kt                # Existing - HTTP server, needs resume endpoint
│   │   │   │   ├── IncompleteUploadDetector.kt    # Existing - Detection logic
│   │   │   │   └── models/TransferModels.kt       # Existing - ResumableUpload model
│   │   │   └── repositories/UploadSessionRepository/
│   │   │       ├── UploadSessionRepository.kt     # Existing - Repository interface
│   │   │       └── UploadSessionRepositoryImpl.kt # Existing - Repository implementation
│   │   └── TransferService.kt                     # Existing - Service orchestration
│   └── assets/transfer/
│       ├── index.html                             # Existing - Web UI structure
│       ├── style.css                              # Existing - Styling
│       └── upload.js                              # Existing - MODIFY for beforeunload, file matching, delete
└── src/test/java/com/inotter/travelcompanion/
    └── [unit tests to be added]
```

**Structure Decision**: Android mobile app with embedded web interface. Backend is Kotlin/Android with Room database; frontend is vanilla JavaScript served via NanoHTTPD. No separate frontend/backend projects—all within the `app/` module.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations to justify. Design leverages existing infrastructure with minimal additions.*

---

## Post-Design Constitution Re-Check

*Evaluated after Phase 1 design completion.*

| Gate | Status | Post-Design Notes |
|------|--------|-------------------|
| **Test-First** | ✅ PASS | Design enables unit tests for file matching logic, integration tests for API endpoints |
| **Simplicity** | ✅ PASS | Only 1 new endpoint added; reuses existing UploadSession, IncompleteUploadDetector |
| **Observability** | ✅ PASS | Delete operations will be logged; existing progress tracking remains |
| **Integration Testing** | ✅ PASS | API contract defined in OpenAPI; testable via HTTP client |

**Post-Phase 1 Gate Result**: ✅ **PASS** - Design complies with all principles. Ready for Phase 2 task generation.
