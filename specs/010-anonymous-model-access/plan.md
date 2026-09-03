# Implementation Plan: Anonymous Chat and Model Access Management

**Branch**: `010-anonymous-model-access` | **Date**: 2026-09-02 | **Spec**: [spec.md](/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/spec.md)
**Input**: Feature specification from `/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/spec.md`

## Summary

Add administrator-controlled anonymous-access and Guest-default flags to platform-owned configured models, expose a policy-filtered public model catalogue ordered with up to three configured defaults first, and provide an ephemeral anonymous chat stream that never creates a server-owned conversation. The browser will keep anonymous conversations locally and send the selected conversation context with each anonymous turn. After registration, the frontend will import local conversations into authenticated sessions through an idempotent, per-conversation transaction.

The existing authenticated session/chat path remains intact. Shared LLM dispatch logic will be extracted from the persistence-oriented chat service so authenticated and anonymous flows use the same provider-agnostic orchestration and streaming behavior without duplicating adapter or response-stream logic. A new administrator model-access view will provide filtered, paginated, cross-connection bulk operations for anonymous access, normal display state, and deletion.

## Technical Context

**Language/Version**: Kotlin 2.0.21 on JVM 21; TypeScript 5 with React 19 and Node.js 24
**Primary Dependencies**: Spring Boot 3.3.5 WebFlux, Spring Data JPA, Spring Security, Flyway, Jackson, jjwt; Next.js 16.2.7 App Router, Tailwind CSS v4, Vitest, Playwright
**Storage**: PostgreSQL 16 for configured-model policy, anonymous request leases, and synchronization identity; browser `localStorage` for anonymous conversations; existing encrypted connection-key storage remains authoritative
**Testing**: JUnit 5, Spring WebTestClient, existing PostgreSQL integration-test harness, Vitest/Testing Library, TypeScript strict checking, ESLint, Playwright E2E
**Target Platform**: Docker Compose and Kubernetes-compatible Linux deployment; desktop and mobile browsers using the published frontend origin
**Project Type**: Web application with a Kotlin REST/SSE backend and a Next.js browser frontend
**Performance Goals**: Public model catalogue p95 response under 500 ms excluding network startup; anonymous SSE headers within 1 second for a valid request; a bulk state operation over 100 models completes within 2 seconds at the service boundary; selected models continue to dispatch concurrently
**Constraints**: New browser-facing calls use the same-origin `/api/...` proxy and `/api/v2` contracts; collection pages accept at most 100 items; anonymous turns are text-only, do not accept attachments or server-side tools, and do not persist server-side conversation state; dedicated HMAC-keyed rate limits and expiring concurrency leases protect public execution; API keys and private connection fields never enter public payloads, logs, or migration records; all DDL uses a Flyway migration; no distributed locks
**Scale/Scope**: Hundreds to low thousands of administrator-controlled configured models across multiple built-in connections; bulk selection of at least 100 models; concurrent anonymous visitors; local conversations containing multiple turns and multiple model responses; authenticated users and existing share links must remain compatible

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Plan assessment |
|-----------|------|-----------------|
| I. Provider-Agnostic Abstraction | PASS | Anonymous and authenticated turns both use the existing protocol-keyed adapter registry and concurrent orchestrator. No provider-specific behavior is added to the public or admin layers. |
| II. API-First Design | PASS | Public model listing, anonymous streaming, sync import, and admin bulk operations are specified as REST/SSE `/api/v2` contracts before UI work. The current project rule requires model/chat APIs to remain on v2; no removed v1 routes are restored. |
| III. Concurrent Execution & Streaming | PASS | The shared runner preserves concurrent dispatch and forwards normalized events over SSE. No distributed lock or artificial serialization is introduced. |
| IV. Data Integrity & Immutable Sessions | PASS | Policy and import identity use a new Flyway migration. Anonymous turns are not server sessions; imported sessions and responses use existing append-only tables, with one transaction per local conversation. Existing columns retain their meaning. |
| V. Observability & Analytics | PASS | Anonymous provider calls emit the existing structured call metrics with an anonymous marker and no prompt or key material. Imported history is not counted as a new provider call. |
| VI. Security & User Key Privacy | PASS | The public catalogue is restricted to administrator-controlled built-in models and safe metadata. Public requests validate the allowlist server-side, enforce bounded text-only execution, and no anonymous route can access personal sessions or user-provided keys. |
| VII. Simplicity & Horizontal Scalability | PASS | The design uses stateless anonymous requests, database uniqueness for import idempotency, and eventual UI refresh. Rate counts and expiring concurrency slots use atomic database operations rather than JVM-local state or distributed locks; no queue or new inter-service protocol is added. |
| VIII. UX Consistency & Visual Coherence | PASS | The public chat and admin model-access surfaces reuse the existing chat/admin shells, shared controls, confirmation dialog, inline banners, responsive table treatment, and in-app navigation. |

**Gate result**: PASS. No constitution violation requires a Complexity Tracking exception. The v2 paths follow the repository's current API rule while preserving the constitution's requirement that public contracts be versioned.

## Project Structure

### Documentation (this feature)

```text
/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── admin-model-access.md
│   ├── anonymous-chat.md
│   └── anonymous-conversation-sync.md
└── tasks.md                         # created later by /speckit.tasks
```

### Source Code (repository root)

```text
/Users/gqq/OpenSource/octopus-llm/backend/src/main/kotlin/com/octopusllm/
├── admin/
│   ├── AdminConnectionController.kt       # existing; retain connection lifecycle
│   ├── AdminConnectionService.kt           # extend or delegate model bulk actions
│   ├── AdminModelAccessController.kt       # new aggregate model list/bulk contract
│   ├── AdminModelAccessService.kt          # new admin-controlled model operations
│   ├── AdminModelBulkOperation.kt          # frozen selection and per-item outcomes
│   ├── AdminModelBulkOperationRepository.kt
│   └── AdminAuditLog.kt                    # extend action/target enums
├── anonymous/
│   ├── AnonymousChatController.kt          # new public SSE endpoint
│   ├── AnonymousChatService.kt              # public validation and request context
│   ├── AnonymousConversationSyncController.kt
│   ├── AnonymousConversationSyncService.kt
│   ├── AnonymousConversationImport.kt      # import identity entity/repository
│   ├── AnonymousRequestLease.kt            # expiring per-client concurrency slots
│   ├── AnonymousRequestLeaseRepository.kt
│   └── AnonymousThrottleService.kt          # HMAC-keyed rate/payload/concurrency guard
├── chat/
│   ├── ChatService.kt                      # delegate common execution to runner
│   └── LlmTurnRunner.kt                    # extracted provider-agnostic stream runner
├── connection/
│   ├── ConfiguredModel.kt                   # new is_anonymous_allowed field
│   ├── ConfiguredModelRepository.kt         # public/admin query methods
│   └── ConfiguredModelService.kt            # public safe-model lookup
└── ...                                      # existing bounded-context support classes

/Users/gqq/OpenSource/octopus-llm/backend/src/main/resources/db/migration/
└── V041__anonymous_model_access.sql

/Users/gqq/OpenSource/octopus-llm/backend/src/test/kotlin/com/octopusllm/
├── admin/AdminModelAccessControllerTest.kt
├── anonymous/AnonymousChatControllerTest.kt
├── anonymous/AnonymousConversationSyncServiceTest.kt
├── migration/AnonymousModelAccessMigrationTest.kt
└── ... existing regression suites

/Users/gqq/OpenSource/octopus-llm/frontend/src/
├── app/chat/page.tsx                       # public-compatible route; replaces auth-layout-bound chat route
├── app/chat/loading.tsx
├── app/(app)/admin/models/page.tsx          # new connected admin route
├── components/admin/AdminModelAccessPage.tsx
├── components/chat/AnonymousChatNotice.tsx # public empty/revocation/limit states
├── components/chat/ChatPage.tsx             # shared authenticated/public chat surface
├── lib/api/anonymousChat.ts
├── lib/api/adminModelAccess.ts
├── lib/api/anonymousConversationSync.ts
├── lib/hooks/useAnonymousConversations.ts
└── lib/utils/anonymousConversationStorage.ts

/Users/gqq/OpenSource/octopus-llm/frontend/src/test/
└── ... existing Vitest and Playwright suites extended for public chat/admin flows
```

**Structure Decision**: Keep the existing backend bounded-context packages and add a small `anonymous` package for public chat and registration import. Extend `connection` for the model policy and `admin` for aggregate model management. Extract only the provider-agnostic execution seam required by both chat modes. Move the route entry for chat outside the authentication-enforcing `(app)` layout while keeping account/admin pages protected; use the existing same-origin Next proxy for all browser API calls.

## Implementation Phases

### Phase 0 — Research and decisions

1. Confirm the current auth boundary, configured-model ownership rules, provider dispatch seam, migration conventions, audit constraints, and frontend route/proxy behavior.
2. Resolve the design decisions recorded in [research.md](/Users/gqq/OpenSource/octopus-llm/specs/010-anonymous-model-access/research.md), especially ephemeral anonymous execution, policy storage, and idempotent import identity.

### Phase 1 — Data model and backend contracts

1. Add `is_anonymous_allowed BOOLEAN NOT NULL DEFAULT FALSE` to `configured_models` in `V041__anonymous_model_access.sql`, plus an index supporting public filtering and ordered admin results. Add the `is_anonymous_default BOOLEAN NOT NULL DEFAULT FALSE` flag and default-row index in `V042__anonymous_default_models.sql`; the service permits at most three enabled, anonymous-allowed built-in defaults. Add `anonymous_request_leases`, `anonymous_conversation_imports`, `admin_model_bulk_operations`, and `admin_model_bulk_operation_items` in V041, and extend audit action/target constraints without changing existing column meaning. The import table uses `(user_id, source_conversation_id)` and unique imported `session_id`; lease claims recover by expiry. None of these tables store conversation text, API keys, or private connection settings.
2. Extend configured-model queries and DTOs so only built-in, administrator-controlled, enabled, anonymous-approved models enter the public catalogue. Keep user-owned/BYOK models excluded even when authenticated model listing includes them, and expose only safe metadata.
3. Add public safe-model and anonymous SSE contracts. Validate every selected UUID against the current policy immediately before provider dispatch; accept only bounded text prompts and user/assistant history, reject attachments/tools, and apply `AnonymousThrottleService` for HMAC-keyed rate, concurrent-stream, prompt/history, model-count, and execution-duration limits.
4. Extract common target construction, request normalization, capability handling, concurrent orchestration, and normalized stream events into `LlmTurnRunner`. Keep authenticated persistence callbacks and tool/media behavior in the authenticated path; anonymous execution emits metrics but does not save turns/responses.
5. Add an authenticated sync endpoint and service. Import each local conversation in its own transaction, inserting the authenticated session, immutable turns, and response snapshots before marking the unique import identity successful. Return `imported`, `already_imported`, `skipped`, and `failed` item results; reject a reused source ID with a different digest.
6. Add the aggregate admin model list and bulk action contract. Support direct UUID selection or a preview-frozen server-evaluated filter, enforce a 100-item page and a bounded maximum operation size, apply each item idempotently, preserve historical responses on configured-model deletion, and record a safe operation summary plus per-item outcomes.

### Phase 2 — Frontend and user flows

1. Make `/chat` reachable without the auth-only `(app)` layout and let the shared chat surface choose authenticated server sessions or anonymous browser conversations based on the current token. Keep account history, media, tools, and sharing behind authentication.
2. Implement local conversation storage with a versioned envelope, stable conversation/turn identifiers, bounded serialization, corruption/quota handling, and atomic replacement after a completed update. Keep share controls and authenticated-only session actions absent in anonymous mode.
3. Add the public model API client and anonymous SSE client. Persist prompt/response state as events arrive, filter stale model IDs after policy refresh, and show inline empty/error/revocation states.
4. Call conversation synchronization after the existing registration-to-login sequence. Clear only confirmed imports; retain failed items and show a retryable status without blocking access to the newly authenticated chat.
5. Add the connected `/admin/models` page. Reuse `AdminGuard`, `AdminShell`, shared buttons/modal/confirmation primitives, responsive table behavior, URL-backed filter/page state where appropriate, and visible busy/success/partial-failure banners.
6. Add the admin navigation entry and ensure the route is reachable from all related admin sections and back to chat.

### Phase 3 — Verification and hardening

1. Add backend unit/integration tests for policy filtering, anonymous authorization, no persistence, rate/concurrency/payload guards, concurrent multi-model streaming, sync idempotency, digest conflict, partial retry, delete-history preservation, audit entries, and migration shape.
2. Add frontend tests for local storage recovery/quota failure, public model filtering, anonymous stream persistence, no-share rendering, registration sync retry, and all bulk action confirmations/results.
3. Add Playwright coverage for an unauthenticated visitor, registration migration, and an administrator managing a multi-page model result set.
4. Run backend build, frontend type-check/lint/unit tests, production frontend build, and the focused browser flows from the published frontend origin. Verify proxy paths and both `localhost`/`127.0.0.1` local origins where configured.

## Complexity Tracking

No constitution violations are planned. Two deliberate cross-cutting pieces are explicitly bounded: `LlmTurnRunner` prevents anonymous and authenticated chat from implementing separate provider dispatch paths; `AnonymousThrottleService` reuses the existing HMAC/IP and atomic-throttle patterns, with expiring database slots for horizontally safe concurrency protection. Neither adds an inter-service protocol or distributed lock, and no separate quota-management UI is introduced.
