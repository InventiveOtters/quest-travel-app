# Tasks: TUS Protocol Upload Refactor

**Input**: Design documents from `/specs/001-tus-upload-refactor/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/ ✓

**Tests**: Not explicitly requested - test tasks omitted (manual testing via quickstart.md)

**Organization**: Tasks grouped by user story to enable independent implementation and testing

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Paths are relative to repository root

---

## Phase 1: Setup

**Purpose**: Download dependencies and prepare development environment

- [ ] T001 Download tus-js-client library to `app/src/main/assets/transfer/tus.min.js`
- [ ] T002 [P] Update `app/src/main/assets/transfer/index.html` to include tus.min.js script tag

**Checkpoint**: Client library ready for integration

---

## Phase 2: Foundational (Data Layer)

**Purpose**: Core data model changes that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T003 Simplify UploadSession entity to TUS-focused schema in `app/src/main/java/com/inotter/travelcompanion/data/datasources/videolibrary/models/UploadSession.kt`
- [ ] T004 Add TUS-specific DAO queries in `app/src/main/java/com/inotter/travelcompanion/data/datasources/videolibrary/VideoLibraryDao.kt`
- [ ] T005 Update UploadSessionRepository interface with TUS methods in `app/src/main/java/com/inotter/travelcompanion/data/repositories/UploadSessionRepository/UploadSessionRepository.kt`
- [ ] T006 Implement TUS methods in UploadSessionRepositoryImpl in `app/src/main/java/com/inotter/travelcompanion/data/repositories/UploadSessionRepository/UploadSessionRepositoryImpl.kt`
- [ ] T007 Create TusProtocolHandler class in `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/TusProtocolHandler.kt`

**Checkpoint**: Foundation ready - TUS data layer complete, user story implementation can begin

---

## Phase 3: User Story 2 - Basic File Upload (Priority: P1) 🎯 MVP

**Goal**: Replace current multipart upload with TUS protocol for basic file uploads

**Independent Test**: Select file → Upload completes → File appears in video library

**Why US2 First**: Basic upload must work before resume (US1) can be tested. This is the foundation.

### Server-Side Implementation

- [ ] T008 [US2] Add TUS route detection to serve() in `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/UploadServer.kt`
- [ ] T009 [US2] Implement OPTIONS /tus/ handler for capability discovery in TusProtocolHandler
- [ ] T010 [US2] Implement POST /tus/ handler to create upload session in TusProtocolHandler
- [ ] T011 [US2] Implement PATCH /tus/{id} handler to receive chunks in TusProtocolHandler
- [ ] T012 [US2] Wire TusProtocolHandler into UploadServer via Hilt injection

### Client-Side Implementation

- [ ] T013 [US2] Replace XMLHttpRequest upload with tus.Upload in `app/src/main/assets/transfer/upload.js`
- [ ] T014 [US2] Implement onProgress callback for real-time progress display in upload.js
- [ ] T015 [US2] Implement onSuccess callback to update UI on completion in upload.js
- [ ] T016 [US2] Implement onError callback with user-friendly error messages in upload.js
- [ ] T017 [US2] Update file queue to use TUS uploads sequentially (single upload at a time) in upload.js

**Checkpoint**: Basic TUS upload working end-to-end. Test: upload a file, verify it appears in library.

---

## Phase 4: User Story 1 - Resume Interrupted Upload (Priority: P1)

**Goal**: Enable resuming interrupted uploads from last byte position

**Independent Test**: Start upload → Kill connection → Reconnect → Resume from offset → Completes

### Server-Side Implementation

- [ ] T018 [US1] Implement HEAD /tus/{id} handler to return current offset in TusProtocolHandler
- [ ] T019 [US1] Add offset validation in PATCH handler (409 Conflict if mismatch) in TusProtocolHandler
- [ ] T020 [US1] Store fileFingerprint from Upload-Metadata for resume validation in TusProtocolHandler
- [ ] T021 [US1] Update /api/incomplete-uploads to return TUS session data in UploadServer

### Client-Side Implementation

- [ ] T022 [US1] Configure tus-js-client with retryDelays for automatic retry in upload.js
- [ ] T023 [US1] Add beforeunload handler to warn user before leaving during upload in upload.js
- [ ] T024 [US1] Implement findPreviousUploads() to show incomplete uploads on page load in upload.js
- [ ] T025 [US1] Add "Resume" button UI for incomplete uploads in upload.js
- [ ] T026 [US1] Implement resume flow using tus.Upload with uploadUrl option in upload.js

**Checkpoint**: Resume working. Test: upload 50%, disconnect WiFi, reconnect, verify resumes from 50%.

---

## Phase 5: User Story 3 - Cancel and Clean Up Upload (Priority: P2)

**Goal**: Allow users to cancel uploads and clean up partial files

**Independent Test**: Start upload → Click Cancel → Verify file removed from device storage

### Server-Side Implementation

- [ ] T027 [US3] Implement DELETE /tus/{id} handler in TusProtocolHandler
- [ ] T028 [US3] Add cleanup logic: delete MediaStore entry + Room session in TusProtocolHandler
- [ ] T029 [US3] Create cleanup worker for expired sessions in `app/src/main/java/com/inotter/travelcompanion/workers/UploadCleanupWorker.kt`
- [ ] T030 [US3] Schedule cleanup worker to run daily in TransferService or App initialization

### Client-Side Implementation

- [ ] T031 [US3] Add Cancel button to active upload UI in upload.js
- [ ] T032 [US3] Implement cancel flow using upload.abort() + DELETE request in upload.js
- [ ] T033 [US3] Add "Discard" button for incomplete uploads (alternative to Resume) in upload.js
- [ ] T034 [US3] Update UI to remove cancelled/discarded uploads from list in upload.js

**Checkpoint**: Cancel and cleanup working. Test: start upload, cancel, verify no orphan files on device.

---

## Phase 6: User Story 4 - Upload with PIN Protection (Priority: P2)

**Goal**: Ensure PIN protection works with TUS endpoints

**Independent Test**: Enable PIN → Attempt upload without PIN (fails) → Enter PIN → Upload succeeds

### Server-Side Implementation

- [ ] T035 [US4] Add PIN verification to all TUS handlers (POST, HEAD, PATCH, DELETE) in TusProtocolHandler
- [ ] T036 [US4] Return 401 Unauthorized with clear message when PIN invalid in TusProtocolHandler

### Client-Side Implementation

- [ ] T037 [US4] Add X-Upload-Pin header to tus.Upload configuration in upload.js
- [ ] T038 [US4] Handle 401 response by prompting for PIN (reuse existing PIN flow) in upload.js

**Checkpoint**: PIN protection working with TUS. Test: enable PIN, verify all TUS operations require valid PIN.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Cleanup, documentation, and edge case handling

- [ ] T039 [P] Add KDoc documentation to TusProtocolHandler class
- [ ] T040 [P] Remove deprecated /api/upload and /api/upload-resume handlers from UploadServer
- [ ] T041 [P] Update TransferService callbacks to work with TUS session model
- [ ] T042 Handle storage full error (413) gracefully in TusProtocolHandler and upload.js
- [ ] T043 Handle server restart: validate session exists on HEAD/PATCH in TusProtocolHandler
- [ ] T044 Add logging for TUS operations (upload start, progress milestones, complete, errors)
- [ ] T045 Run quickstart.md validation: test full upload/resume/cancel flow

**Checkpoint**: Feature complete and polished.

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1: Setup ──────────────────────────────┐
                                             │
Phase 2: Foundational ───────────────────────┤
                                             │
         ┌───────────────────────────────────┴───────────────────────────────────┐
         │                                                                       │
Phase 3: US2 Basic Upload (MVP) ─────┬───── Phase 4: US1 Resume ────────────────┤
                                     │                                          │
                                     └───── Phase 5: US3 Cancel ────────────────┤
                                     │                                          │
                                     └───── Phase 6: US4 PIN ───────────────────┤
                                                                                │
Phase 7: Polish ────────────────────────────────────────────────────────────────┘
```

### User Story Dependencies

| Story | Depends On | Can Run In Parallel With |
|-------|------------|-------------------------|
| US2 (Basic Upload) | Foundational only | None (must complete first) |
| US1 (Resume) | US2 complete | US3, US4 |
| US3 (Cancel) | US2 complete | US1, US4 |
| US4 (PIN) | US2 complete | US1, US3 |

### Parallel Opportunities by Phase

**Phase 2 (Foundational)**:
- T003, T004, T005, T006 can be done sequentially (same logical flow)
- T007 (TusProtocolHandler skeleton) can start in parallel with DAO work

**Phase 3 (US2 - Basic Upload)**:
- Server tasks (T008-T012) and Client tasks (T013-T017) can be developed in parallel
- Within server: T009, T010, T011 can be parallelized (different handlers)

**Phase 4-6 (US1, US3, US4)**:
- All three user stories can run in parallel after US2 completes
- Each story's server and client tasks can run in parallel

---

## Implementation Strategy

### MVP First (Recommended)

1. **Phase 1**: Setup (30 min)
2. **Phase 2**: Foundational (2-3 hours)
3. **Phase 3**: US2 Basic Upload (4-6 hours)
4. **STOP & VALIDATE**: Test complete upload flow
5. **Ship MVP** if basic upload working

### Full Implementation

1. Complete MVP above
2. **Phase 4**: US1 Resume (3-4 hours)
3. **VALIDATE**: Test resume flow
4. **Phase 5**: US3 Cancel (2-3 hours)
5. **Phase 6**: US4 PIN (1-2 hours)
6. **Phase 7**: Polish (2-3 hours)
7. **Final validation**: Run through quickstart.md

### Estimated Total: 15-22 hours

---

## Notes

- [P] tasks = different files, no dependencies within that group
- [Story] label maps task to specific user story
- US2 (Basic Upload) must complete before US1/US3/US4 can be meaningfully tested
- All TUS handlers share TusProtocolHandler - coordinate changes
- Client changes are all in upload.js - may have merge conflicts if parallelized
- Test on actual Quest device periodically, not just emulator

