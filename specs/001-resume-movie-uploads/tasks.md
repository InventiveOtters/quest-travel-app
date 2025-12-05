# Tasks: Resume Incomplete Movie Uploads

**Input**: Design documents from `/specs/001-resume-movie-uploads/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

**Tests**: Automated test tasks are not included in this task list. Acceptance criteria from spec.md (12 scenarios across 3 user stories) will be manually verified during T030 (quickstart.md testing). Consider adding automated tests in a follow-up iteration.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions (per plan.md)

- **Frontend**: `app/src/main/assets/transfer/upload.js`
- **Backend**: `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/UploadServer.kt`
- **Repository**: `app/src/main/java/com/inotter/travelcompanion/data/repositories/UploadSessionRepository/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and verification of existing infrastructure

- [x] T001 Verify existing UploadSession entity has required fields in `app/src/main/java/com/inotter/travelcompanion/data/datasources/videolibrary/models/UploadSession.kt`. **PASS criteria**: Entity contains `filename: String`, `expectedSize: Long`, `bytesReceived: Long`, `status: UploadSessionStatus`, `lastUpdatedAt: Long`. **FAIL action**: Update entity or abort with architecture review.
- [x] T002 Verify existing IncompleteUploadDetector functionality in `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/IncompleteUploadDetector.kt`. **PASS criteria**: Class exposes method returning list of incomplete UploadSession records filtered by IN_PROGRESS status. **FAIL action**: Implement missing detection logic before proceeding.
- [x] T003 Verify GET /api/incomplete-uploads endpoint exists and returns ResumableUpload DTOs in `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/UploadServer.kt`. **PASS criteria**: HTTP GET returns JSON array with fields matching contracts/upload-api.yaml ResumableUpload schema. **FAIL action**: Implement endpoint per contract before Phase 2.

**Checkpoint**: Existing infrastructure verified - no new entities or schemas needed

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Backend endpoint that MUST be complete before frontend user stories can fully function

**⚠️ CRITICAL**: Delete endpoint needed for US2 delete functionality

- [x] T004 Add DELETE /api/incomplete-uploads/{sessionId} route handler in `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/UploadServer.kt`
- [x] T005 Implement deleteIncompleteUpload logic: validate session exists and is IN_PROGRESS, delete MediaStore entry, delete database record via UploadSessionRepository in `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/UploadServer.kt`
- [x] T006 Add JSON response handling for delete endpoint (success, 404 not found, 500 error) per OpenAPI contract in `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/UploadServer.kt`

**Checkpoint**: DELETE endpoint ready - frontend can now call delete API

---

## Phase 3: User Story 1 - Upload In-Progress Warning (Priority: P1) 🎯 MVP

**Goal**: Prevent accidental data loss by showing browser-native warning when user tries to leave during active upload

**Independent Test**: Start a movie upload → click browser refresh/close → verify "Leave site?" dialog appears

### Implementation for User Story 1

- [x] T007 [US1] Add beforeUnloadHandler function that calls event.preventDefault() and sets event.returnValue = true in `app/src/main/assets/transfer/upload.js`
- [x] T008 [US1] Add hasActiveUploads() helper function to track if any uploads are currently in progress in `app/src/main/assets/transfer/upload.js`
- [x] T009 [US1] Add window.addEventListener("beforeunload", beforeUnloadHandler) when first upload starts in `app/src/main/assets/transfer/upload.js`
- [x] T010 [US1] Add window.removeEventListener("beforeunload", beforeUnloadHandler) when all uploads complete (success or failure) in `app/src/main/assets/transfer/upload.js`
- [x] T011 [US1] Add logging for beforeunload handler activation/deactivation in `app/src/main/assets/transfer/upload.js`

**Checkpoint**: User Story 1 complete - browser warning protects active uploads

---

## Phase 4: User Story 2 - Incomplete Upload Detection and Recovery (Priority: P2)

**Goal**: Inform returning users about incomplete uploads and allow them to resume or delete

**Independent Test**: Create incomplete upload state → reopen page → verify notification shows with resume/delete options

### Implementation for User Story 2

- [x] T012 [US2] Add matchFileToSession(file, incompleteUploads) function that matches by filename + size in `app/src/main/assets/transfer/upload.js`
- [x] T013 [US2] Extend showIncompleteUploadsUI() to display resume and delete buttons for each incomplete upload in `app/src/main/assets/transfer/upload.js`
- [x] T014 [US2] Add deleteIncompleteUpload(sessionId) function that calls DELETE /api/incomplete-uploads/{sessionId} in `app/src/main/assets/transfer/upload.js`
- [x] T015 [US2] Add file mismatch warning dialog when user selects wrong file for resume (show expected vs actual name/size) in `app/src/main/assets/transfer/upload.js`
- [x] T016 [US2] Add "Start New Upload" and "Cancel" options to file mismatch dialog in `app/src/main/assets/transfer/upload.js`
- [x] T017 [US2] Update incomplete upload notification to show progress percentage, filename, and last activity timestamp in `app/src/main/assets/transfer/upload.js`
- [x] T018 [US2] Add error handling for delete API (show toast on 404/500 errors) in `app/src/main/assets/transfer/upload.js`
- [x] T019 [US2] Add visual indicator for expired uploads (older than 7 days) with "Recommend delete" styling in `app/src/main/assets/transfer/upload.js`

**Checkpoint**: User Story 2 complete - users can resume or delete individual incomplete uploads

---

## Phase 5: User Story 3 - Multiple Incomplete Upload Management (Priority: P3)

**Goal**: Allow users with multiple incomplete uploads to manage them efficiently with batch operations

**Independent Test**: Create multiple incomplete upload states → verify batch "Resume All" and "Delete All" buttons work

### Implementation for User Story 3

- [x] T020 [US3] Add "Resume All" button to incomplete uploads UI when multiple uploads exist in `app/src/main/assets/transfer/upload.js`
- [x] T021 [US3] Implement resumeAllUploads() function that queues all incomplete uploads for sequential resumption in `app/src/main/assets/transfer/upload.js`
- [x] T022 [US3] Add "Delete All" button to incomplete uploads UI when multiple uploads exist in `app/src/main/assets/transfer/upload.js`
- [x] T023 [US3] Add confirmation dialog before bulk delete ("Are you sure you want to delete N incomplete uploads?") in `app/src/main/assets/transfer/upload.js`
- [x] T024 [US3] Implement deleteAllIncompleteUploads() function that calls delete API for each session in `app/src/main/assets/transfer/upload.js`
- [x] T025 [US3] Add progress indicator for batch operations (e.g., "Resuming 2 of 5...") in `app/src/main/assets/transfer/upload.js`

**Checkpoint**: User Story 3 complete - batch management for multiple incomplete uploads works

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Edge cases and improvements that affect multiple user stories

- [x] T026 Handle corrupted/unreadable incomplete upload data with appropriate error message and delete option in `app/src/main/assets/transfer/upload.js`
- [x] T027 Handle case when MediaStore entry was cleaned by system (cannot resume) - show "Cannot resume" message in `app/src/main/assets/transfer/upload.js`
- [x] T028 Detect duplicate upload attempt when incomplete upload exists for same file - offer to resume instead in `app/src/main/assets/transfer/upload.js`
- [x] T029 Add logging for all resume/delete operations in `app/src/main/java/com/inotter/travelcompanion/data/managers/TransferManager/UploadServer.kt`
- [x] T030 Run quickstart.md manual testing scenarios to validate all user stories

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - verification only
- **Foundational (Phase 2)**: Depends on Setup - BLOCKS US2 delete functionality
- **User Story 1 (Phase 3)**: Depends on Setup only - NO backend changes needed
- **User Story 2 (Phase 4)**: Depends on Foundational (needs DELETE endpoint)
- **User Story 3 (Phase 5)**: Depends on User Story 2 (reuses resume/delete functions)
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start immediately after Setup - frontend only, independent
- **User Story 2 (P2)**: Requires Foundational phase (DELETE endpoint) - but can proceed in parallel with US1 for non-delete tasks
- **User Story 3 (P3)**: Builds on US2 functions (resume/delete) - should complete after US2

### Within Each User Story

- T007-T011 (US1): T007 → T008 → T009 → T010 → T011 (sequential, same file)
- T012-T019 (US2): T012 first, then T013-T19 can be done in sequence (same file)
- T020-T025 (US3): Sequential (same file, building on each other)

### Parallel Opportunities

- T001, T002, T003 (Setup): Can run in parallel (different files, read-only verification)
- T004, T005, T006 (Foundational): Sequential (same endpoint, same file)
- **US1 and US2 can start in parallel** after Foundational begins (US1 doesn't need DELETE)
- Polish tasks T026-T028 can run in parallel (different concerns, same file - but low risk)

---

## Parallel Example: Setup Phase

```text
# Launch all verification tasks together:
Task T001: "Verify UploadSession entity in models/UploadSession.kt"
Task T002: "Verify IncompleteUploadDetector in TransferManager/"
Task T003: "Verify GET /api/incomplete-uploads in UploadServer.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (verify existing infrastructure)
2. Complete Phase 3: User Story 1 (beforeunload protection)
3. **STOP and VALIDATE**: Test browser warning independently
4. Deploy/demo - users now protected from accidental page closes

### Incremental Delivery

1. Setup → Verify infrastructure ready
2. **User Story 1** → Browser warning works → Deploy (MVP!)
3. Foundational → DELETE endpoint ready
4. **User Story 2** → Resume/delete individual uploads → Deploy
5. **User Story 3** → Batch operations → Deploy
6. Polish → Edge cases handled → Final release

### Single Developer Strategy

Recommended order for maximum value delivery:

1. T001-T003 (Setup) - 15 minutes
2. T007-T011 (US1) - 1 hour → **First testable MVP**
3. T004-T006 (Foundational) - 1 hour
4. T012-T019 (US2) - 2 hours → **Core resume feature complete**
5. T020-T025 (US3) - 1 hour → **Batch operations**
6. T026-T030 (Polish) - 1 hour → **Production ready**

---

## Notes

- All frontend tasks modify the same file (`upload.js`) - cannot truly parallelize
- DELETE endpoint (T004-T006) is the only backend change required
- Existing entities (UploadSession, ResumableUpload) are reused - no data model changes
- 7-day expiration policy leverages existing `isOrphaned()` method
- File matching uses existing `filename + expectedSize` combination
- Browser `beforeunload` requires user "sticky activation" to show dialog

