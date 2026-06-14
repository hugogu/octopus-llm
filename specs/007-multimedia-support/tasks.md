---
description: "Task list for Multimedia Support (Images, Video, Voice)"
---

# Tasks: Multimedia Support (Images, Video, Voice)

**Input**: Design documents from `specs/007-multimedia-support/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included only where the constitution requires them — one happy-path integration test per
new endpoint (Constitution: Code Quality Gates) and Playwright visual verification for new user-facing
surfaces (Constitution VIII). No speculative unit tests are generated.

**Organization**: Tasks are grouped by user story (spec.md priorities) for independent implementation
and testing.

## Path Conventions

Web app: `backend/src/main/kotlin/com/octopusllm/...`, `frontend/src/...`. Migrations in
`backend/src/main/resources/db/migration/`. Tests in `backend/src/test/...` and `frontend/src/...`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencies and infra needed before any media code

- [X] T001 [P] Add AWS SDK for Java v2 `s3` dependency (path-style, OSS/MinIO-compatible) to `backend/build.gradle.kts`
- [X] T002 [P] Add MinIO Testcontainer test dependency to `backend/build.gradle.kts` for the S3 storage path
- [X] T003 [P] Extend `docker-compose.yml`: add a media bind-mount for the local backend (e.g. `./data/media:/app/media`) and an optional `minio` dev service (no public port beyond dev) for the S3/OSS path

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The media storage + upload plumbing every user story depends on (upload, storage backend, `media` table, attachment-reference contract). Local storage only here; S3 + admin UI come in US5.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Create Flyway migration `V030__storage_settings.sql` (single-row platform config + size limits per data-model.md) in `backend/src/main/resources/db/migration/`
- [X] T005 Create Flyway migration `V031__media.sql` (`media` table: opaque uuid id, owner, media_type, mime_type, size_bytes, storage_backend, storage_key, public_url, nullable turn_id, created_at; indexes per data-model.md) in `backend/src/main/resources/db/migration/`
- [X] T006 [P] Create `Media` entity + `MediaRepository` in `backend/src/main/kotlin/com/octopusllm/media/Media.kt` and `MediaRepository.kt`
- [X] T007 [P] Create `StorageSettings` entity + `StorageSettingsRepository` (single-row) in `backend/src/main/kotlin/com/octopusllm/admin/StorageSettings.kt` and repository
- [X] T008 Implement `StorageSettingsService` read path with default-row seeding (backend=local, default 1MB/10MB/10MB limits, ceiling 5 files/15MB) in `backend/src/main/kotlin/com/octopusllm/admin/StorageSettingsService.kt` (depends on T007)
- [X] T009 [P] Define `MediaStorage` strategy interface (store(bytes, mime) → StoredMedia(key, publicUrl); delete(key); exists) in `backend/src/main/kotlin/com/octopusllm/media/MediaStorage.kt`
- [X] T010 Implement `LocalMediaStorage` (writes opaque-named files under `MEDIA_LOCAL_DIR`, returns public base URL + key) in `backend/src/main/kotlin/com/octopusllm/media/LocalMediaStorage.kt` (depends on T009)
- [X] T011 Implement `MediaStorageFactory` resolving the active backend from `StorageSettings` (local wired now; S3 added in US5) in `backend/src/main/kotlin/com/octopusllm/media/MediaStorageFactory.kt` (depends on T008, T009, T010)
- [X] T012 Implement `MediaService`: magic-byte content-type detection, per-type size-limit validation, opaque-id generation, store via factory, persist `Media` row (turn_id NULL), and orphan delete-by-owner in `backend/src/main/kotlin/com/octopusllm/media/MediaService.kt` (depends on T006, T011)
- [X] T013 Implement `MediaController` `POST /api/v2/media` (multipart) and `DELETE /api/v2/media/{id}` per contracts/media-upload.md in `backend/src/main/kotlin/com/octopusllm/media/MediaController.kt` (depends on T012)
- [X] T014 [P] Extend `LlmRequest.Attachment` with `mediaType` (image|video|audio) and `url`, keeping inline `data` optional for backward-compat, in `backend/src/main/kotlin/com/octopusllm/llm/LlmRequest.kt`
- [X] T015 [P] Add frontend media API client (upload, delete) in `frontend/src/lib/api/media.ts` and update the `Attachment` type to the media-reference shape in `frontend/src/lib/types/api.ts`
- [X] T016 Integration test (Testcontainers): upload happy path + size-limit rejection + orphan delete, in `backend/src/test/kotlin/com/octopusllm/media/MediaControllerTest.kt` (depends on T013)

**Checkpoint**: Media can be uploaded/stored/deleted and referenced — user stories can begin.

---

## Phase 3: User Story 1 - Attach images/video to a capable model, incapable auto-excluded (Priority: P1) 🎯 MVP

**Goal**: Attach image/video, capable models answer about it, incapable models are auto-excluded with a notice; send blocked when no selected model is capable.

**Independent Test**: Select one image-capable and one text-only model, attach an image, send → capable model answers, text-only excluded with notice; selecting only the text-only model blocks send (quickstart Scenario 1).

### Tests for User Story 1

- [X] T017 [P] [US1] Integration test: submit turn with media → capable model receives attachment, incapable model excluded via `notice`, all-incapable returns 409, in `backend/src/test/kotlin/com/octopusllm/chat/ChatMediaSubmitTest.kt`

### Implementation for User Story 1

- [X] T018 [US1] In `ChatService` (`backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`): build `LlmRequest.attachments` from submitted media refs, persist `chat_turns.attachments` in reference shape, and bind each `media.turn_id` to the saved turn (depends on T012, T014)
- [X] T019 [US1] In `ChatService`: per-selected-model capability gating from merged `CapabilityMatrix.input_modalities` — exclude incapable models from dispatch and emit a terminal per-model `notice`; ensure media is included only on the current turn (prior history stays text-only)
- [X] T020 [US1] In `ChatControllerV2` (`backend/src/main/kotlin/com/octopusllm/chat/ChatControllerV2.kt`): change `SubmitTurnRequestV2.attachments` to media references, validate ownership + orphaned state of each `media_id`, re-validate the per-prompt ceiling (≤5 files / ≤15 MB), and return `409 no_capable_model` when no selected model can accept an attached type (depends on T018)
- [X] T021 [P] [US1] Extend `AnthropicAdapter` (`backend/src/main/kotlin/com/octopusllm/llm/adapter/AnthropicAdapter.kt`): fetch media from `url` when present and emit image + video content blocks
- [X] T022 [P] [US1] Extend `OpenAiCompatAdapter` (`.../adapter/OpenAiCompatAdapter.kt`): fetch from `url`, add video content parts alongside existing `image_url`
- [X] T023 [P] [US1] Extend `MiniMaxAdapter` (`.../adapter/MiniMaxAdapter.kt`): add image + video content per MiniMax multimodal schema (currently none)
- [X] T024 [US1] In `frontend/src/app/(app)/chat/page.tsx`: upload attachments via `lib/api/media.ts` before submit, send media refs, compute per-type (image/video) exclusion from `input_modalities` and show a pre-send notice, and block send when no selected model is capable (depends on T015)
- [X] T025 [US1] In `frontend/src/components/chat/ChatInput.tsx`: replace inline base64 encoding with upload-and-attach-by-reference flow, passing media refs to submit (depends on T015)

**Checkpoint**: Multimodal send with capability gating works end-to-end (MVP).

---

## Phase 4: User Story 2 - Manage attachments before sending (Priority: P2)

**Goal**: Multi-file preview tray with delete, drag-reorder, and enforced size + per-prompt limits at attach time.

**Independent Test**: Attach several images and a video → previews shown; reject an oversize file and a 6th file/over-15MB with messages; reorder and remove without sending (quickstart Scenario 2).

### Implementation for User Story 2

- [X] T026 [P] [US2] Create `MediaItem` component (renders image thumb / video poster / audio playback for one item) in `frontend/src/components/chat/MediaItem.tsx`
- [X] T027 [US2] Create `AttachmentTray` component (previews via `MediaItem`, remove control calling `DELETE /api/v2/media/{id}`, drag-reorder updating `order`) in `frontend/src/components/chat/AttachmentTray.tsx` (depends on T026)
- [X] T028 [US2] Enforce per-type size limits and the per-prompt ceiling (≤5 files / ≤15 MB) at attach time with clear limit+size messaging, before upload, in `AttachmentTray`/`ChatInput` (depends on T027)
- [X] T029 [US2] Integrate `AttachmentTray` into `frontend/src/components/chat/ChatInput.tsx`, preserving drag order into the submitted refs (depends on T027, T025)

**Checkpoint**: Attachment authoring (preview/delete/reorder/limits) complete.

---

## Phase 5: User Story 3 - Media in history & share, video plays (Priority: P2)

**Goal**: Attached media renders inline (image) and plays (video/audio) in conversation history and the public share view, in order, with parity; revoking a share revokes its media.

**Independent Test**: Send a turn with image+video, reload history → both render, video plays; open share link logged-out → same; revoke → media inaccessible (quickstart Scenario 3).

### Implementation for User Story 3

- [ ] T030 [US3] Expose `attachments` (media refs, ordered) on `TurnDtoV2` and map them in the session GET in `backend/src/main/kotlin/com/octopusllm/chat/ChatControllerV2.kt`
- [ ] T031 [US3] Add `attachments` (no owner identity) to the shared-conversation DTO and ensure share revocation cascades media inaccessibility, in `backend/src/main/kotlin/com/octopusllm/share/ShareService.kt`
- [ ] T032 [P] [US3] Render turn media via `MediaItem` in conversation history (user-prompt rendering) in `frontend/src/components/chat/markdownComponents.tsx` (or the turn/prompt render path) (depends on T026, T030)
- [ ] T033 [P] [US3] Render + play turn media via `MediaItem` in `frontend/src/components/share/SharedConversation.tsx` (depends on T026, T031)
- [ ] T034 [US3] Playwright visual verification: image renders + video/audio play in both in-app history and the public share view (Constitution VIII) in `frontend/src/components/share/SharedConversation.test.tsx` (or a Playwright spec) (depends on T032, T033)

**Checkpoint**: Persistence + playback parity across history and share.

---

## Phase 6: User Story 4 - Voice input sent as audio (Priority: P3)

**Goal**: Record voice in the chat window, add it to the tray as an audio item, and send it as an audio file under the same capability gating.

**Independent Test**: Record a clip → audio item in tray with playback; send to an audio-capable model → it responds; audio-incapable model excluded with notice (quickstart Scenario 4).

### Implementation for User Story 4

- [ ] T035 [US4] Add `"audio"` as a recognized input modality in capability handling (known keys / `input_modalities`) in `backend/src/main/kotlin/com/octopusllm/model/ProtocolDefinition.kt` and `backend/src/main/kotlin/com/octopusllm/llm/CapabilityMatrix.kt`
- [ ] T036 [P] [US4] Add audio content handling to `AnthropicAdapter`, `OpenAiCompatAdapter`, and `MiniMaxAdapter` (input-audio parts per each protocol) in `backend/src/main/kotlin/com/octopusllm/llm/adapter/` (depends on T021, T022, T023)
- [ ] T037 [P] [US4] Create `VoiceRecorder` component using `MediaRecorder`/`getUserMedia` to capture an audio blob and add it as an audio attachment in `frontend/src/components/chat/VoiceRecorder.tsx`
- [ ] T038 [US4] Integrate `VoiceRecorder` into `ChatInput` and include `audio` in the frontend per-type capability gating in `frontend/src/components/chat/ChatInput.tsx` and `frontend/src/app/(app)/chat/page.tsx` (depends on T037, T024)

**Checkpoint**: Voice input works end-to-end under capability gating.

---

## Phase 7: User Story 5 - Admin storage config & size limits (Priority: P3)

**Goal**: Admin selects local vs S3/OSS backend + credentials and per-type size limits, validated before apply; local media is served directly without a per-file backend round-trip.

**Independent Test**: Switch backend to MinIO with creds (validated, bad creds rejected), upload → object in bucket via opaque URL; switch to local → media loads directly from public URL (no `/api/v2` per-file request); change a size limit → enforced (quickstart Scenario 5).

### Tests for User Story 5

- [ ] T039 [P] [US5] Integration test (MinIO Testcontainer): `PUT /api/v2/admin/storage-settings` validates connectivity before persist (good saves, bad creds rejected with previous config retained) and secret is never returned, in `backend/src/test/kotlin/com/octopusllm/admin/StorageSettingsControllerTest.kt`

### Implementation for User Story 5

- [ ] T040 [US5] Implement `S3MediaStorage` (AWS SDK v2, path-style endpoint, opaque object key, public/CDN base URL) in `backend/src/main/kotlin/com/octopusllm/media/S3MediaStorage.kt` and wire it into `MediaStorageFactory` (depends on T011)
- [ ] T041 [US5] Extend `StorageSettingsService` with an update path: field-coherence validation, S3 connectivity check (HEAD bucket / probe object) before persist, secret encryption at rest, and admin audit-log entry, in `backend/src/main/kotlin/com/octopusllm/admin/StorageSettingsService.kt` (depends on T008, T040)
- [ ] T042 [US5] Implement `StorageSettingsController` `GET`/`PUT /api/v2/admin/storage-settings` (admin-only; secret returned only as `s3_secret_key_set`) per contracts/storage-settings.md in `backend/src/main/kotlin/com/octopusllm/admin/StorageSettingsController.kt` (depends on T041)
- [ ] T043 [US5] Surface configured size limits + per-prompt ceiling to the client (chat config/capabilities payload) and enforce updated limits in `MediaService` + the tray, replacing hardcoded defaults (depends on T012, T028, T042)
- [ ] T044 [P] [US5] Add admin storage-settings API client in `frontend/src/lib/api/storageSettings.ts`
- [ ] T045 [US5] Create admin storage settings page (backend selector, conditional S3 fields with write-only secret, size limits, Test & Save with loading/disabled/error states) using `AdminShell` + design system in `frontend/src/app/(app)/admin/storage/page.tsx`, and link it in admin navigation (depends on T044)

**Checkpoint**: Operators can configure storage backend + limits; local direct-serve verified.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T046 [P] Implement the idempotent orphaned-media cleanup sweep (delete `media` rows + stored objects with `turn_id IS NULL` older than the TTL) as a scheduled job in `backend/src/main/kotlin/com/octopusllm/media/MediaService.kt` (FR-023)
- [ ] T047 Ensure session/turn deletion cascades media deletion (rows + stored objects) consistent with share revocation (FR-024) across `ChatService`/`MediaService`
- [ ] T048 [P] Structured logging for uploads and per-model exclusions (media_type, size_bytes, backend, excluded model + reason) per Constitution V
- [ ] T049 Run code-quality gates: `cd backend && ./gradlew build` and `cd frontend && npx tsc --noEmit` — fix all errors
- [ ] T050 Execute `specs/007-multimedia-support/quickstart.md` scenarios 1–6 end-to-end and confirm expected outcomes

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories.
- **User Stories (Phase 3–7)**: All depend on Foundational. US1 is the MVP. US2/US3 build on US1's send path but are independently testable; US4 extends adapters/tray; US5 adds S3 + admin config.
- **Polish (Phase 8)**: Depends on the desired stories being complete.

### User Story Dependencies

- **US1 (P1)**: After Foundational. No dependency on other stories.
- **US2 (P2)**: After Foundational; integrates with US1's `ChatInput`/refs but tray is testable on its own.
- **US3 (P2)**: After Foundational; needs US1's persisted media refs to display, independently testable via history/share.
- **US4 (P3)**: After Foundational; reuses US1 adapters + US2 tray.
- **US5 (P3)**: After Foundational; independent of US1–US4 (admin surface), shares the `MediaStorage`/`StorageSettings` foundation.

### Within Each User Story

- Migrations/entities before services; services before controllers; backend contract before frontend wiring.
- Adapter tasks (`[P]`) touch different files and can run in parallel.

### Parallel Opportunities

- Setup: T001, T002, T003 all `[P]`.
- Foundational: T006/T007 `[P]`; T009 `[P]`; T014/T015 `[P]` (different files).
- US1 adapters T021/T022/T023 `[P]` (different files).
- US3 frontend T032/T033 `[P]`; US4 T036/T037 `[P]`.
- Across teams after Foundational: US5 (admin) can proceed fully in parallel with US1–US4.

---

## Parallel Example: User Story 1

```bash
# After T018–T020 (ChatService/controller), the three adapter extensions run in parallel:
Task: "Extend AnthropicAdapter: fetch from url + image/video blocks"
Task: "Extend OpenAiCompatAdapter: fetch from url + video content parts"
Task: "Extend MiniMaxAdapter: image + video content"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 Setup → 2. Phase 2 Foundational (CRITICAL) → 3. Phase 3 US1 → **STOP & VALIDATE** quickstart Scenario 1 → demo.

### Incremental Delivery

Foundation → US1 (MVP: multimodal send + gating) → US2 (tray UX) → US3 (history/share playback) → US4 (voice) → US5 (admin storage) → Polish. Each story adds value without breaking prior ones.

### Parallel Team Strategy

After Foundational: Dev A → US1; Dev B → US5 (independent admin surface); then US2/US3/US4 layer onto US1 as it lands.

---

## Notes

- `[P]` = different files, no incomplete dependencies.
- `[Story]` label maps each task to its user story for traceability.
- `chat_turns`/`provider_responses` stay INSERT-once; attachment jsonb shape is forward-only (Constitution IV).
- Media URLs must stay opaque/non-enumerable; secrets never returned/logged (Constitution VI).
- Commit after each task or logical group; verify gates (T049) before marking work done.
