# Tasks: Anonymous Chat and Model Access Management

**Input**: Design documents from `/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/`

**Prerequisites**: [plan.md](/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/plan.md), [spec.md](/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/spec.md), [research.md](/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/research.md), [data-model.md](/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/data-model.md), [contracts/](/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/contracts/), [quickstart.md](/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/quickstart.md)

**Tests**: Included because the repository instructions require unit/integration tests for new code, and the feature specification defines independent acceptance tests and measurable success criteria. Add story-specific tests before implementation tasks in each story phase.

**Organization**: Tasks are grouped by the five user stories in priority order. User Story 1 and User Story 2 are both P1; User Story 3 is P1 but depends on the public chat surface from User Story 1. User Stories 4 and 5 are P2.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish configuration and test helpers without changing runtime behavior.

- [X] T001 [P] Document conservative anonymous runtime defaults and environment bindings in `backend/src/main/resources/application.yml` and `backend/src/main/resources/application-docker.yml`
- [X] T002 [P] Add non-secret anonymous configuration placeholders and local-development notes to `.env.example`
- [X] T003 [P] Add browser `localStorage` and SSE test helpers for anonymous flows in `frontend/src/test/setup.ts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build the schema, shared execution seam, security boundary, and common error/configuration infrastructure required by all user stories.

**⚠️ CRITICAL**: No user story implementation should begin until this phase is complete.

- [X] T004 Add Flyway migration `backend/src/main/resources/db/migration/V041__anonymous_model_access.sql` for `configured_models.is_anonymous_allowed`, anonymous request leases, conversation import identities, bulk-operation snapshots/items, indexes, and audit action/target constraints, then add migration-shape coverage in `backend/src/test/kotlin/com/octopusllm/migration/AnonymousModelAccessMigrationTest.kt`
- [X] T005 [P] Extend `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModel.kt` and `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModelRepository.kt` with the anonymous-policy field, built-in/admin scope predicates, safe public projection, and stable paginated ordering
- [X] T006 [P] Implement import-identity and expiring-concurrency persistence in `backend/src/main/kotlin/com/octopusllm/anonymous/AnonymousConversationImport.kt`, `backend/src/main/kotlin/com/octopusllm/anonymous/AnonymousConversationImportRepository.kt`, `backend/src/main/kotlin/com/octopusllm/anonymous/AnonymousRequestLease.kt`, and `backend/src/main/kotlin/com/octopusllm/anonymous/AnonymousRequestLeaseRepository.kt` with unique keys and atomic claim/release behavior
- [X] T007 [P] Implement bulk-operation entities and repository in `backend/src/main/kotlin/com/octopusllm/admin/AdminModelBulkOperation.kt` and `backend/src/main/kotlin/com/octopusllm/admin/AdminModelBulkOperationRepository.kt`, and extend safe model-related actions/targets in `backend/src/main/kotlin/com/octopusllm/admin/AdminAuditLog.kt`
- [X] T008 [P] Implement anonymous configuration binding, HMAC client-key derivation, fixed-window request limits, expiring stream leases, payload/model-count guards, and execution deadlines in `backend/src/main/kotlin/com/octopusllm/anonymous/AnonymousThrottleService.kt`
- [X] T009 Extract provider-agnostic request normalization, capability filtering, target construction, concurrent dispatch, and normalized stream events into `backend/src/main/kotlin/com/octopusllm/chat/LlmTurnRunner.kt` without moving authenticated persistence out of `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`
- [X] T010 Update exact public route authorization and cache policy in `backend/src/main/kotlin/com/octopusllm/config/SecurityConfig.kt` so only anonymous catalogue/chat endpoints are public while sync, account, chat-session, media, tool, share, and admin routes remain protected
- [X] T011 Extend safe pre-SSE error responses and `Cache-Control: no-store` handling for the new public APIs in `backend/src/main/kotlin/com/octopusllm/config/GlobalExceptionHandler.kt`

**Checkpoint**: Schema, shared execution, public security boundaries, and common limits are ready; user stories can now be implemented in their dependency order.

---

## Phase 3: User Story 1 - Start Chatting Without an Account (Priority: P1) 🎯 MVP

**Goal**: Let an unauthenticated visitor discover only approved public models and complete a multi-model text chat through an ephemeral SSE request.

**Independent Test**: In a fresh unauthenticated browser context, open `/chat`, verify the public catalogue contains only enabled built-in anonymous-approved models, submit a text prompt against one or more models, and observe model-specific streamed success/error states without a server-owned session.

### Tests for User Story 1

- [X] T012 [P] [US1] Add backend contract tests for public model filtering, safe DTO redaction, stale/revoked model rejection, text-only validation, anonymous limits, no server persistence, and normalized multi-model SSE in `backend/src/test/kotlin/com/octopusllm/anonymous/AnonymousChatControllerTest.kt`
- [X] T013 [P] [US1] Add frontend API/SSE client tests for paginated public models, event parsing, safe errors, model-specific failures, and revoked selections in `frontend/src/lib/api/anonymousChat.test.ts`

### Implementation for User Story 1

- [X] T014 [US1] Implement the safe built-in model catalogue query and DTO mapping in `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModelService.kt` and `backend/src/main/kotlin/com/octopusllm/anonymous/AnonymousChatService.kt`
- [X] T015 [US1] Implement anonymous turn validation, current allowlist recheck, bounded user/assistant history, throttle/lease lifecycle, metrics marker, and `LlmTurnRunner` integration in `backend/src/main/kotlin/com/octopusllm/anonymous/AnonymousChatService.kt`
- [X] T016 [US1] Expose `GET /api/v2/anonymous/models` and `POST /api/v2/anonymous/chat/turns` with safe JSON/SSE error behavior in `backend/src/main/kotlin/com/octopusllm/anonymous/AnonymousChatController.kt`
- [X] T017 [US1] Implement same-origin public catalogue and anonymous SSE requests, including page traversal up to the 100-item maximum, in `frontend/src/lib/api/anonymousChat.ts`
- [X] T018 [US1] Relocate the chat entry out of the auth-redirecting layout and connect the shared surface to public/authenticated modes in `frontend/src/app/chat/page.tsx`, `frontend/src/app/(app)/chat/page.tsx`, and `frontend/src/components/chat/ChatPage.tsx`
- [X] T019 [US1] Add anonymous empty, revoked-model, rate-limit, and provider-error states while keeping media, tools, account history, and sharing controls unavailable in `frontend/src/components/chat/AnonymousChatNotice.tsx` and `frontend/src/components/chat/ChatPage.tsx`
- [X] T020 [US1] Add unauthenticated public-chat Playwright coverage for model filtering, first prompt streaming, empty state, and direct-request authorization in `frontend/e2e/anonymous-chat.spec.ts`

**Checkpoint**: A visitor can reach `/chat`, use approved text-only models, and receive concurrent streamed responses without creating server history.

---

## Phase 4: User Story 2 - Manage Model Access in Bulk (Priority: P1)

**Goal**: Give administrators a cross-connection model table with search/filter/pagination and preview-frozen bulk allow, revoke, show, hide, and delete operations.

**Independent Test**: Seed at least 100 built-in models across connections, select a filtered result spanning pages, confirm the frozen target count, execute each bulk action, and verify per-item outcomes, audit records, state isolation, and preserved response snapshots.

### Tests for User Story 2

- [ ] T021 [P] [US2] Add backend contract/integration tests for admin scope, cross-connection filters, 100-item page limits, preview freezing, idempotent execution, partial outcomes, deletion/history preservation, and audit summaries in `backend/src/test/kotlin/com/octopusllm/admin/AdminModelAccessControllerTest.kt`
- [ ] T022 [P] [US2] Add frontend tests for cross-page selection, exact preview scope, independent allow/revoke/show/hide confirmation, destructive delete confirmation, busy controls, and partial-failure retry in `frontend/src/components/admin/AdminModelAccessPage.test.tsx`

### Implementation for User Story 2

- [X] T023 [US2] Implement administrator-only cross-connection model search, filtering, stable sorting, pagination, and safe response projection in `backend/src/main/kotlin/com/octopusllm/admin/AdminModelAccessService.kt`
- [X] T024 [US2] Implement preview-frozen selection, explicit-ID/filter modes, exclusions, bounded target count, per-item idempotent outcomes, concurrent-change handling, and operation expiry in `backend/src/main/kotlin/com/octopusllm/admin/AdminModelAccessService.kt` and `backend/src/main/kotlin/com/octopusllm/admin/AdminModelBulkOperation.kt`
- [X] T025 [US2] Expose `GET /api/v2/admin/models`, preview, execute, and operation-status endpoints with administrator identity and safe error responses in `backend/src/main/kotlin/com/octopusllm/admin/AdminModelAccessController.kt`
- [X] T026 [US2] Implement the same-origin admin model list, selection, preview, execute, status, and failed-item retry clients in `frontend/src/lib/api/adminModelAccess.ts`
- [X] T027 [US2] Build the responsive `/admin/models` table with URL-backed filters/page state, page/all matching selection, state badges, confirmations, progress, result summaries, and retry affordances in `frontend/src/components/admin/AdminModelAccessPage.tsx` and `frontend/src/app/(app)/admin/models/page.tsx`
- [X] T028 [US2] Add the model-management navigation entry and connected admin route wiring in `frontend/src/components/admin/AdminShell.tsx` and `frontend/src/app/(app)/admin/models/page.tsx`
- [ ] T029 [US2] Add administrator Playwright coverage for 100-plus models, cross-connection filtering, multi-page select-all, preview scope, all bulk actions, partial results, and historical-response preservation in `frontend/e2e/admin-anonymous-model-access.spec.ts`
- [X] T056 [US2] Add administrator-configurable Guest defaults, enforce enabled/anonymous eligibility and a maximum of three defaults, order defaults first in the public catalogue, and expose the per-model control in `frontend/src/components/admin/AdminModelAccessPage.tsx` and the built-in model API

**Checkpoint**: Administrators can safely manage the public allowlist and normal display state across the catalogue without changing the other state accidentally.

---

## Phase 5: User Story 3 - Preserve Anonymous Conversations Locally (Priority: P1)

**Goal**: Keep anonymous prompts and streamed response state in the same browser's versioned local storage, while making local-only conversations unshareable and resilient to storage failures.

**Independent Test**: Create multiple anonymous conversations, refresh and revisit them, simulate unavailable/full/corrupt storage, and confirm readable local content remains in memory where possible with no share action or server history exposure.

### Tests for User Story 3

- [ ] T030 [P] [US3] Add versioning, canonical digest, corruption, quota, unavailable-storage, bounded-size, and atomic-update tests in `frontend/src/lib/utils/anonymousConversationStorage.test.ts`
- [ ] T031 [P] [US3] Add conversation hook tests for event-by-event persistence, refresh recovery, multi-tab last-write handling, stale model labels, and local sync-state retention in `frontend/src/lib/hooks/useAnonymousConversations.test.ts`
- [ ] T032 [P] [US3] Add UI tests proving local-only conversations never render share URLs/actions and show storage warnings in `frontend/src/components/chat/ChatPage.test.tsx` and `frontend/src/components/chat/ShareConversationButton.test.tsx`

### Implementation for User Story 3

- [X] T033 [US3] Implement the versioned `octopus.anonymous-conversations.v1` envelope, stable conversation/turn/request IDs, canonical digest, size bounds, corruption recovery, quota handling, and atomic local replacement in `frontend/src/lib/utils/anonymousConversationStorage.ts`
- [X] T034 [US3] Implement local conversation CRUD, active-conversation selection, event persistence, refresh restoration, and storage warning state in `frontend/src/lib/hooks/useAnonymousConversations.ts`
- [X] T035 [US3] Integrate local prompt/response snapshots and partial/error states with the anonymous stream in `frontend/src/components/chat/AnonymousChatPage.tsx` and `frontend/src/components/chat/AnonymousChatNotice.tsx`
- [X] T036 [US3] Add local conversation listing/actions and explicitly suppress share, account-session, media, and tool actions until a server session ID exists in `frontend/src/components/chat/AnonymousChatPage.tsx`
- [ ] T037 [US3] Add refresh, storage failure, stale/deleted model readability, and no-share Playwright coverage in `frontend/e2e/anonymous-local-storage.spec.ts`

**Checkpoint**: Anonymous work survives same-browser refreshes when storage permits, remains readable after model policy changes, and cannot be shared before synchronization.

---

## Phase 6: User Story 4 - Migrate Local Conversations on Registration (Priority: P2)

**Goal**: Automatically import eligible local conversations after registration login, exactly once per conversation, while preserving failed/skipped local data for retry.

**Independent Test**: Register in a browser containing complete, failed, unsupported, and multiple local conversations; verify post-login import results, exact-once history, conflict behavior, retained failures, and normal sharing after successful import.

### Tests for User Story 4

- [ ] T038 [P] [US4] Add backend sync tests for authenticated ownership, supported snapshot import without provider calls, per-conversation transaction isolation, duplicate retry, digest conflict, concurrent race, unsupported-state skip, and safe errors in `backend/src/test/kotlin/com/octopusllm/anonymous/AnonymousConversationSyncServiceTest.kt`
- [ ] T039 [P] [US4] Add registration-flow tests proving sync starts only after login, clears only confirmed items, preserves failures, and does not merge during ordinary login in `frontend/src/components/auth/RegisterForm.test.tsx`

### Implementation for User Story 4

- [X] T040 [US4] Implement per-conversation validation, canonical digest verification, session/turn/response snapshot import, unique identity reservation, and partial result mapping in `backend/src/main/kotlin/com/octopusllm/anonymous/AnonymousConversationSyncService.kt`
- [X] T041 [US4] Expose authenticated `POST /api/v2/anonymous/conversations/sync` with bounded batch/body limits and safe item-level conflict/skip/failure responses in `backend/src/main/kotlin/com/octopusllm/anonymous/AnonymousConversationSyncController.kt`
- [X] T042 [US4] Implement the sync request/response client and registration-to-login handoff in `frontend/src/lib/api/anonymousConversationSync.ts` and `frontend/src/components/auth/RegisterForm.tsx`
- [X] T043 [US4] Add retryable synchronization status, confirmed-local cleanup, unsupported-data retention, and post-import server-session handoff in `frontend/src/lib/hooks/useAnonymousConversations.ts` and `frontend/src/components/chat/AnonymousChatPage.tsx`
- [ ] T044 [US4] Add registration migration Playwright coverage for partial success, retry/no-duplicates, digest conflict, local retention, and sharing after confirmed import in `frontend/e2e/anonymous-conversation-sync.spec.ts`

**Checkpoint**: A newly registered user receives each eligible local conversation exactly once, while failed or unsupported data remains available locally and retryable.

---

## Phase 7: User Story 5 - Continue Existing Authenticated Use (Priority: P2)

**Goal**: Preserve authenticated model access, server history, account features, provider capabilities, and sharing behavior while anonymous access is added.

**Independent Test**: Use an authenticated account before and after anonymous policy changes to verify normal model selection, session history, tools/media, sync-created session sharing, and private model access remain governed by existing rules.

### Tests for User Story 5

- [ ] T045 [P] [US5] Add authenticated backend regression tests for model visibility, session persistence, runner delegation, retry behavior, and unchanged access when anonymous policy changes in `backend/src/test/kotlin/com/octopusllm/chat/ChatServiceTest.kt` and `backend/src/test/kotlin/com/octopusllm/connection/ConfiguredModelServiceTest.kt`
- [ ] T046 [P] [US5] Add authenticated frontend regression tests for server sessions, media/tools controls, history navigation, and share controls in `frontend/src/components/chat/SessionSidebar.test.tsx` and `frontend/src/components/chat/ShareConversationButton.test.tsx`

### Implementation for User Story 5

- [X] T047 [US5] Rewire authenticated `submitTurn`/retry execution through `LlmTurnRunner` while retaining persistence, ownership checks, media/tool behavior, and existing response snapshots in `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`
- [ ] T048 [US5] Verify synchronized sessions enter existing history/share flows without granting model permissions and preserve deleted-model response readability in `backend/src/test/kotlin/com/octopusllm/share/ShareServiceTest.kt` and `backend/src/test/kotlin/com/octopusllm/chat/SharedQuestImportTest.kt`
- [ ] T049 [US5] Add authenticated-vs-anonymous route and capability regression coverage to `frontend/e2e/chat-ux-redesign.spec.ts`

**Checkpoint**: Existing authenticated usage remains compatible, and only successfully imported local conversations gain ordinary authenticated session/share behavior.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Harden privacy, operations, documentation, and end-to-end verification across all stories.

- [ ] T050 [P] Add security/redaction tests for public DTOs, logs/metrics, cache headers, raw-IP/key exclusion, exact security permits, and no anonymous persistence in `backend/src/test/kotlin/com/octopusllm/security/AnonymousAccessSecurityTest.kt`
- [X] T051 [P] Document deployment configuration, anonymous limits, storage/privacy boundaries, and operational rollback expectations in `docs/anonymous-model-access.md`
- [X] T052 [P] Add focused proxy-path tests for public, sync, and admin routes and verify upstream paths are preserved in `frontend/src/app/api/[...path]/route.test.ts`
- [ ] T053 Run the backend unit/integration suite and production build defined in `specs/010-anonymous-model-access/quickstart.md`, then fix failures without weakening the acceptance criteria
- [ ] T054 Run frontend unit, type-check, lint, production-build, and Playwright flows from the published frontend origin defined in `specs/010-anonymous-model-access/quickstart.md`
- [ ] T055 Run the Docker Compose smoke flow against the published frontend origin and verify both `localhost` and `127.0.0.1` behavior where configured in `docker-compose.yml` and `specs/010-anonymous-model-access/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No feature dependency; T001–T003 can run in parallel.
- **Phase 2 (Foundational)**: Depends on Phase 1; T004 must complete before entity/repository tasks, and T009 depends on understanding the existing chat path. T005–T011 can then proceed in parallel where their files do not overlap.
- **Phase 3 (US1)**: Depends on Phase 2; delivers the MVP public chat path.
- **Phase 4 (US2)**: Depends on Phase 2 and can run in parallel with US1 after the shared model schema is ready.
- **Phase 5 (US3)**: Depends on US1's public chat surface, especially T017–T019; storage tests and utility work can proceed in parallel once the public event shape is fixed.
- **Phase 6 (US4)**: Depends on US3's local envelope/digest implementation and the existing registration/login flow.
- **Phase 7 (US5)**: Depends on Phase 2; T048 additionally depends on US4's imported-session behavior.
- **Phase 8 (Polish)**: Depends on all stories selected for release; T053–T055 run after implementation and focused tests exist.

### User Story Dependencies

```text
Foundational
   ├── US1 (P1) ──→ US3 (P1) ──→ US4 (P2) ──→ US5 sharing regression
   ├── US2 (P1) ────────────────────────────────→ cross-cutting verification
   └── US5 (P2 authenticated regression)
```

- **US1**: No dependency on another user story after Foundational; recommended MVP.
- **US2**: No dependency on another user story after Foundational; it shares the configured-model schema with US1.
- **US3**: Depends on US1's anonymous event and chat surface; it is independently testable with mocked public SSE.
- **US4**: Depends on US3's versioned local envelope and digest; backend import tests can be developed in parallel with frontend registration work.
- **US5**: Depends on Foundational runner/security changes; synchronized-session sharing verification depends on US4.

### Within Each User Story

- Write contract/unit/integration tests first and confirm they fail for the missing behavior.
- Implement persistence/model changes before services, services before controllers/API clients, and API clients before UI/E2E integration.
- Keep story checkpoints independently runnable; do not make anonymous persistence depend on authenticated session APIs.
- Use a new preview for failed bulk items rather than silently resubmitting successful items.

## Parallel Execution Examples

### User Story 1

```text
Parallel: T012 backend contract tests and T013 frontend API/SSE tests
Parallel after T012/T013: T014 safe catalogue mapping and T017 frontend API client
Sequential: T015 → T016, then T018 → T019 → T020
```

### User Story 2

```text
Parallel: T021 backend tests and T022 admin UI tests
Parallel after the schema: T023 admin query service and T026 frontend API client
Sequential: T024 → T025 and T027 → T028 → T029
```

### User Story 3

```text
Parallel: T030 storage tests, T031 hook tests, and T032 no-share UI tests
Sequential: T033 → T034 → T035/T036 → T037
```

### User Story 4

```text
Parallel: T038 backend sync tests and T039 registration-flow tests
Parallel after the contract: T040/T041 backend sync implementation and T042 frontend client/handoff
Sequential: T043 → T044
```

### User Story 5

```text
Parallel: T045 backend regression tests and T046 frontend regression tests
Sequential: T047 → T048; T049 can run after the authenticated/public route shape is stable
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 setup and Phase 2 foundational tasks.
2. Complete US1: public safe model catalogue, text-only anonymous SSE, limits, and public `/chat` route.
3. Run the US1 contract/unit and Playwright tests independently.
4. Stop for a product demo before adding local persistence, admin bulk management, or registration migration.

### Incremental Delivery

1. Deliver US1 for anonymous discovery and first-use chat.
2. Deliver US2 so administrators can operate the allowlist and normal display states at scale.
3. Deliver US3 for browser-local continuity and the no-share boundary.
4. Deliver US4 for post-registration migration and exact-once import.
5. Deliver US5 and Phase 8 hardening before general release.

### Parallel Team Strategy

1. One developer completes Phase 1–2 and owns the shared runner/security/schema seams.
2. After the foundation checkpoint, one developer owns US1, one owns US2, and one owns US3 storage work against the agreed event/local envelope contracts.
3. After US3 stabilizes, the registration owner implements US4 while the authenticated regression owner implements US5.
4. Keep API contract tests and Playwright verification with the story owner so every checkpoint remains independently demonstrable.

## Task Format Validation

All 55 implementation tasks use the required checklist format: `- [ ]`, sequential `T###` ID, `[P]` only for parallelizable work, `[US#]` on user-story tasks, and an explicit repository file path in every task description.
