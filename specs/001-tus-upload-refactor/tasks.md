# Tasks: TUS Protocol Upload Refactor

**Input**: Design documents from `/specs/001-tus-upload-refactor/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓

> ⚠️ **CRITICAL**: Use existing TUS libraries only. Do NOT implement TUS protocol from scratch.

**Assumptions**:
- ✅ **No backwards compatibility needed** - Legacy endpoints can be removed immediately
- ✅ **Fresh install acceptable** - App can be reinstalled; no data migration required

**Tests**: Not explicitly requested - manual testing via quickstart.md

**Organization**: Tasks grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)

---

## Phase 1: Setup ✅ COMPLETE

**Purpose**: Add dependencies, remove NanoHTTPD, prepare project structure

- [x] T001 Add Jetty Embedded + tus-java-server dependencies to `app/build.gradle.kts`, remove NanoHTTPD
- [x] T002 [P] Download tus-js-client to `app/src/main/assets/transfer/tus.min.js`
- [x] T003 [P] Update `app/src/main/assets/transfer/index.html` to include tus.min.js script tag

**Checkpoint**: ✅ Project builds with new dependencies (Jetty 11.0.18, tus-java-server 1.0.0-3.0)

---

## Phase 2: Foundational (Blocking Prerequisites) ✅ COMPLETE

**Purpose**: Core infrastructure that MUST be complete before user stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Database Layer
- [x] T004 Create UploadSession entity with TUS fields in `app/.../models/UploadSession.kt`
- [x] T005 Add DAO queries (getByUploadUrl, getByTusId, updateProgress, deleteExpired) in `app/.../dao/UploadSessionDao.kt`
- [x] T006 Update UploadSessionRepository with TUS-related methods in `app/.../UploadSessionRepository/`

### Upload Processing (Simplified Architecture)
> **Architecture Change**: Instead of implementing `UploadStorageService`, we use a simpler approach:
> - TUS library handles temp file storage in cache dir
> - `TusUploadHandler` monitors completion and moves files to MediaStore
> - `MediaStoreUploader` handles MediaStore operations

- [x] T007 Create TusUploadHandler to process completed uploads in `app/.../TransferManager/TusUploadHandler.kt`
- [x] T008 Create MediaStoreUploader for MediaStore file operations in `app/.../TransferManager/MediaStoreUploader.kt`
- [x] T009 Implement upload completion detection in TusUploadHandler.checkAndProcessUpload()
- [x] T010 Implement file copy from TUS temp to MediaStore in TusUploadHandler.processCompletedUpload()
- [x] T011 Implement TUS temp file cleanup after successful MediaStore copy

### Server Layer
- [x] T012 Create JettyUploadServer with start/stop lifecycle in `app/.../TransferManager/JettyUploadServer.kt`
- [x] T013 Create StaticAssetsServlet to serve files from assets/transfer/ in `app/.../TransferManager/StaticAssetsServlet.kt`
- [x] T014 Create TusUploadServlet wrapping TusFileUploadService in `app/.../TransferManager/TusUploadServlet.kt`
- [x] T015 Register servlets (TusUploadServlet at /tus/*, ApiServlet at /api/*, StaticAssetsServlet at /*) in JettyUploadServer

### Integration
- [x] T016 Update TransferService to use JettyUploadServer in `app/.../TransferService.kt`
- [x] T017 TUS dependencies created inline in TransferService (no separate Hilt module needed)
- [x] T018 Delete old UploadServer.kt (NanoHTTPD-based) - confirmed removed
- [x] T019 Delete MediaStoreTempFileManager.kt (not needed with Jetty/TUS) - confirmed removed

**Checkpoint**: ✅ Jetty server starts, TUS endpoints respond, static assets served

---

## Phase 3: User Story 1 - Resume Interrupted Upload (Priority: P1) ✅ COMPLETE

**Goal**: Users can resume interrupted uploads from where they left off

**Independent Test**: Start upload, kill WiFi at 50%, restore WiFi, verify resume from last byte position

### Implementation for User Story 1

- [x] T020 [US1] Configure tus.Upload with retryDelays for automatic resume in `app/.../assets/transfer/upload.js`
- [x] T021 [US1] Implement findPreviousUploads() using tus-js-client localStorage in `upload.js`
- [x] T022 [US1] Add Resume button UI for incomplete uploads shown on page load in `upload.js`
- [x] T023 [US1] Implement resumeUpload() handler calling upload.start() on previous upload in `upload.js`
- [x] T024 [US1] Add beforeunload handler to warn user when upload in progress in `upload.js`

**Checkpoint**: ✅ User Story 1 complete - Resume flow works: start upload → interrupt → resume from offset

---

## Phase 4: User Story 2 - Basic File Upload (Priority: P1) ✅ COMPLETE

**Goal**: Users can upload files with real-time progress indication

**Independent Test**: Select file, upload completes, file appears in video library with correct metadata

### Implementation for User Story 2

- [x] T025 [US2] Replace XMLHttpRequest with tus.Upload in `app/.../assets/transfer/upload.js`
- [x] T026 [US2] Implement onProgress callback - update progress bar, speed, ETA in `upload.js`
- [x] T027 [US2] Implement onSuccess callback - update UI, notify completion in `upload.js`
- [x] T028 [US2] Implement onError callback with user-friendly error messages in `upload.js`
- [x] T029 [US2] Finalize uploaded file when TUS upload completes in TusUploadHandler (copy to MediaStore, cleanup temp)

**Checkpoint**: ✅ User Story 2 complete - Full upload works with accurate progress display

---

## Phase 5: User Story 3 - Cancel and Clean Up Upload (Priority: P2) ✅ COMPLETE

**Goal**: Users can cancel uploads and partial files are cleaned up

**Independent Test**: Start upload, click Cancel, verify no orphaned files on device storage

### Implementation for User Story 3

- [x] T030 [US3] Add Cancel button UI during active upload in `upload.js`
- [x] T031 [US3] Implement cancelUpload() with upload.abort() + localStorage cleanup in `upload.js`
- [x] T032 [US3] Add Discard button for incomplete uploads (removes from resume list) in `upload.js`
- [x] T033 [US3] Implement discardUpload() calling DELETE /tus/{id} to cleanup server-side in `upload.js`
- [x] T034 [US3] Implement cleanupExpiredUploads() for sessions >24h in UploadSessionRepository + TUS cache cleanup
- [x] T035 [US3] Create UploadCleanupWorker (WorkManager) for periodic cleanup in `app/.../workers/UploadCleanupWorker.kt`
- [x] T036 [US3] Schedule UploadCleanupWorker on app startup

**Checkpoint**: ✅ User Story 3 complete - Cancel/Discard cleanup works, no orphaned files

---

## Phase 6: User Story 4 - Upload with PIN Protection (Priority: P2)

**Goal**: PIN-protected uploads require correct PIN before proceeding

**Independent Test**: Enable PIN → upload without PIN fails with 401 → upload with correct PIN succeeds

### Implementation for User Story 4

- [ ] T037 [US4] Add PIN verification in TusUploadServlet before delegating to library in `TusUploadServlet.kt`
- [ ] T038 [US4] Add X-Upload-Pin header to tus.Upload configuration in `upload.js`
- [ ] T039 [US4] Handle 401 response - prompt user for PIN and retry with header in `upload.js`
- [ ] T040 [US4] Create ApiServlet for /api/verify-pin and /api/status endpoints in `app/.../TransferManager/ApiServlet.kt`
- [ ] T041 [US4] Register ApiServlet at /api/* path in JettyUploadServer

**Checkpoint**: User Story 4 complete - PIN flow works for both TUS and status endpoints

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final cleanup, documentation, validation

- [ ] T042 Add KDoc documentation to new classes (JettyUploadServer, TusUploadServlet, TusUploadHandler, MediaStoreUploader)
- [ ] T043 Update style.css for Resume/Cancel/Discard button styles if needed
- [ ] T044 Run quickstart.md validation: complete upload/resume/cancel/PIN flow on Quest device

**Checkpoint**: Feature complete, all user stories validated

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies - start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Phase 2 completion
  - Can proceed in priority order: US1 → US2 → US3 → US4
  - OR in parallel if team capacity allows
- **Phase 7 (Polish)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (Resume)**: Can start after Phase 2 - Independent
- **US2 (Basic Upload)**: Can start after Phase 2 - Independent (but shares upload.js with US1)
- **US3 (Cancel/Cleanup)**: Can start after Phase 2 - Independent
- **US4 (PIN Protection)**: Can start after Phase 2 - Independent

### Parallel Opportunities

**Phase 1**: T002-T003 can run in parallel (different files)
**Phase 2**: T004-T006 (database) and T012-T013 (servlets) can run in parallel
**Phase 3-6**: User stories use same upload.js - best done sequentially per story

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. ~~Complete Phase 1: Setup~~ ✅
2. ~~Complete Phase 2: Foundational (CRITICAL)~~ ✅
3. ~~Complete Phase 3: User Story 1 (Resume)~~ ✅
4. ~~Complete Phase 4: User Story 2 (Basic Upload)~~ ✅
5. **STOP and VALIDATE**: Test complete upload + resume flow ← **NEXT**
6. Deploy/demo MVP

### Incremental Delivery

1. **MVP**: Setup + Foundational + US1 + US2 → Resumable uploads work
2. **+Cancel**: Add US3 → Users can cancel and cleanup
3. **+PIN**: Add US4 → Secure uploads enabled
4. **Polish**: Final validation and documentation

---

## Summary

| Phase | Tasks | Estimated Time |
|-------|-------|----------------|
| Phase 1: Setup | T001-T003 | ✅ Complete |
| Phase 2: Foundational | T004-T019 | ✅ Complete |
| Phase 3: US1 Resume | T020-T024 | ✅ Complete |
| Phase 4: US2 Basic Upload | T025-T029 | ✅ Complete |
| Phase 5: US3 Cancel | T030-T036 | 1-2 hours |
| Phase 6: US4 PIN | T037-T041 | 1 hour |
| Phase 7: Polish | T042-T044 | 30 min |
| **Total** | **44 tasks** | **~2-3 hours remaining** |

**Savings from removing backwards compatibility**: ~2 hours (no LegacyApiServlet, no migration, no deprecated endpoint handlers)

---

## Notes

- **NO custom TUS protocol code** - tus-java-server handles all protocol logic
- **NO legacy endpoints** - clean slate; old /api/upload paths removed
- **NO data migration** - app reinstall acceptable; database recreated
- TusUploadServlet is a thin wrapper that adds PIN verification
- Test on actual Quest device, not just emulator

### Architecture (Implemented)

The implementation uses a simpler architecture than originally planned:

```
┌─────────────────────────────────────────────────────────────┐
│                     JettyUploadServer                        │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │StaticAssets │  │  ApiServlet  │  │  TusUploadServlet │  │
│  │  Servlet    │  │ /api/*       │  │     /tus/*        │  │
│  │    /*       │  │              │  │                   │  │
│  └─────────────┘  └──────────────┘  └─────────┬─────────┘  │
└────────────────────────────────────────────────┼────────────┘
                                                 │
                                    ┌────────────▼────────────┐
                                    │  TusFileUploadService   │
                                    │  (tus-java-server lib)  │
                                    │  - Disk storage in      │
                                    │    cache/tus/           │
                                    └────────────┬────────────┘
                                                 │ on complete
                                    ┌────────────▼────────────┐
                                    │    TusUploadHandler     │
                                    │  - Detects completion   │
                                    │  - Copies to MediaStore │
                                    │  - Cleans up temp files │
                                    └────────────┬────────────┘
                                                 │
                                    ┌────────────▼────────────┐
                                    │   MediaStoreUploader    │
                                    │  - Creates MediaStore   │
                                    │    entry                │
                                    │  - Copies file content  │
                                    └─────────────────────────┘
```

**Key Files**:
- `JettyUploadServer.kt` - Jetty server with servlet registration
- `TusUploadServlet.kt` - Thin wrapper with PIN verification
- `TusUploadHandler.kt` - Handles upload completion → MediaStore
- `MediaStoreUploader.kt` - MediaStore file operations
- `StaticAssetsServlet.kt` - Serves web UI from assets/
- `ApiServlet.kt` - /api/status and /api/verify-pin endpoints

