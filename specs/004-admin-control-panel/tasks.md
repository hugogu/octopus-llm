---
description: "Task list for Admin Control Panel"
---

# Tasks: Admin Control Panel

**Input**: Design documents from `/specs/004-admin-control-panel/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: Integration tests are INCLUDED because the project constitution mandates "New API endpoints MUST have at least one integration test covering the happy path." Concurrency and scale tests are additionally required to satisfy SC-006/SC-007 and the review findings (C1, C2, H4, H5, H6).

**Organization**: Tasks are grouped by user story (US1, US2, US3) for independent implementation and testing.

## Path Conventions

- Backend (Kotlin/Spring): `backend/src/main/kotlin/com/octopusllm/`, tests `backend/src/test/kotlin/com/octopusllm/`, migrations `backend/src/main/resources/db/migration/`
- Frontend (Next.js): `frontend/src/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configuration and scaffolding for the feature.

- [ ] T001 [P] Add `app.admin.bootstrap-email` property (default empty) to `backend/src/main/resources/application.yml` and pass it through as an env var in `docker-compose.yml`
- [ ] T002 [P] Scaffold frontend admin area: create `frontend/src/app/(app)/admin/` route group, `frontend/src/components/admin/` directory, and an empty `frontend/src/lib/api/admin.ts` client module

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Account-state schema, session/authority enforcement, and audit infrastructure. **MUST complete before US1 and US2.**

- [ ] T003 Create Flyway migration `backend/src/main/resources/db/migration/V019__admin_control_panel.sql` adding: `users` columns `is_admin`/`is_active`/`is_disabled` (BOOLEAN NOT NULL DEFAULT FALSE) and `session_epoch` (INTEGER NOT NULL DEFAULT 0); partial index `idx_users_enabled_admins`; index supporting email search + deterministic order; `connections.is_builtin` (BOOLEAN NOT NULL DEFAULT FALSE); tables `connection_allocations`, `password_resets`, `admin_audit_log` with indexes per [data-model.md](data-model.md)
- [ ] T004 Extend `User` entity with `isAdmin`, `isActive`, `isDisabled`, `sessionEpoch` in `backend/src/main/kotlin/com/octopusllm/auth/User.kt`
- [ ] T005 Extend `UserRepository` in `backend/src/main/kotlin/com/octopusllm/auth/UserRepository.kt` with: paged email search (deterministic `createdAt, id` order), `countByIsAdminTrueAndIsDisabledFalse()`, and a concurrency-safe conditional disable update (`@Modifying` query that disables + increments `session_epoch` only when the row is not the last usable admin) — see [research.md](research.md) Decision 7
- [ ] T006 Add `sessionEpoch: Int` to `JwtClaims`, change `JwtTokenService.issue` to accept and embed the epoch claim, and have `validate` return it (treat a missing claim as `0`) in `backend/src/main/kotlin/com/octopusllm/auth/JwtTokenService.kt`
- [ ] T007 Enforce account state in `backend/src/main/kotlin/com/octopusllm/config/SecurityConfig.kt`: in the security-context load, fetch the `User`, reject (`401`) when `is_disabled` or `token.sessionEpoch < user.sessionEpoch`, grant `ROLE_ADMIN` when `is_admin`, and add `.pathMatchers("/api/v2/admin/**").hasRole("ADMIN")`
- [ ] T008 [P] Create `AdminAuditLog` entity in `backend/src/main/kotlin/com/octopusllm/admin/AdminAuditLog.kt`
- [ ] T009 Create `AdminAuditLogRepository` (depends on T008) in `backend/src/main/kotlin/com/octopusllm/admin/AdminAuditLogRepository.kt`
- [ ] T010 Create `AdminAuditService` (write append-only rows, excluding key/password material; depends on T008/T009) in `backend/src/main/kotlin/com/octopusllm/admin/AdminAuditService.kt`
- [ ] T011 Create `AdminBootstrap` `ApplicationRunner` (idempotently promote the configured email to `is_admin = true, is_active = true`; depends on T004/T005) in `backend/src/main/kotlin/com/octopusllm/admin/AdminBootstrap.kt`
- [ ] T012 Integration test `backend/src/test/kotlin/com/octopusllm/admin/AdminSecurityIntegrationTest.kt`: disabled user gets `401` on next request; non-admin gets `403` on `/api/v2/admin/**`; **epoch revocation (H6)** — issue a token, bump `session_epoch`, re-login immediately, and confirm the fresh token succeeds while the pre-bump token is rejected within the same second

**Checkpoint**: Migration applies, security filter enforces disable/epoch/role, audit + bootstrap exist.

---

## Phase 3: User Story 1 - Manage user accounts (Priority: P1) 🎯 MVP

**Goal**: Admin can list/search accounts and activate, disable/enable, and reset passwords; last-usable-admin protected (disable/demote/reset) under concurrency; data preserved on disable; admins discover the panel, users complete resets.

**Independent Test**: As admin, disable a test account → it cannot log in or authenticate; re-enable → access restored; reset password → old password fails, the emailed link sets a new one; last-admin disable/reset refused; non-admins never see admin nav.

### Implementation for User Story 1

- [ ] T013 [P] [US1] Create `PasswordReset` entity in `backend/src/main/kotlin/com/octopusllm/auth/PasswordReset.kt`
- [ ] T014 [US1] Create `PasswordResetRepository` (depends on T013) with an **atomic single-use** consume query — `@Modifying UPDATE ... SET used_at = now() WHERE token = :token AND used_at IS NULL AND expires_at > now()` returning the affected-row count — in `backend/src/main/kotlin/com/octopusllm/auth/PasswordResetRepository.kt` (H5)
- [ ] T015 [US1] Add `sendPasswordResetEmail(toEmail, token)` to `backend/src/main/kotlin/com/octopusllm/auth/EmailService.kt`
- [ ] T016 [US1] Update `backend/src/main/kotlin/com/octopusllm/auth/AuthService.kt`: (a) `login` rejects `is_disabled` accounts with `401` before issuing a token and embeds `session_epoch` in the JWT (H1); (b) `adminResetPassword(userId)` scrambles the hash, increments `session_epoch`, creates a token, emails the link, and is refused when the target is the last usable admin (C2); (c) `confirmPasswordReset(token, password)` uses the atomic consume (T014) and sets a new bcrypt hash, succeeding only when one row was affected (H5)
- [ ] T017 [US1] Add public endpoint `POST /api/v1/auth/password-reset/confirm` in `backend/src/main/kotlin/com/octopusllm/auth/AuthController.kt` (permitted under `/api/v1/auth/**`)
- [ ] T018 [US1] Add `GET /api/v2/me` returning `{ id, email, isAdmin, isActive }` for the authenticated caller in `backend/src/main/kotlin/com/octopusllm/auth/MeController.kt` (FR-026 / H3)
- [ ] T019 [US1] Create `AdminUserService` in `backend/src/main/kotlin/com/octopusllm/admin/AdminUserService.kt`: paged list/search (deterministic order), activate, disable (concurrency-safe last-admin guard via the conditional update from T005 + epoch bump), enable (no epoch change), trigger reset (guarded for last admin, C2), all writing audit rows; the usable-admin invariant is enforced by conditional UPDATE / `SERIALIZABLE` retry, never a bare count-then-update (C1)
- [ ] T020 [US1] Create `AdminUserController` (`GET /api/v2/admin/users`, `POST .../{id}/activate|disable|enable|reset-password`) returning `AdminUserResponse` in a `PageResponse` with `items` per [contracts/admin-users.md](contracts/admin-users.md) in `backend/src/main/kotlin/com/octopusllm/admin/AdminUserController.kt`
- [ ] T021 [P] [US1] Integration test `backend/src/test/kotlin/com/octopusllm/admin/AdminUserControllerTest.kt`: list returns `items` and excludes `password_hash`; activate/disable/enable idempotent; last-admin disable refused `409`; last-admin reset refused `409` (C2); non-admin `403`
- [ ] T022 [P] [US1] Concurrency test `backend/src/test/kotlin/com/octopusllm/admin/LastAdminConcurrencyTest.kt`: with exactly two usable admins, two concurrent disable requests (and separately two concurrent reset requests) leave exactly one usable admin — one request succeeds, one is refused `409` (C1 / C2 / SC-007)
- [ ] T023 [P] [US1] Integration test `backend/src/test/kotlin/com/octopusllm/auth/PasswordResetFlowTest.kt`: admin reset → old password fails → confirm with token sets new password → login works; **concurrent confirm of the same token → exactly one succeeds** (H5); expired/used token rejected `400`; disabled account login returns `401` with no token (H1)
- [ ] T024 [P] [US1] Scale/perf test `backend/src/test/kotlin/com/octopusllm/admin/AdminUserListPerformanceTest.kt`: seed ≥10,000 accounts, assert first page returns in < 1s with deterministic ordering and indexed email search (SC-006 / H4)
- [ ] T025 [P] [US1] Add user-management methods (list/search, activate, disable, enable, reset-password) and a `me()` call to `frontend/src/lib/api/admin.ts`
- [ ] T026 [US1] Build admin users page (table, search, pagination over `items`, status badges, action buttons) at `frontend/src/app/(app)/admin/users/page.tsx`
- [ ] T027 [P] [US1] Build admin user UI components (user row, status badges, confirm dialogs as `"use client"`) in `frontend/src/components/admin/`
- [ ] T028 [US1] Gate admin discoverability (FR-026 / H3): fetch `GET /api/v2/me`, show the admin nav entry only when `isAdmin`, and guard `frontend/src/app/(app)/admin/` routes to redirect non-admins (wire in the app shell / layout)
- [ ] T029 [US1] Build the public password-reset page at `frontend/src/app/(auth)/reset-password/page.tsx` (read token from query, POST new password to `/api/v1/auth/password-reset/confirm`, then route to login) (FR-027 / H3)

**Checkpoint**: US1 fully functional and independently testable — MVP deliverable.

---

## Phase 4: User Story 2 - Allocate built-in connections to users (Priority: P1)

**Goal**: Admin manages platform-owned built-in connections (encrypted key) and allocates them read-only to activated users for chat; keys never exposed; shared across many users.

**Independent Test**: Admin creates a built-in connection + model, allocates to U1 (not U2); U1 sees it read-only and chats with it (key never returned); U2 sees nothing; revoke removes U1 only.

### Implementation for User Story 2

- [ ] T030 [P] [US2] Add `isBuiltin` field to `Connection` entity in `backend/src/main/kotlin/com/octopusllm/connection/Connection.kt`
- [ ] T031 [P] [US2] Create `ConnectionAllocation` entity (composite PK `connection_id`+`user_id`) in `backend/src/main/kotlin/com/octopusllm/admin/ConnectionAllocation.kt`
- [ ] T032 [US2] Create `ConnectionAllocationRepository` (depends on T031; find by user, by connection, exists, count) in `backend/src/main/kotlin/com/octopusllm/admin/ConnectionAllocationRepository.kt`
- [ ] T033 [US2] Extend `ConnectionRepository` with built-in queries and allocated-to-user queries in `backend/src/main/kotlin/com/octopusllm/connection/ConnectionRepository.kt`
- [ ] T034 [US2] Add `requireSelectable(userId, ids)` (owned ∪ enabled models on built-in connections allocated to the user) to `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModelService.kt`
- [ ] T035 [US2] Switch `ChatService.submitTurn` to `requireSelectable` instead of `requireOwned` in `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`
- [ ] T036 [US2] Include allocated built-in connections (flagged `builtin/readOnly`) in `ConnectionService.list` and add `builtin`/`readOnly` fields to `ConnectionResponseV2` in `backend/src/main/kotlin/com/octopusllm/connection/ConnectionService.kt` and `ConnectionControllerV2.kt`
- [ ] T037 [US2] Create `AdminConnectionService` (create built-in, list, patch, rotate key, delete, model CRUD addressed by configured-model UUID, allocate/revoke with `is_active` guard, audit) in `backend/src/main/kotlin/com/octopusllm/admin/AdminConnectionService.kt`
- [ ] T038 [US2] Create `AdminConnectionController` (`/api/v2/admin/connections` + `/models/{configuredModelId}` + `/allocations/{userId}`, all paged responses using `items`) per [contracts/admin-builtin-connections.md](contracts/admin-builtin-connections.md) in `backend/src/main/kotlin/com/octopusllm/admin/AdminConnectionController.kt`
- [ ] T039 [P] [US2] Integration test `backend/src/test/kotlin/com/octopusllm/admin/AdminConnectionControllerTest.kt`: create/list/patch/rotate/delete built-in; responses use `items` and omit the key; model paths use the configured-model UUID; allocation to a non-activated user `422`; revoke isolates other allocations
- [ ] T040 [P] [US2] Integration test `backend/src/test/kotlin/com/octopusllm/admin/BuiltinAllocationChatTest.kt`: allocated user lists the built-in as read-only, submits a turn using a built-in model with a mock adapter, response streams, key never appears in response/logs; non-owner `PATCH`/`DELETE` of the built-in returns `404`
- [ ] T041 [P] [US2] Add built-in connection + model + allocation methods to `frontend/src/lib/api/admin.ts`
- [ ] T042 [US2] Build admin built-in connections page (CRUD, model management, allocation picker) at `frontend/src/app/(app)/admin/connections/page.tsx` and components in `frontend/src/components/admin/`
- [ ] T043 [US2] Render allocated built-in connections as read-only (no edit/delete/key-reveal) in `frontend/src/components/settings/connections/` and surface `builtin`/`readOnly` flags in `frontend/src/lib/api/connections.ts`
- [ ] T044 [US2] Show allocated built-in models alongside own models in the chat model selector in `frontend/src/components/chat/`

**Checkpoint**: US1 AND US2 both work independently.

---

## Phase 5: User Story 3 - BYOK remains always available (Priority: P2)

**Goal**: Any registered, verified, non-disabled account uses its own connections/models regardless of activation; disabled accounts are fully blocked; own + allocated models coexist.

**Independent Test**: Register a fresh account, do not activate it, confirm BYOK create + chat works and no built-in is visible.

### Implementation for User Story 3

- [ ] T045 [P] [US3] Integration test `backend/src/test/kotlin/com/octopusllm/connection/ByokAlwaysAvailableTest.kt`: non-activated verified user creates and uses a BYOK connection/model successfully, and sees no built-in connections
- [ ] T046 [P] [US3] Integration test `backend/src/test/kotlin/com/octopusllm/auth/DisabledUserBlockedTest.kt`: disabled account is refused at login and on BYOK connection/model/chat endpoints until re-enabled
- [ ] T047 [P] [US3] Integration test `backend/src/test/kotlin/com/octopusllm/chat/CombinedModelSelectionTest.kt`: a turn selecting one owned model and one allocated built-in model produces two distinct streams/responses (depends on US2)
- [ ] T048 [US3] Verify BYOK and combined-model rendering are unaffected via a Playwright e2e touching `frontend/src/components/chat/` (own + allocated models both selectable)

**Checkpoint**: All user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validation, gates, and documentation.

- [ ] T049 [P] Execute [quickstart.md](quickstart.md) Scenarios A, B, C and record results
- [ ] T050 [P] Confirm zero key disclosure: grep backend logs/responses during built-in chat (Scenario B) for any key fragment
- [ ] T051 Backend quality gate: `cd backend && ./gradlew build` passes (compilation + all integration/concurrency/scale tests)
- [ ] T052 Frontend quality gate: `cd frontend && npx tsc --noEmit` passes with zero errors and admin Playwright e2e green
- [ ] T053 [P] Document the admin panel, `app.admin.bootstrap-email` setup, and the password-reset page in `README.md` / deployment docs

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup — **BLOCKS US1 and US2** (schema, security enforcement, audit, bootstrap).
- **US1 (Phase 3)**: Depends on Foundational. Independently testable and deployable (MVP).
- **US2 (Phase 4)**: Depends on Foundational. Independent of US1 (different endpoints/entities); reuses the activated-user state from Foundational.
- **US3 (Phase 5)**: Depends on Foundational; T047 additionally depends on US2 (allocated models). T045/T046 are independent of US1/US2.
- **Polish (Phase 6)**: Depends on all targeted stories.

### Within Each User Story

- Entities before their repositories (T013→T014, T031→T032, T008→T009→T010); repositories/services before controllers; controllers before frontend; integration tests after the endpoints they cover.

### Parallel Opportunities

- Setup: T001, T002 in parallel.
- Foundational: T008 runs parallel to the T004→T005 / T006→T007 chains; T009 (needs T008), T010 (needs T008/T009), and T011 (needs T004/T005) are sequential and **not** marked `[P]`.
- US1: T013 is `[P]`; T014 follows it (not `[P]`). Tests T021/T022/T023/T024 in parallel; frontend T025/T027 in parallel.
- US2: T030, T031 in parallel; T032 follows T031 (not `[P]`). Tests T039/T040 in parallel; T041 parallel with backend.
- US3: T045, T046, T047 in parallel.
- Once Foundational completes, US1 and US2 can be built by separate developers in parallel.

---

## Parallel Example: User Story 1 tests

```bash
# After the US1 endpoints exist, run these test tasks together:
Task: "AdminUserControllerTest.kt (T021)"
Task: "LastAdminConcurrencyTest.kt (T022)"
Task: "PasswordResetFlowTest.kt (T023)"
Task: "AdminUserListPerformanceTest.kt (T024)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup → 2. Phase 2 Foundational (critical) → 3. Phase 3 US1 → **STOP and validate** account lifecycle (including last-admin and disabled-login safety) → deploy/demo.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 (account management) → test → deploy (MVP).
3. US2 (built-in allocation) → test → deploy.
4. US3 (BYOK guarantees) → test → deploy.

### Parallel Team Strategy

After Foundational: Developer A takes US1, Developer B takes US2; US3 tests follow once US2 lands.
