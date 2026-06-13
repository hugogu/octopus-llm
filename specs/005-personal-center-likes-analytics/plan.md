# Implementation Plan: Personal Center, Response Likes & Usage Analytics

**Branch**: `005-personal-center-likes-analytics` | **Date**: 2026-06-13 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/005-personal-center-likes-analytics/spec.md`

## Summary

Deliver four connected capabilities on top of the existing Octopus LLM stack:

1. **Personal Center** — a discoverable in-app hub (mirroring `AdminShell`) that surfaces profile
   editing (new `display_name`), authenticated password change using the existing `session_epoch`,
   public self-service password reset, email-verification status + resend, and model management with
   optional configured-model pricing.
2. **Response likes** — per-response (per `provider_response`) named likes for registered users
   (idempotent, toggleable) plus anonymous like counts on shared sessions, with opaque, revocable
   share links that expose no identity.
3. **Personal usage analytics** — read-only, user-scoped aggregations over the *existing immutable*
   `provider_responses` table, extended with client IP, connection snapshots, and pricing snapshots.
4. **Public model analytics** — unauthenticated aggregates grouped only by protocol + literal model ID,
   with no personal, conversation, connection, configured-model, prompt, or response fields.

Technical approach: extend existing packages (`auth`, `chat`, `connection`) and add three small backend
packages (`reaction`, `share`, `analytics`). Authenticated account operations use `/api/v2/me/*`; the
existing public auth namespace remains `/api/v1/auth/*`; chat, sharing, reactions, and analytics use
`/api/v2`. The frontend adds an authenticated `(app)/account` hub plus public `(public)/share/[token]`
and `(public)/analytics` routes. Browser calls remain same-origin through the existing Next proxy, and
rewritten paths are verified with real HTTP requests. No write-path duplication: `provider_responses`
remains the single immutable per-response analytics record (Constitution IV/V/VII).

## Technical Context

**Language/Version**: Kotlin on JVM, Java 21 (backend); TypeScript 5 / Node.js 24 (frontend)
**Primary Dependencies**: Spring Boot WebFlux, Spring Security (reactive), Spring Data JPA/Hibernate, Flyway, jjwt, hypersistence-utils (JSONB); Next.js App Router (React Server Components), Tailwind
**Storage**: PostgreSQL (Flyway migrations; existing `users`, `chat_sessions`, `chat_turns`, `provider_responses`, `configured_models`, `connections`, `email_verifications`, `password_resets`, `revoked_tokens`)
**Testing**: JUnit 5 + Testcontainers (Postgres) + MockK/springmockk (backend); Vitest (frontend)
**Target Platform**: Linux server (Docker Compose); modern browsers
**Project Type**: Web application (Kotlin backend + Next.js frontend)
**Performance Goals**: Analytics dashboard renders per-model/per-conversation breakdowns over ≥1,000 responses in <2s; statistics capture adds no user-perceptible latency to response streaming (off hot path)
**Constraints**: No distributed locks; analytics read-only and off the hot write path; `provider_responses` immutable/append-only; share tokens opaque with zero identity; IP and account-specific dimensions never in aggregate payloads; collection APIs use the standard page envelope with size ≤100; snake_case schema via Flyway only
**Scale/Scope**: Personal and public analytics over append-only history; 6 new migrations, 3 new backend packages + 3 extended, ~7 new frontend routes/views + 1 like control

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance |
|-----------|-----------|
| I. Provider-Agnostic Abstraction | ✅ No provider-specific logic touched; no changes to `LLMProvider` adapters. |
| II. API-First Design | ✅ Every capability is exposed via REST first (`/api/v1/auth/password-reset/*`, `/api/v2/me/*`, `/api/v2/responses/*`, `/api/v2/chat/sessions/*/shares`, `/api/v2/shared/{token}`, `/api/v2/analytics/*`); collection responses use the standard page envelope and size ≤100; the frontend consumes same-origin proxy routes with no direct DB access. |
| III. Concurrent Execution & Streaming | ✅ Provider calls and token streaming remain concurrent. Terminal response persistence is awaited before the matching complete/error SSE event, guaranteeing the single immutable record without delaying stream start or adding a second write path. |
| IV. Data Integrity & Immutable Sessions | ✅ `provider_responses` stays INSERT-once/immutable; likes, anonymous likes, and shares live in **separate** tables referencing it. All schema changes via Flyway, snake_case. |
| V. Observability & Analytics | ✅ Analytics queries are read-only and off the hot write path. The feature provides both owner-scoped analytics and public anonymized model aggregates. IP and identity appear only in owner-visible detail and are excluded from all aggregate payloads. |
| VI. Security & User Key Privacy | ✅ Share links use opaque tokens with no identity; anonymous view exposes no liker identity; IP is owner-detail only; password change/reset invalidates prior credentials; auth is required except public auth actions, shared read/anonymous like, and public anonymous analytics. No API-key handling changes. |
| VII. Simplicity & Horizontal Scalability | ✅ Reuses the existing immutable table instead of a duplicated statistics table; anonymous-like dedup uses a server-issued browser cookie plus a UNIQUE constraint; password change reuses the existing `session_epoch` mechanism; no distributed lock or in-memory coordination is introduced. |
| VIII. UX Consistency & Visual Coherence | ✅ New `AccountShell` mirrors `AdminShell`/`ModelsSettingsPage` design system; connected nav entry from chat; all surfaces visually verified before done. |

**Result**: PASS — no violations. Complexity Tracking not required.

## Project Structure

### Documentation (this feature)

```text
specs/005-personal-center-likes-analytics/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (REST endpoint contracts)
│   ├── personal-center.md
│   ├── reactions.md
│   ├── sharing.md
│   └── analytics.md
├── checklists/
│   └── requirements.md  # Spec quality checklist (already present)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
backend/src/main/kotlin/com/octopusllm/
├── auth/                        # EXTEND: profile, password change/reset, email verification,
│   │                            #         persistent auth-action throttling; reuse session_epoch
│   ├── AuthController.kt         # + public password-reset request; existing confirm reused
│   ├── AuthService.kt            # + requestPasswordReset(), changePassword(), resendVerification()
│   ├── MeController.kt           # + profile, password-change, verification status/resend
│   ├── AuthActionThrottle.kt / AuthActionThrottleRepository.kt
│   │                              # persistent cross-instance auth action throttling
│   └── User.kt                   # + displayName; existing sessionEpoch reused
├── chat/
│   ├── ChatService.kt            # EXTEND: trusted IP/snapshots; await persistence before terminal SSE
│   ├── ChatControllerV2.kt       # EXTEND: pass ServerWebExchange for client IP; expose responseId
│   └── ProviderResponse.kt       # + connectionId and pricing snapshots
├── reaction/                     # NEW: named + anonymous likes
│   ├── ResponseLike.kt / ResponseLikeRepository.kt
│   ├── AnonymousResponseLike.kt / AnonymousResponseLikeRepository.kt # stores scoped HMAC digest
│   ├── ReactionService.kt
│   └── ReactionControllerV2.kt   # /api/v2/.../reactions (authenticated)
├── share/                        # NEW: session share links + public read
│   ├── SessionShare.kt / SessionShareRepository.kt
│   ├── ShareService.kt
│   ├── ShareControllerV2.kt      # paged /api/v2/chat/sessions/{id}/shares (owner)
│   └── SharedSessionController.kt# /api/v2/shared/{token}; issues HttpOnly visitor cookie
├── analytics/                    # NEW: read-only aggregations
│   ├── AnalyticsService.kt       # personal aggregates/detail + public anonymized aggregates
│   ├── AnalyticsController.kt    # /api/v2/analytics/*
│   └── ModelPricing.kt           # read-time cost = tokens × response pricing snapshot
└── connection/                   # EXTEND: optional prices on ConfiguredModel/API

backend/src/main/resources/db/migration/
├── V021__user_profile_and_auth_throttles.sql          # users.display_name + auth_action_throttles
├── V022__configured_model_pricing.sql                 # configured_models: input/output price, currency
├── V023__response_analytics_snapshots.sql              # trusted IP; connection + pricing snapshots
├── V024__response_likes.sql                           # named likes (UNIQUE user+response)
├── V025__anonymous_response_likes.sql                 # anonymous likes (UNIQUE digest+response)
└── V026__session_shares.sql                           # opaque token, revocable, no expiry

frontend/src/
├── app/(app)/account/
│   ├── layout.tsx                # AccountShell wrapper (connected nav: Profile / Security / Analytics)
│   ├── page.tsx                  # Profile (display name, email + verification status/resend)
│   ├── security/page.tsx         # Change password
│   └── analytics/page.tsx        # Personal usage analytics dashboard
├── app/(public)/share/[token]/page.tsx # public read-only shared session + anonymous likes
├── app/(public)/analytics/page.tsx     # public anonymized model analytics
├── app/(auth)/forgot-password/page.tsx # non-disclosing self-service reset request
├── components/account/AccountShell.tsx
├── components/chat/ModelResponsePanel.tsx   # EXTEND: like button + count
└── lib/api/
    ├── account.ts                # profile, change-password, resend-verification
    ├── auth.ts                   # EXTEND: password-reset request
    ├── reactions.ts              # like/unlike
    ├── shares.ts                 # create/revoke share, fetch shared session, anonymous like
    └── analytics.ts              # personal + public dashboard data
```

**Structure Decision**: Web application (Option 2). Reuse the existing `backend/` (Kotlin/Spring
WebFlux) and `frontend/` (Next.js App Router) trees. Backend follows the established
package-per-feature convention (`admin`, `auth`, `chat`, `connection`, with `analytics` new). Frontend
follows the existing `(app)` route-group + shared-shell pattern (`AccountShell` mirrors `AdminShell`),
and uses a separate public route group so shared/public analytics pages do not inherit the authenticated
`(app)` layout.

## Complexity Tracking

> No Constitution Check violations — this section is intentionally empty.
