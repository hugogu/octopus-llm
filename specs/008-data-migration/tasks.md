# Tasks: Data Migration, Quest Sharing & Lifecycle

**Input**: Design documents from `specs/008-data-migration/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Backend happy-path integration coverage is required for every new or changed endpoint.
Security, rollback, idempotency, and proxy-streaming failure paths are explicit tasks because they
are part of the feature contract, not optional polish.

**Organization**: Grouped by user story (US1–US6). `[P]` means different files with no incomplete
dependency.

## Phase 1: Setup and prerequisite refactor

- [X] T001 [P] Create backend package `com.octopusllm.migration` with `MigrationController.kt`, `MigrationExportService.kt`, `MigrationImportService.kt`, `MigrationBundle.kt`, `MigrationArtifactCrypto.kt`, `MigrationOperation.kt`, and `MigrationOperationRepository.kt`
- [X] T002 [P] Create typed frontend client skeleton `frontend/src/lib/api/migration.ts` from [contracts/admin-migration.md](./contracts/admin-migration.md)
- [X] T003 Refactor `frontend/src/app/api/[...path]/route.ts` to stream non-GET request bodies upstream instead of calling `request.arrayBuffer()`; preserve the full `/api/...` path and manual redirect behavior, and commit this proxy refactor separately before feature UI work
- [X] T004 [P] Extend `frontend/src/app/api/[...path]/route.test.ts` with a real binary/multipart streaming request that asserts the exact upstream path, query, headers, and bytes

## Phase 2: Foundational schema and shared infrastructure

**Checkpoint**: complete before user-story implementation.

- [X] T005 [P] Add `V032__session_share_scope.sql`: `scope VARCHAR(20) NOT NULL DEFAULT 'authenticated'`, backfill existing rows to `public`, and add the allowed-value check
- [X] T006 [P] Add `V033__dialog_redactions.sql`: append-only table, nullable `redacted_by ... ON DELETE SET NULL`, scope check, FK/indexes, and partial unique indexes
- [X] T007 [P] Add `V034__quest_import_origin.sql`: nullable `imported_from_label` and `imported_at` on `chat_sessions`
- [X] T008 [P] Add `V035__migration_operations.sql`: non-secret operation/result audit, partial idempotency uniqueness, and `migration_staged_media` crash-cleanup ledger from [data-model.md](./data-model.md)
- [X] T009 [P] Add share scope to `backend/src/main/kotlin/com/octopusllm/share/SessionShare.kt` using an enum mapped to varchar; add migration/entity tests
- [X] T010 [P] Add `DialogRedaction.kt` and `DialogRedactionRepository.kt`; validate response belongs to turn/session in service code, not only by caller-supplied ids
- [X] T011 [P] Add import-origin fields to `backend/src/main/kotlin/com/octopusllm/chat/ChatSession.kt`
- [X] T012 Add redaction exclusion to owned Quest reads in `ChatService.kt` and shared reads in `ShareService.kt`; centralize the filter so export/share/import cannot drift
- [X] T013 [P] Implement `MigrationOperation` claim/complete/fail operations with actor + operation + key-hash uniqueness, source-digest conflict detection, and no secret-bearing metadata
- [X] T014 [P] Add `MigrationStagedMedia` repository plus an idempotent cleanup service/job: record deterministic backend/key before each object write, delete tracked objects for failed/stale operations, and run safely on every instance without distributed locks

## Phase 3: User Story 1 — Admin Quest/Connection migration (P1, MVP)

**Goal**: Stream a passphrase-encrypted artifact; import it atomically under the calling admin with
portable Connections, full history/media, endpoint validation, and idempotent retries.

### Tests

- [X] T015 [P] [US1] Unit tests for `MigrationArtifactCrypto`: round-trip, wrong passphrase/tamper rejection, random ciphertext, no plaintext provider key/Quest text in artifact entries, and passphrase/key exclusion from exception/log metadata
- [X] T016 [P] [US1] Unit tests for safe ZIP parsing: path traversal, absolute paths, duplicate entries, excessive count, per-entry/expanded-size limits, and checksum mismatch
- [X] T017 [P] [US1] Integration test `POST /api/v2/admin/migration/export`: admin-only, acknowledgement/passphrase validation (request passphrase honored; configured `MIGRATION_ARTIFACT_PASSPHRASE` used when request omits it; `400` when neither present), encrypted streamed artifact, redacted Dialog exclusion, and non-secret operation record
- [X] T018 [P] [US1] Integration test `POST /api/v2/admin/migration/import`: full round-trip, all Quests/Connections owned by admin, configured-model UUID/reference remapping, keys usable after target-key re-encryption, and media restored
- [X] T019 [P] [US1] Import rejection integration tests: wrong passphrase/tamper, malformed/unsafe/wrong-version bundle, missing reference, checksum mismatch, and `ConnectionEndpointPolicy` rejection all create zero business rows
- [X] T020 [P] [US1] Atomicity/compensation integration test: inject DB failure after media staging, verify no Quest/Connection/configured-model/media rows and staged blobs removed; simulate interrupted staging and verify sweep cleanup
- [X] T021 [P] [US1] Idempotency integration test: same actor/key/source returns original result with no duplicates; same key/different source returns `409 idempotency_conflict`; new key creates an intentional independent copy

### Implementation

- [X] T022 [P] [US1] Define bundle/envelope DTOs with artifact-local ids for every referenced Connection, configured model, Quest, turn, response, and media object; include size/checksum metadata and `formatVersion=1`
- [X] T023 [P] [US1] Implement `MigrationArtifactCrypto.kt` with existing Spring Security Crypto authenticated password-based encryption, one random salt per artifact, independently encrypted structured/media entries, and memory-only passphrase/key handling
- [X] T024 [US1] Implement `MigrationExportService.kt`: page through repositories, decrypt Connection keys only in memory, exclude redacted Dialogs and their media, read included media one object at a time, and stream `envelope.json` plus encrypted entries without buffering the complete artifact
- [X] T025 [US1] Implement `MigrationImportService.kt` preflight: stream `FilePart` to bounded temp storage; enforce ZIP limits; authenticate/decrypt; validate schema/version/checksums/references; validate imported endpoints through `ConnectionEndpointPolicy`; compute source digest
- [X] T026 [US1] Implement import staging and commit: allocate new ids/maps (including non-selectable snapshot UUIDs for unresolved historical model refs), stage media under new opaque ids, re-encrypt provider keys with `ApiKeyEncryptionService`, insert the complete artifact in one DB transaction, compensate staged blobs on failure, and complete the idempotency operation
- [X] T027 [US1] Implement WebFlux `MigrationController.kt`: `POST /api/v2/admin/migration/export` as streamed `Flux<DataBuffer>` and multipart `POST /import`; require `ROLE_ADMIN`; never bind/log passphrase through generic object logging. Resolve the artifact passphrase from the request, falling back to the optional `MIGRATION_ARTIFACT_PASSPHRASE` config property (memory-only); reject with `400 passphrase_required` when neither is present and `400 passphrase_too_short` when a supplied passphrase is < 16 chars
- [X] T028 [P] [US1] Implement `frontend/src/lib/api/migration.ts`: same-origin export/import, `Idempotency-Key` generation/reuse for import retries, blob download, and structured error handling
- [X] T029 [US1] Build `frontend/src/components/admin/MigrationPage.tsx`: styled warning, passphrase + confirmation (optional when the deployment configures `MIGRATION_ARTIFACT_PASSPHRASE`, with helper text), import upload/passphrase, disabled/loading state, result counts, and inline errors with no native dialogs
- [X] T030 [US1] Add `frontend/src/app/(app)/admin/migration/page.tsx` and add a Migration tab to the existing `frontend/src/components/admin/AdminShell.tsx`
- [X] T031 [P] [US1] Vitest Migration page: mismatch/weak passphrase blocked client-side, acknowledgement required, busy state, successful download/import counts, and secrets never rendered after submission

**Checkpoint**: Scenario A passes through the published frontend origin.

## Phase 4: User Story 2 — Import a shared Quest to continue (P1)

### Tests

- [X] T032 [P] [US2] Integration test `POST /api/v2/shared/{token}/import`: anonymous 401; authorized import creates fresh Quest/turn/response ids owned by caller; redactions skipped
- [X] T033 [P] [US2] Media independence/rollback integration test: imported attachments use new media rows/storage objects owned by importer and still render after source Quest deletion; injected clone failure leaves no Quest/media rows and tracked blobs are cleaned
- [X] T034 [P] [US2] Shared-import idempotency integration test: replay returns original Quest/media; key conflict returns 409; new key creates a deliberate independent copy

### Implementation

- [X] T035 [US2] Add `importFromShare(token, caller, idempotencyKey)` to `ChatService.kt`: authorize through active share scope, deep-copy visible immutable snapshots, clone media through the shared staging/compensation path, set generic import-origin metadata, and commit through `MigrationOperation`
- [X] T036 [US2] Add authenticated `POST /import` to `SharedSessionController.kt`; require `Idempotency-Key` and return 201 first-use / 202 in-progress / 200 completed replay
- [X] T037 [P] [US2] Add `importSharedSession(token, idempotencyKey)` to `frontend/src/lib/api/shares.ts`
- [X] T038 [US2] Update `frontend/src/components/share/SharedConversation.tsx`: visible “Import to continue” explanation; preserve token + idempotency key through sign-in/register return; navigate to the imported Quest
- [X] T039 [P] [US2] Vitest share import: anonymous resume, successful navigation, retry key reuse, and auth-gated content non-rendering

## Phase 5: User Story 3 — Combined New/Import control (P2)

- [X] T040 [US3] Convert `frontend/src/components/chat/SessionSidebar.tsx` New action into an accessible split/combined button (primary New Quest, secondary Import)
- [X] T041 [P] [US3] Create `frontend/src/components/chat/QuestImportDialog.tsx`: styled dialog accepting a same-deployment share link/token, stable idempotency key per submission, inline errors, and success navigation
- [X] T042 [US3] Wire the dialog to the sidebar secondary action
- [X] T043 [P] [US3] Update `SessionSidebar.test.tsx` for keyboard/mouse primary and secondary actions; add `QuestImportDialog` tests

## Phase 6: User Story 4 — Share audience scope (P2)

### Tests

- [X] T044 [P] [US4] Integration test changed `POST /api/v2/chat/sessions/{id}/shares`: default authenticated, explicit public, idempotent active-share behavior, owner-only
- [X] T045 [P] [US4] Integration test `PATCH .../shares/{token}`: scope change is idempotent and owner-only
- [X] T046 [P] [US4] Integration test all `/api/v2/shared/{token}/...` audience checks: authenticated scope returns non-disclosing 401 to anonymous GET/anonymous-like calls; public remains anonymous-readable/reactable; revoked/unknown remains 404

### Implementation

- [X] T047 [US4] Extend `ShareService.kt` create/change/read DTOs and central scope enforcement for reads, reactions, and import; do not resolve/render/probe shared content before the auth check
- [X] T048 [US4] Extend `ShareControllerV2.kt` create body and add `PATCH /{token}`; keep collection/list response bounded by existing maximum page size 100
- [X] T049 [US4] Update `SharedSessionController.kt`/`SharedSessionDto` with scope/canImport only on successful authorized reads
- [X] T050 [P] [US4] Update `frontend/src/lib/api/shares.ts` create/patch types and methods
- [X] T051 [US4] Update the existing `frontend/src/components/chat/ShareConversationButton.tsx` with styled scope selection/change/revoke confirmation
- [X] T052 [US4] Update `frontend/src/components/share/SharedConversation.tsx` auth gate; render no title, turns, owner metadata, or media before authorization
- [X] T053 [P] [US4] Update existing share component tests for both scopes, revoke confirmation, and zero sensitive pre-auth rendering

## Phase 7: User Story 5 — Delete an individual Dialog (P2)

### Tests and implementation

- [X] T054 [P] [US5] Integration test idempotent `DELETE /api/v2/chat/sessions/{id}/turns/{turnId}`: owner/admin only, whole turn excluded from owned/shared/export reads, analytics rows unchanged
- [X] T055 [P] [US5] Integration test idempotent `DELETE .../responses/{responseId}`: response belongs to supplied turn/session, sibling responses remain, analytics rows unchanged
- [X] T056 [US5] Add `redactTurn`/`redactResponse` to `ChatService.kt` and endpoints to `ChatControllerV2.kt`; insert append-only markers and return 204 for first/repeated delete
- [X] T057 [P] [US5] Add `deleteTurn`/`deleteResponse` to `frontend/src/lib/api/chatV2.ts`
- [X] T058 [US5] Add delete affordances to existing `MessageThread.tsx`, `ResponseGroup.tsx`, and `ModelResponsePanel.tsx`, always through `confirmDialog`
- [X] T059 [P] [US5] Update existing `MessageThread.test.tsx`, `ResponseGroup.test.tsx`, and `ModelResponsePanel.test.tsx` for confirm/cancel/error/sibling behavior
- [X] T060 [P] [US5] Guard all `frontend/src` destructive actions: no runtime `window.alert/confirm/prompt`; every delete/revoke action is covered by styled-confirmation tests (FR-032/SC-004)

## Phase 8: User Story 6 — Reframe Chat as Quest (P2)

- [ ] T061 [US6] Move `frontend/src/app/(app)/chat/` to `frontend/src/app/(app)/quests/` and add Next redirects preserving `/chat` and `/chat/{id}` bookmarks/query strings
- [ ] T062 [P] [US6] Replace user-facing Chat/Conversation copy with Quest across app components, `frontend/src/lib/utils/exportConversation.ts`, tests, loading states, navigation, shares, and admin shell; keep internal API/DB/package names unchanged
- [ ] T063 [P] [US6] Replace conversational icons with task/comparison iconography using existing `lucide-react`
- [ ] T064 [US6] Reframe landing/empty-state copy as LLM comparison/testing, not platform-native conversation
- [ ] T065 [P] [US6] Add route redirect tests and a user-facing-copy guard that excludes internal identifiers/test fixtures but fails on residual rendered Chat/Conversation wording

## Phase 9: Quality gates and end-to-end validation

- [ ] T066 [P] Backend gate: `./gradlew build`; Flyway V032–V035 on fresh and populated PostgreSQL; all integration/unit tests green
- [ ] T067 [P] Frontend gates: `npm run build`, `npm run lint`, and `npm run test:run`
- [ ] T068 Build backend/frontend Docker images and run [quickstart.md](./quickstart.md) A–F from the published frontend origin, including large binary proxy transfer and visual verification at mobile/desktop widths
- [ ] T069 Verify no provider key, passphrase, ciphertext, IV, sensitive custom parameter, or secret fragment appears in logs/errors/operation metadata during successful and failed export/import
- [ ] T070 Verify production endpoint policy/public HTTPS and no-redirect behavior on imported Connections immediately after save and immediately before dispatch
- [ ] T071 Update project recent-change documentation with the final 008 behavior and exact validation commands

## Dependencies and execution order

- Phase 1 proxy refactor is standalone and precedes artifact UI/API verification.
- Phase 2 blocks US1, US2, US4, and US5.
- US3 depends on T037 (shared-import frontend client).
- US6 should land after US2–US5 UI changes to avoid route/copy churn.
- US1, US4, and US5 can proceed in parallel after Phase 2.
- Within each story: failing tests → service/data work → controller → client → UI → end-to-end check.

## Coverage notes

- FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009, FR-015 /
  SC-001, SC-002, SC-007, SC-008: T015–T031, T066–T070.
- FR-010, FR-011, FR-012, FR-013, FR-014 / SC-003: T032–T043, T068.
- FR-020, FR-021, FR-022, FR-023 / SC-005: T044–T053.
- FR-030, FR-031, FR-032, FR-033 / SC-004: T054–T060.
- FR-040, FR-041, FR-042 / SC-006: T061–T065.
