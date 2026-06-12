# Tasks: Protocol, Connection, and Configured Model

**Input**: Design documents from `/specs/003-protocol-connection-model/`  
**Prerequisites**: `spec.md`, `plan.md`, `data-model.md`, `contracts/api-v2.md`, `quickstart.md`

**Tests**: Required by the specification and constitution. Test tasks precede implementation tasks in each user story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it touches independent files.
- **[Story]**: User story mapping from `spec.md`.

## Phase 1: Setup

**Purpose**: Establish feature scaffolding and shared v2 contract primitives.

- [X] T001 Verify Node.js 24, Java 21, strict TypeScript, and current migration history using `frontend/package.json`, `frontend/tsconfig.json`, `backend/build.gradle.kts`, and `backend/src/main/resources/db/migration/`
- [X] T002 [P] Add paginated response and standard API error DTOs for v2 endpoints in `backend/src/main/kotlin/com/octopusllm/api/v2/ApiV2Types.kt`
- [X] T003 [P] Add frontend v2 pagination and error types in `frontend/src/lib/types/api.ts`
- [X] T004 [P] Create test fixtures for users, encrypted keys, connections, and configured models in `backend/src/test/kotlin/com/octopusllm/testsupport/Feature003Fixtures.kt`
- [X] T005 Configure `/api/v2/**` authentication defaults and public protocol/catalogue exceptions in `backend/src/main/kotlin/com/octopusllm/config/SecurityConfig.kt`

---

## Phase 2: Foundational

**Purpose**: Shared protocol, security, migration, and persistence infrastructure that blocks all stories.

- [X] T006 [P] Add conservative `ProtocolDefinition` constants and validated capability merge behavior in `backend/src/main/kotlin/com/octopusllm/model/ProtocolDefinition.kt`
- [X] T007 [P] Add static, non-authoritative catalogue entries including current Kimi models in `backend/src/main/kotlin/com/octopusllm/model/ModelCatalogue.kt`
- [X] T008 [P] Extend `LlmAdapter` with `protocolId` and implement protocol declarations in `backend/src/main/kotlin/com/octopusllm/llm/LlmAdapter.kt` and `backend/src/main/kotlin/com/octopusllm/llm/adapter/`
- [X] T009 Implement Spring-injected, duplicate-safe protocol lookup in `backend/src/main/kotlin/com/octopusllm/llm/ProtocolAdapterRegistry.kt`
- [X] T010 [P] Write registry extensibility and duplicate-registration tests in `backend/src/test/kotlin/com/octopusllm/llm/ProtocolAdapterRegistryTest.kt`
- [X] T011 [P] Implement normalized URI and public-address validation in `backend/src/main/kotlin/com/octopusllm/connection/ConnectionEndpointPolicy.kt`
- [ ] T012 [P] Write SSRF tests covering IPv4, IPv6, DNS answers, metadata targets, redirects, userinfo, fragments, ports, and development HTTP in `backend/src/test/kotlin/com/octopusllm/connection/ConnectionEndpointPolicyTest.kt`
- [X] T013 Create `Connection` and `ConfiguredModel` entities with composite ownership integrity in `backend/src/main/kotlin/com/octopusllm/connection/Connection.kt` and `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModel.kt`
- [X] T014 Create owner-scoped and pageable repositories in `backend/src/main/kotlin/com/octopusllm/connection/ConnectionRepository.kt` and `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModelRepository.kt`
- [ ] T015 Write V016-to-V017 migration and rollback tests in `backend/src/test/kotlin/com/octopusllm/migration/ProtocolConnectionMigrationTest.kt`
- [X] T016 Implement transactional V017 data migration, audit checks, ownership constraints, response snapshots, and chat-turn UUID arrays in `backend/src/main/resources/db/migration/V017__protocol_connection_model_migration.sql`
- [X] T017 Run the V017 migration test against PostgreSQL 16 and verify source tables are removed only after validation in `backend/src/test/kotlin/com/octopusllm/migration/ProtocolConnectionMigrationTest.kt`

**Checkpoint**: Protocol lookup, endpoint security, schema, and migration are ready.

---

## Phase 3: User Story 1 - Configure an endpoint and models (Priority: P1)

**Goal**: Users manage secure connections and arbitrary configured models without key disclosure or live discovery.

**Independent Test**: Create, list, edit, rotate, and delete a connection/model; reload data; verify owner isolation and zero key substrings in responses.

### Tests for User Story 1

- [ ] T018 [P] [US1] Write v2 connection contract tests for pagination, validation, owner isolation, key non-disclosure, rotation, and standard errors in `backend/src/test/kotlin/com/octopusllm/connection/ConnectionControllerV2Test.kt`
- [ ] T019 [P] [US1] Write configured-model contract tests for duplicate model IDs, custom-parameter patching, capability validation, ordering, cascade, and owner isolation in `backend/src/test/kotlin/com/octopusllm/connection/ConfiguredModelControllerV2Test.kt`
- [ ] T020 [P] [US1] Write service tests for encryption, endpoint revalidation, map null-removal, and not-found behavior in `backend/src/test/kotlin/com/octopusllm/connection/ConnectionServiceTest.kt`

### Implementation for User Story 1

- [X] T021 [US1] Implement connection CRUD, key rotation, owner-scoped lookup, endpoint validation, and encrypted-key handling in `backend/src/main/kotlin/com/octopusllm/connection/ConnectionService.kt`
- [X] T022 [US1] Implement configured-model CRUD, validated capabilities/custom parameters, ordering, and owner-scoped lookup in `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModelService.kt`
- [X] T023 [US1] Implement paginated `/api/v2/connections` and key-rotation endpoints in `backend/src/main/kotlin/com/octopusllm/connection/ConnectionControllerV2.kt`
- [X] T024 [US1] Implement paginated `/api/v2/configured-models` endpoints in `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModelControllerV2.kt`
- [X] T025 [P] [US1] Add v2 connection/configured-model API client functions in `frontend/src/lib/api/connections.ts`
- [X] T026 [P] [US1] Add connection and configured-model v2 DTOs in `frontend/src/lib/types/api.ts`
- [X] T027 [P] [US1] Write Settings component tests for one management entry point, key rotation, manual model entry, editing custom parameters, and delete confirmation in `frontend/src/components/settings/connections/`
- [X] T028 [US1] Implement `ConnectionCard` and `ModelRow` in `frontend/src/components/settings/connections/ConnectionCard.tsx` and `frontend/src/components/settings/connections/ModelRow.tsx`
- [X] T029 [US1] Implement add/edit/key-rotation connection dialogs in `frontend/src/components/settings/connections/AddConnectionDialog.tsx` and `frontend/src/components/settings/connections/EditConnectionDialog.tsx`
- [X] T030 [US1] Implement add/edit configured-model dialogs with manual fallback and custom parameters in `frontend/src/components/settings/connections/AddModelDialog.tsx` and `frontend/src/components/settings/connections/EditModelDialog.tsx`
- [X] T031 [US1] Rewrite the single Settings management page with paginated connection cards and one Back to Chat action in `frontend/src/app/(app)/settings/models/ModelsSettingsPage.tsx`
- [X] T032 [US1] Remove live model-sync controls and obsolete provider/model components from `frontend/src/components/models/` and `frontend/src/lib/api/userConfig.ts`

**Checkpoint**: Connection and configured-model management works independently through API v2.

---

## Phase 4: User Story 2 - Chat using configured model identities (Priority: P1)

**Goal**: Concurrent chat and persistence use configured-model UUID identity while retaining literal model metadata.

**Independent Test**: Select identical model IDs on two mock connections and verify two streams, two immutable responses, and historical rendering after deletion.

### Tests for User Story 2

- [ ] T033 [P] [US2] Write orchestrator tests proving registry lookup, concurrent dispatch, endpoint revalidation, and UUID event attribution in `backend/src/test/kotlin/com/octopusllm/llm/ConcurrentLlmOrchestratorTest.kt`
- [ ] T034 [P] [US2] Write v2 chat integration tests for duplicate model IDs, disabled/foreign UUID rejection, idempotency, snapshots, and deletion-safe history in `backend/src/test/kotlin/com/octopusllm/chat/ChatControllerV2Test.kt`
- [X] T035 [P] [US2] Write frontend stream-state and chat picker tests using configured-model UUIDs in `frontend/src/lib/hooks/useParallelStream.test.ts` and `frontend/src/components/chat/ModelSelectorPanel.test.tsx`

### Implementation for User Story 2

- [X] T036 [US2] Refactor `ConcurrentLlmOrchestrator` to resolve adapters through `ProtocolAdapterRegistry` and emit both configured-model UUID and model ID in `backend/src/main/kotlin/com/octopusllm/llm/ConcurrentLlmOrchestrator.kt`
- [X] T037 [US2] Extend LLM stream events with `configuredModelId` while preserving `modelId` meaning in `backend/src/main/kotlin/com/octopusllm/llm/LlmStreamEvent.kt`
- [X] T038 [US2] Update provider response persistence with immutable configured-model snapshots in `backend/src/main/kotlin/com/octopusllm/chat/ProviderResponse.kt`
- [X] T039 [US2] Resolve owner-scoped configured-model UUIDs, reject duplicates/disabled rows, revalidate endpoints, and persist both selected identity arrays in `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`
- [X] T040 [US2] Implement `/api/v2/chat/sessions` DTOs and SSE serialization in `backend/src/main/kotlin/com/octopusllm/chat/ChatControllerV2.kt`
- [X] T041 [P] [US2] Add v2 chat request and SSE event types in `frontend/src/lib/types/api.ts`
- [X] T042 [P] [US2] Add v2 chat API and SSE parsing in `frontend/src/lib/api/chatV2.ts`
- [X] T043 [US2] Key parallel stream state by configured-model UUID and retain model metadata in `frontend/src/lib/hooks/useParallelStream.ts`
- [X] T044 [US2] Update compact model selection, persistence, session loading, and request payloads in `frontend/src/app/(app)/chat/page.tsx` and `frontend/src/components/chat/ModelSelectorPanel.tsx`
- [X] T045 [US2] Render immutable response snapshot names after configuration deletion in `frontend/src/components/chat/ModelResponsePanel.tsx` and `frontend/src/lib/utils/exportConversation.ts`
- [X] T046 [US2] Add structured call logs without keys or sensitive parameters in `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`

**Checkpoint**: Chat supports duplicate model IDs and deletion-safe historical attribution.

---

## Phase 5: User Story 3 - Use catalogue suggestions without discovery (Priority: P2)

**Goal**: Catalogue suggestions improve setup but never block arbitrary model entry.

**Independent Test**: Browse paginated protocol-filtered suggestions, then create an uncatalogued model while catalogue is unavailable.

### Tests for User Story 3

- [ ] T047 [P] [US3] Write protocol/catalogue pagination and filtering contract tests in `backend/src/test/kotlin/com/octopusllm/model/ProtocolCatalogueControllerV2Test.kt`
- [X] T048 [P] [US3] Write Add Model catalogue-success and catalogue-failure tests in `frontend/src/components/settings/connections/AddModelDialog.test.tsx`

### Implementation for User Story 3

- [X] T049 [US3] Implement paginated public `/api/v2/protocols` and `/api/v2/catalogue` endpoints in `backend/src/main/kotlin/com/octopusllm/model/ProtocolCatalogueControllerV2.kt`
- [X] T050 [P] [US3] Add protocol/catalogue API functions in `frontend/src/lib/api/connections.ts`
- [X] T051 [US3] Integrate optional catalogue suggestions without disabling manual entry in `frontend/src/components/settings/connections/AddModelDialog.tsx`
- [X] T052 [US3] Delete `ProviderModelSyncService` and remove sync endpoints only after all callers are migrated in `backend/src/main/kotlin/com/octopusllm/userconfig/` and `frontend/src/`

**Checkpoint**: Catalogue is optional, paginated, and discovery-free.

---

## Phase 6: User Story 4 - Preserve existing user configuration (Priority: P1)

**Goal**: Deploy the redesign without losing usable configuration or historical chat data.

**Independent Test**: Upgrade a V016 database fixture and compare key bytes, usable model counts, historical responses, and audit totals.

### Tests for User Story 4

- [X] T053 [P] [US4] Add migration fixture cases for every provider mapping, custom base URL, custom parameters, disabled model, and missing key in `backend/src/test/kotlin/com/octopusllm/migration/ProtocolConnectionMigrationTest.kt`
- [ ] T054 [P] [US4] Add historical response/session API tests against migrated data in `backend/src/test/kotlin/com/octopusllm/chat/MigratedSessionCompatibilityTest.kt`

### Implementation for User Story 4

- [X] T055 [US4] Finalize provider-to-protocol and default-URL migration mappings in `backend/src/main/resources/db/migration/V017__protocol_connection_model_migration.sql`
- [X] T056 [US4] Backfill configured-model selection preference where uniquely resolvable and define null fallback in `backend/src/main/resources/db/migration/V017__protocol_connection_model_migration.sql`
- [X] T057 [US4] Remove obsolete entities, repositories, controllers, and catalogue DB access only after migration compatibility tests pass in `backend/src/main/kotlin/com/octopusllm/model/` and `backend/src/main/kotlin/com/octopusllm/userconfig/`
- [X] T058 [US4] Document the coordinated removal of affected v1 routes and the atomic v2 backend/frontend rollout in `specs/003-protocol-connection-model/contracts/api-v2.md`

**Checkpoint**: Existing data upgrades safely and remains readable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final quality gates and operational verification.

- [X] T059 [P] Add frontend Playwright flow for connection -> model -> chat -> delete -> historical reload in `frontend/e2e/protocol-connection-model.spec.ts`
- [X] T060 [P] Add API documentation examples and pagination/error details in `specs/003-protocol-connection-model/contracts/api-v2.md`
- [ ] T061 Run backend `./gradlew build` and resolve all failures in `backend/`
- [X] T062 Run frontend `npx tsc --noEmit`, `npx vitest run`, lint, and production build using Node.js 24 in `frontend/`
- [ ] T063 Run Docker Compose build and execute all scenarios in `specs/003-protocol-connection-model/quickstart.md`
- [X] T064 Run `.specify/scripts/bash/check-prerequisites.sh --json --require-tasks --include-tasks` and confirm it resolves `specs/003-protocol-connection-model`
- [X] T065 Update `AGENTS.md` active technologies/recent changes and remove stale provider-discovery guidance in `AGENTS.md` and `frontend/AGENTS.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup**: Starts immediately.
- **Foundational**: Depends on Setup and blocks every user story.
- **US1**: Depends on Foundational.
- **US2**: Depends on Foundational and backend configured-model lookup from US1; tests can start in parallel with US1 UI.
- **US3**: Depends on Foundational; can proceed in parallel with US1/US2 after catalogue types exist.
- **US4**: Migration tests begin in Foundational and final cleanup waits for US1/US2 compatibility.
- **Polish**: Depends on all selected stories.

### User Story Completion Order

1. US1 connection/model APIs
2. US2 UUID-based chat
3. US3 optional catalogue and discovery removal
4. US4 destructive cleanup after migration verification

### Parallel Opportunities

- T006-T008, T010-T012 can run in parallel.
- T018-T020 can run in parallel before US1 implementation.
- Backend US1 work and frontend US1 tests/types can run in parallel.
- T033-T035 can run in parallel before US2 implementation.
- US3 contract/frontend tests can run while US2 backend work proceeds.
- T059 and T060 can run in parallel before final builds.

## Implementation Strategy

### MVP

1. Complete Setup and Foundational phases.
2. Complete US1.
3. Complete US2.
4. Validate duplicate-model chat and historical snapshots.

This delivers the redesigned configuration and core chat workflow without depending on catalogue UX.

### Incremental Delivery

1. Build and test v2 contracts and migration structures before deployment.
2. Move Settings and Chat to v2 in the same release.
3. Enable optional catalogue UX.
4. Remove affected v1 model/configuration/chat routes and obsolete storage after compatibility verification.
5. Deploy backend and frontend together as a coordinated major-version cutover.

## Format Validation

All implementation tasks use the required checkbox, sequential task ID, optional `[P]`, user-story label where applicable, and concrete file path.
