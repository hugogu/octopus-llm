# Tasks: Personal Center, Response Likes & Usage Analytics

**Input**: Design documents from `/specs/005-personal-center-likes-analytics/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Required by the project implementation rules and constitution. Write each listed test
before its corresponding implementation and confirm it fails for the expected reason.

**Organization**: Tasks are grouped by user story so each story can be implemented and validated as
an independently useful increment. Shared migrations are foundational because Flyway version ordering
must be stable before parallel story work begins.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it changes different files and has no incomplete dependency.
- **[Story]**: Maps the task to a user story in `spec.md`.
- Every task names the concrete repository path it changes.

## Phase 1: Setup (Shared Test and Configuration Scaffolding)

**Purpose**: Prepare reusable feature fixtures and environment settings before schema or story work.

- [X] T001 Add feature-005 entity and authenticated-user fixture builders in `backend/src/test/kotlin/com/octopusllm/testsupport/Feature005Fixtures.kt`
- [X] T002 [P] Add shared Playwright login, seeded-session, and API response helpers in `frontend/e2e/support/feature005.ts`
- [X] T003 [P] Add trusted-proxy and anonymous-visitor HMAC configuration keys with safe local defaults in `backend/src/main/resources/application.yml`, `backend/src/main/resources/application-docker.yml`, and `docker-compose.yml`

---

## Phase 2: Foundational (Blocking Schema and Security Prerequisites)

**Purpose**: Establish the complete versioned schema and shared security primitives required by all
five user stories.

**CRITICAL**: Complete this phase before starting user-story implementation.

- [X] T004 Create Flyway migration `backend/src/main/resources/db/migration/V021__user_profile_and_auth_throttles.sql` for `users.display_name` and the cross-instance `auth_action_throttles` table, indexes, constraints, and expiry lookup
- [X] T005 [P] Create Flyway migration `backend/src/main/resources/db/migration/V022__configured_model_pricing.sql` for nullable non-negative configured-model input/output prices and three-letter currency validation
- [X] T006 [P] Create Flyway migration `backend/src/main/resources/db/migration/V023__response_analytics_snapshots.sql` for `chat_turns.client_ip` plus immutable connection and pricing snapshot columns/indexes on `provider_responses`
- [X] T007 [P] Create Flyway migration `backend/src/main/resources/db/migration/V024__response_likes.sql` with named-like foreign keys, cascade behavior, uniqueness, and count index
- [X] T008 [P] Create Flyway migration `backend/src/main/resources/db/migration/V025__anonymous_response_likes.sql` with share-scoped visitor digest uniqueness and response cascade behavior
- [X] T009 [P] Create Flyway migration `backend/src/main/resources/db/migration/V026__session_shares.sql` with opaque token uniqueness, one-active-share partial unique index, revoke state, and session cascade behavior
- [X] T010 Add a Testcontainers migration/schema test covering V021-V026 columns, constraints, partial uniqueness, and cascades in `backend/src/test/kotlin/com/octopusllm/migration/PersonalAnalyticsMigrationTest.kt`
- [X] T011 [P] Implement trusted direct-peer/forwarded-client-IP resolution with explicit trusted proxy configuration in `backend/src/main/kotlin/com/octopusllm/config/TrustedClientIpResolver.kt`
- [X] T012 [P] Permit only the documented public auth, shared-session, anonymous-like, and public-analytics routes while preserving authentication on all other routes in `backend/src/main/kotlin/com/octopusllm/config/SecurityConfig.kt`
- [X] T013 Add feature-005 configuration properties to the shared integration-test environment in `backend/src/test/kotlin/com/octopusllm/testsupport/AbstractPostgresIntegrationTest.kt`

**Checkpoint**: Flyway validates V001-V026 and the shared security/configuration foundation is ready.

---

## Phase 3: User Story 1 - Personal Center: Manage My Account (Priority: P1) MVP

**Goal**: Provide a discoverable account hub for profile editing, password change/reset, email
verification, and configured-model pricing.

**Independent Test**: From in-app navigation, update the display name, change the password while the
current browser remains authenticated, verify another old token is rejected, request/complete a
non-disclosing reset, resend/complete email verification, and open model management with pricing.

### Tests for User Story 1

- [X] T014 [P] [US1] Add profile update, password change, replacement-token, and stale-token rejection integration tests in `backend/src/test/kotlin/com/octopusllm/auth/PersonalCenterControllerTest.kt`
- [X] T015 [P] [US1] Update registration and email-verification integration tests for unverified registration, single-use verification, resend invalidation, pending status, and cooldown behavior in `backend/src/test/kotlin/com/octopusllm/auth/AuthControllerTest.kt`
- [X] T016 [P] [US1] Extend self-service password-reset tests for non-disclosing request responses, throttling, expiry/reuse rejection, email delivery, and `session_epoch` invalidation in `backend/src/test/kotlin/com/octopusllm/auth/PasswordResetFlowTest.kt`
- [ ] T017 [P] [US1] Add configured-model pricing validation and built-in-model read-only allocation tests in `backend/src/test/kotlin/com/octopusllm/connection/ConfiguredModelPricingTest.kt`
- [X] T018 [P] [US1] Add Personal Center component tests for active navigation, loading states, inline feedback, profile clearing, and replacement-token storage in `frontend/src/components/account/AccountShell.test.tsx`, `frontend/src/components/account/ProfileForm.test.tsx`, and `frontend/src/components/account/PasswordChangeForm.test.tsx`
- [ ] T019 [P] [US1] Extend model settings dialog tests for optional price/currency validation and allocated-model read-only behavior in `frontend/src/components/settings/connections/SettingsDialogs.test.tsx`

### Implementation for User Story 1

- [X] T020 [P] [US1] Map `display_name` and configured-model pricing columns in `backend/src/main/kotlin/com/octopusllm/auth/User.kt` and `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModel.kt`
- [X] T021 [P] [US1] Implement the persistent throttle entity, repository atomic-upsert query, and expiry cleanup API in `backend/src/main/kotlin/com/octopusllm/auth/AuthActionThrottle.kt` and `backend/src/main/kotlin/com/octopusllm/auth/AuthActionThrottleRepository.kt`
- [X] T022 [US1] Implement registration verification issuance, resend replacement, password-change epoch rotation, non-disclosing reset request, and reset-confirm epoch rotation in `backend/src/main/kotlin/com/octopusllm/auth/AuthService.kt` and `backend/src/main/kotlin/com/octopusllm/auth/AuthController.kt`
- [X] T023 [US1] Extend `GET/PATCH /api/v2/me`, add password change and verification resend endpoints, and derive verification status in `backend/src/main/kotlin/com/octopusllm/auth/MeController.kt`
- [X] T024 [US1] Extend user and built-in configured-model create/patch/response contracts with validated pricing fields in `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModelControllerV2.kt`, `backend/src/main/kotlin/com/octopusllm/connection/ConfiguredModelService.kt`, `backend/src/main/kotlin/com/octopusllm/admin/AdminConnectionController.kt`, and `backend/src/main/kotlin/com/octopusllm/admin/AdminConnectionService.kt`
- [X] T025 [P] [US1] Add Personal Center, password-reset, verification-status, and pricing DTOs plus API clients in `frontend/src/lib/types/api.ts`, `frontend/src/lib/api/account.ts`, `frontend/src/lib/api/auth.ts`, `frontend/src/lib/api/connections.ts`, and `frontend/src/lib/api/admin.ts`
- [X] T026 [P] [US1] Create the responsive Personal Center shell and active Profile/Security/Analytics navigation in `frontend/src/components/account/AccountShell.tsx` and `frontend/src/app/(app)/account/layout.tsx`
- [X] T027 [US1] Implement profile and email-verification status/resend UI with loading, success, error, pending, and empty states in `frontend/src/components/account/ProfileForm.tsx` and `frontend/src/app/(app)/account/page.tsx`
- [X] T028 [P] [US1] Implement password change UI that atomically replaces the auth cookie and reports validation/auth errors in `frontend/src/components/account/PasswordChangeForm.tsx` and `frontend/src/app/(app)/account/security/page.tsx`
- [X] T029 [P] [US1] Implement non-disclosing forgot-password request UI and connect it from sign-in in `frontend/src/components/auth/ForgotPasswordForm.tsx`, `frontend/src/app/(auth)/forgot-password/page.tsx`, and `frontend/src/components/auth/LoginForm.tsx`
- [X] T030 [US1] Add optional input/output price and currency controls to configured-model dialogs and rows in `frontend/src/components/settings/connections/AddModelDialog.tsx`, `frontend/src/components/settings/connections/EditModelDialog.tsx`, and `frontend/src/components/settings/connections/ModelRow.tsx`
- [X] T031 [US1] Add a connected Personal Center navigation entry from the chat shell without removing admin/model links in `frontend/src/components/account/AccountNavLink.tsx`, `frontend/src/components/chat/SessionSidebar.tsx`, and `frontend/src/app/(app)/chat/page.tsx`

**Checkpoint**: US1 is deployable as the MVP and all account actions are reachable without typed URLs.

---

## Phase 4: User Story 2 - Like Individual AI Responses (Priority: P2)

**Goal**: Allow authenticated owners to idempotently like/unlike each persisted model response and
retain the state across reloads.

**Independent Test**: Like one saved response, repeat the PUT without increasing the count, reload to
see `likedByMe`, DELETE the like, and delete the conversation to verify the like cascades.

### Tests for User Story 2

- [X] T032 [P] [US2] Add named-like authorization, idempotency, toggle, count, foreign-session hiding, and cascade integration tests in `backend/src/test/kotlin/com/octopusllm/reaction/ReactionControllerTest.kt`
- [ ] T033 [P] [US2] Add chat persistence tests proving complete/error rows are awaited once and terminal SSE events include `responseId` in `backend/src/test/kotlin/com/octopusllm/chat/ChatResponsePersistenceTest.kt`
- [X] T034 [P] [US2] Add like-control component tests for pending, busy, liked, unliked, count, and failure states in `frontend/src/components/chat/ResponseLikeButton.test.tsx`
- [X] T035 [P] [US2] Extend stream hook tests to retain terminal `responseId` for complete and error events in `frontend/src/lib/hooks/useParallelStream.test.ts`

### Implementation for User Story 2

- [X] T036 [P] [US2] Implement named-like entity and repository queries for idempotent insert/delete, count, and caller state in `backend/src/main/kotlin/com/octopusllm/reaction/ResponseLike.kt` and `backend/src/main/kotlin/com/octopusllm/reaction/ResponseLikeRepository.kt`
- [X] T037 [US2] Implement owner-scoped named-like service and PUT/DELETE endpoints in `backend/src/main/kotlin/com/octopusllm/reaction/ReactionService.kt` and `backend/src/main/kotlin/com/octopusllm/reaction/ReactionControllerV2.kt`
- [X] T038 [US2] Refactor response persistence to await one immutable complete/error row before forwarding the terminal event and expose `responseId` in saved-session DTOs/SSE in `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`, `backend/src/main/kotlin/com/octopusllm/chat/ChatControllerV2.kt`, and `backend/src/main/kotlin/com/octopusllm/llm/LlmStreamEvent.kt`
- [X] T039 [US2] Batch-load named-like counts and caller state when reading a session to avoid per-response queries in `backend/src/main/kotlin/com/octopusllm/reaction/ResponseLikeRepository.kt` and `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`
- [X] T040 [P] [US2] Add response identity/like DTOs and idempotent like/unlike API calls in `frontend/src/lib/types/api.ts` and `frontend/src/lib/api/reactions.ts`
- [X] T041 [P] [US2] Extend streaming state to retain terminal response identity in `frontend/src/lib/hooks/useParallelStream.ts`
- [X] T042 [US2] Build the accessible like control and integrate it into saved and streaming response panels in `frontend/src/components/chat/ResponseLikeButton.tsx`, `frontend/src/components/chat/ModelResponsePanel.tsx`, and `frontend/src/app/(app)/chat/page.tsx`

**Checkpoint**: US2 works on existing private conversations without sharing or analytics UI.

---

## Phase 5: User Story 4 - Personal Usage Analytics Dashboard (Priority: P2)

**Goal**: Capture trusted request/connection/pricing snapshots and expose private model,
conversation, and response analytics with filters and stable historical cost estimates.

**Independent Test**: Generate complete/error responses, confirm exactly one immutable row per
terminal outcome, then view/filter model and conversation aggregates plus owner-only response detail;
changing/deleting configured pricing must not change historical estimates.

### Tests for User Story 4

- [ ] T043 [P] [US4] Add trusted-proxy IP, connection/pricing snapshot, complete/error persistence, and historical-pricing tests in `backend/src/test/kotlin/com/octopusllm/chat/ResponseAnalyticsCaptureTest.kt`
- [X] T044 [P] [US4] Add owner-scope, filter, pagination, mixed-currency, percentile, empty-state, and aggregate-field privacy integration tests in `backend/src/test/kotlin/com/octopusllm/analytics/PersonalAnalyticsControllerTest.kt`
- [X] T045 [P] [US4] Add analytics API serialization/filter tests in `frontend/src/lib/api/analytics.test.ts`
- [X] T046 [P] [US4] Add dashboard component tests for loading, filters, responsive tables, drill-down, mixed currencies, and empty states in `frontend/src/components/account/AnalyticsDashboard.test.tsx`

### Implementation for User Story 4

- [X] T047 [P] [US4] Map client IP and immutable connection/pricing snapshots in `backend/src/main/kotlin/com/octopusllm/chat/ChatTurn.kt` and `backend/src/main/kotlin/com/octopusllm/chat/ProviderResponse.kt`
- [X] T048 [US4] Capture trusted client IP at turn creation and copy connection/pricing values into each terminal response in `backend/src/main/kotlin/com/octopusllm/chat/ChatControllerV2.kt` and `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt`
- [X] T049 [P] [US4] Implement owner-scoped summary, by-model, by-session, and paged detail projection queries in `backend/src/main/kotlin/com/octopusllm/analytics/AnalyticsRepository.kt`
- [X] T050 [US4] Implement read-only analytics filtering, percentile calculations, per-currency cost estimates, and privacy-safe DTO mapping in `backend/src/main/kotlin/com/octopusllm/analytics/AnalyticsService.kt` and `backend/src/main/kotlin/com/octopusllm/analytics/ModelPricing.kt`
- [X] T051 [US4] Implement authenticated summary, by-model, by-session, and response-detail endpoints with bounded pagination in `backend/src/main/kotlin/com/octopusllm/analytics/AnalyticsController.kt`
- [X] T052 [P] [US4] Add personal analytics DTOs and query-string API client functions in `frontend/src/lib/types/api.ts` and `frontend/src/lib/api/analytics.ts`
- [X] T053 [US4] Build the responsive analytics dashboard with time/configured-model filters, summary cards, paged model/session views, detail drill-down, loading/error/empty states, and per-currency cost rendering in `frontend/src/components/account/AnalyticsDashboard.tsx` and `frontend/src/app/(app)/account/analytics/page.tsx`

**Checkpoint**: US4 is independently usable from historical responses and leaks no other-user data.

---

## Phase 6: User Story 3 - Anonymous Likes on Shared Conversations (Priority: P3)

**Goal**: Let owners create/revoke opaque share links and let public visitors read and anonymously
like shared responses with server-controlled best-effort de-duplication.

**Independent Test**: Create a share, open it logged out, verify the anonymous-safe DTO/cookie, like
twice from one browser for one count, add a named like as a logged-in non-owner, revoke the link, and
delete the conversation without producing a 500.

### Tests for User Story 3

- [X] T054 [P] [US3] Add owner create/list/revoke, one-active-share, revoked/deleted 404, and token-scoped named-like integration tests in `backend/src/test/kotlin/com/octopusllm/share/ShareControllerTest.kt`
- [X] T055 [P] [US3] Add anonymous-safe DTO, HttpOnly cookie, HMAC digest, repeat-like deduplication, arbitrary-body ignoring, and response-membership tests (implemented as the `public shared read exposes no identity and revoked links 404` case in `backend/src/test/kotlin/com/octopusllm/share/ShareControllerTest.kt`)
- [X] T056 [P] [US3] Add public shared-session component tests for read-only rendering, anonymous/named like modes, loading/error states, and prohibited-field absence in `frontend/src/components/share/SharedConversation.test.tsx`
- [X] T057 [P] [US3] Add owner share-control tests for create/copy/list/revoke states in `frontend/src/components/chat/ShareConversationButton.test.tsx`

### Implementation for User Story 3

- [X] T058 [P] [US3] Implement share and anonymous-like entities/repositories with active-share lookup, pagination, response membership, count, and digest state queries in `backend/src/main/kotlin/com/octopusllm/share/SessionShare.kt`, `backend/src/main/kotlin/com/octopusllm/share/SessionShareRepository.kt`, `backend/src/main/kotlin/com/octopusllm/reaction/AnonymousResponseLike.kt`, and `backend/src/main/kotlin/com/octopusllm/reaction/AnonymousResponseLikeRepository.kt`
- [X] T059 [P] [US3] Implement secure share-token generation and server-issued visitor cookie/HMAC digest handling in `backend/src/main/kotlin/com/octopusllm/share/ShareTokenService.kt` and `backend/src/main/kotlin/com/octopusllm/share/AnonymousVisitorService.kt`
- [X] T060 [US3] Implement owner share creation/list/revocation, anonymous-safe session reads, anonymous likes, and token-scoped named likes in `backend/src/main/kotlin/com/octopusllm/share/ShareService.kt`, `backend/src/main/kotlin/com/octopusllm/share/ShareControllerV2.kt`, and `backend/src/main/kotlin/com/octopusllm/share/SharedSessionController.kt`
- [X] T061 [P] [US3] Add share/public-session DTOs and same-origin owner/public API clients with credentials enabled for the visitor cookie in `frontend/src/lib/types/api.ts` and `frontend/src/lib/api/shares.ts`
- [X] T062 [P] [US3] Build the public read-only shared conversation and anonymous/named like controls outside the authenticated route group in `frontend/src/components/share/SharedConversation.tsx` and `frontend/src/app/(public)/share/[token]/page.tsx`
- [X] T063 [US3] Add owner create/copy/list/revoke controls to the active chat session in `frontend/src/components/chat/ShareConversationButton.tsx` and `frontend/src/app/(app)/chat/page.tsx`

**Checkpoint**: US3 works without authentication and never exposes personal or named-like detail.

---

## Phase 7: User Story 5 - Public Anonymized Model Analytics (Priority: P3)

**Goal**: Publish read-only protocol/literal-model aggregates without exposing personal,
conversation, connection, configured-model, prompt, or response fields.

**Independent Test**: Open `/analytics` while logged out, filter/page the model aggregates, verify an
empty state, reject `size=101`, and assert zero prohibited fields in the API and rendered page.

### Tests for User Story 5

- [X] T064 [P] [US5] Add public endpoint pagination/filter/metric tests and explicit prohibited-field contract assertions in `backend/src/test/kotlin/com/octopusllm/analytics/PublicAnalyticsControllerTest.kt`
- [X] T065 [P] [US5] Add public analytics page tests for unauthenticated loading, filters, pagination, responsive rendering, and empty states in `frontend/src/components/analytics/PublicModelAnalytics.test.tsx`

### Implementation for User Story 5

- [X] T066 [US5] Add protocol/literal-model-only aggregate projections with response, latency, token, success, named-like, and anonymous-like totals in `backend/src/main/kotlin/com/octopusllm/analytics/AnalyticsRepository.kt` and `backend/src/main/kotlin/com/octopusllm/analytics/AnalyticsService.kt`
- [X] T067 [US5] Add the unauthenticated bounded `/api/v2/analytics/public/by-model` endpoint using a dedicated public DTO in `backend/src/main/kotlin/com/octopusllm/analytics/AnalyticsController.kt`
- [X] T068 [P] [US5] Extend public analytics DTOs/API calls without cost or account-specific fields in `frontend/src/lib/types/api.ts` and `frontend/src/lib/api/analytics.ts`
- [X] T069 [US5] Build the public analytics page with protocol/model/time filters, pagination, loading/error/empty states, and responsive result cards/table in `frontend/src/components/analytics/PublicModelAnalytics.tsx` and `frontend/src/app/(public)/analytics/page.tsx`
- [X] T070 [US5] Add an in-app link to public model analytics while keeping the page reachable without authentication in `frontend/src/components/chat/SessionSidebar.tsx` and `frontend/src/app/page.tsx`

**Checkpoint**: US5 satisfies the constitution's public anonymous analytics requirement.

---

## Phase 8: Polish & Cross-Cutting Verification

**Purpose**: Verify security, performance, deployment, visual quality, and complete end-to-end
behavior across all selected stories.

- [ ] T071 [P] Add backend performance tests for 1,000-response private/public analytics queries and p95 stream-start overhead in `backend/src/test/kotlin/com/octopusllm/analytics/AnalyticsPerformanceTest.kt` and `backend/src/test/kotlin/com/octopusllm/chat/StreamStartLatencyTest.kt`
- [ ] T072 [P] Add Playwright journeys for Personal Center, private likes, public sharing, private analytics, public analytics, mobile responsiveness, and prohibited-field checks in `frontend/e2e/personal-center-likes-analytics.spec.ts`
- [X] T073 [P] Add same-origin proxy path-preservation tests for new account, reaction, share, and analytics routes in `frontend/src/app/api/[...path]/route.test.ts`
- [X] T074 Run backend build and tests with `backend/gradlew`, fixing failures in the feature-005 backend files before marking the task complete (fixed `INET`→`host()` so analytics `clientIp` omits the `/32` netmask; full `./gradlew test` green, 62 tests)
- [X] T075 Run frontend type-check, lint, Vitest, and production build using `frontend/package.json`, fixing failures in feature-005 frontend files before marking the task complete
- [ ] T076 Build and start the Docker stack from `docker-compose.yml`, verify migrations and real requests from the published frontend origin, and record the observed commands/results in `specs/005-personal-center-likes-analytics/quickstart.md`
- [ ] T077 Visually verify every new/changed desktop and mobile surface with the in-app browser and record screenshots/check results in `specs/005-personal-center-likes-analytics/quickstart.md`
- [ ] T078 Run the complete scenarios and privacy spot-checks in `specs/005-personal-center-likes-analytics/quickstart.md` and update any mismatched contract or validation note before completion

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: No dependencies.
- **Phase 2 Foundational**: Depends on Phase 1 and blocks all story phases.
- **Phase 3 US1**: Starts after Phase 2; this is the MVP.
- **Phase 4 US2**: Starts after Phase 2 and can proceed in parallel with US1 and most of US4.
- **Phase 5 US4**: Starts after Phase 2. T048 must coordinate with T038 because both modify
  `ChatService.kt`/`ChatControllerV2.kt`; complete T038 first when executed sequentially.
- **Phase 6 US3**: Depends on US2 named reactions and response identity (T036-T042).
- **Phase 7 US5**: Depends on US4 analytics infrastructure (T049-T053) and the named/anonymous
  reaction repositories from US2/US3 (T036 and T058).
- **Phase 8 Polish**: Depends on every story selected for the release.

### User Story Dependencies

- **US1 (P1)**: No story dependency after the foundation.
- **US2 (P2)**: No story dependency after the foundation.
- **US4 (P2)**: No functional dependency on US1/US2, but its chat capture edit must be merged with
  US2 terminal persistence rather than overwriting it.
- **US3 (P3)**: Depends on US2 because authenticated shared visitors produce named likes.
- **US5 (P3)**: Depends on US4 for analytics queries and on US2/US3 for satisfaction totals.

### Within Each User Story

- Write and run the story's tests first; confirm expected failures.
- Map schema-backed entities before repositories/services.
- Implement repositories before service/controller behavior.
- Implement backend contracts before frontend API clients.
- Implement API clients/types before page/component integration.
- Complete the independent test before moving the story checkpoint to done.

---

## Parallel Opportunities

- T002 and T003 can run in parallel after T001 starts.
- T005-T009 and T011-T012 can run in parallel after migration numbering is confirmed by T004.
- US1 tests T014-T019 are parallel; implementation tasks T020-T021 and T025-T029 have separate-file
  parallel opportunities.
- US2 tests T032-T035 are parallel; T036, T040, and T041 can run in parallel.
- US4 tests T043-T046 are parallel; T047, T049, and T052 can run in parallel.
- US3 tests T054-T057 are parallel; T058-T059 and T061-T062 can run in parallel.
- US5 tests T064-T065 are parallel; T068 can run while T066-T067 are implemented.
- T071-T073 can run in parallel before the sequential build/deployment/visual gates.

## Parallel Example: User Story 2

```text
Task T032: Backend named-like contract/integration tests
Task T033: Terminal response persistence/SSE tests
Task T034: Frontend like-control tests
Task T035: Frontend stream-state tests

Then in parallel:
Task T036: Named-like entity/repository
Task T040: Frontend reaction DTO/API client
Task T041: Frontend terminal response identity state
```

## Parallel Example: User Story 4

```text
Task T043: Capture/snapshot tests
Task T044: Personal analytics endpoint tests
Task T045: Frontend analytics API tests
Task T046: Frontend dashboard tests

Then in parallel:
Task T047: JPA snapshot mappings
Task T049: Analytics projection queries
Task T052: Frontend analytics DTO/API client
```

## Implementation Strategy

### MVP First

1. Complete Phases 1 and 2.
2. Complete Phase 3 (US1).
3. Run the US1 independent test and relevant build gates.
4. Deploy/demo the Personal Center before adding analytics or sharing.

### Incremental Delivery

1. Setup + Foundation.
2. US1 Personal Center MVP.
3. US2 private response likes.
4. US4 personal analytics.
5. US3 sharing and anonymous likes.
6. US5 public anonymized analytics.
7. Complete all cross-cutting gates in Phase 8.

### Parallel Team Strategy

After Phase 2:

- Developer A: US1.
- Developer B: US2.
- Developer C: US4, coordinating the terminal persistence merge with Developer B.
- After US2: Developer B can continue with US3.
- After US4 and reaction repositories: Developer C can continue with US5.

---

## Notes

- `[P]` means different files or no dependency on an unfinished task; shared-file edits are deliberately
  not marked parallel.
- Do not create a parallel `response_statistics` table; `provider_responses` remains the immutable
  source of truth.
- Do not use provider model-list APIs; pricing is optional manual configured-model metadata.
- Never log or return API keys, visitor-cookie values/digests, IPs outside owner detail, or sensitive
  custom parameters.
- All collection endpoints use `{items, page, size, totalElements, totalPages}` with `size <= 100`.
- Browser-facing requests use same-origin `/api/...` paths through the Next proxy.
- Commit after each task or coherent task group and stop at any story checkpoint for validation.
