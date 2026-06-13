# Implementation Plan: Personal Center, Response Likes & Usage Analytics

**Branch**: `005-personal-center-likes-analytics` | **Date**: 2026-06-13 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/005-personal-center-likes-analytics/spec.md`

## Summary

Deliver three connected, user-scoped capabilities on top of the existing Octopus LLM stack:

1. **Personal Center** — a discoverable in-app hub (mirroring `AdminShell`) that surfaces profile
   editing (new `display_name`), an authenticated *change-password* flow that invalidates all other
   sessions, email-verification status + resend, and a link into model management.
2. **Response likes** — per-response (per `provider_response`) named likes for registered users
   (idempotent, toggleable) plus anonymous like counts on shared sessions, with opaque, revocable
   share links that expose no identity.
3. **Usage analytics dashboard** — read-only, user-scoped aggregations over the *existing immutable*
   `provider_responses` table, extended with the two missing dimensions (client IP, snapshot
   `connection_id`) and a read-time **cost** estimate derived from new nullable pricing columns on
   `model_definitions`.

Technical approach: extend existing packages (`auth`, `chat`) and add three small backend packages
(`reaction`, `share`, `analytics`); all new surfaces go through `/api/v1` (auth) and `/api/v2`
(everything else). The frontend adds an `(app)/account` hub and a public `(app)/share/[token]` route,
plus a like control inside `ModelResponsePanel`. No write-path duplication: `provider_responses`
remains the single immutable per-response analytics record (Constitution IV/V/VII).

## Technical Context

**Language/Version**: Kotlin on JVM, Java 21 (backend); TypeScript 5 / Node.js 24 (frontend)
**Primary Dependencies**: Spring Boot WebFlux, Spring Security (reactive), Spring Data JPA/Hibernate, Flyway, jjwt, hypersistence-utils (JSONB); Next.js App Router (React Server Components), Tailwind
**Storage**: PostgreSQL (Flyway migrations; existing `users`, `chat_sessions`, `chat_turns`, `provider_responses`, `model_definitions`, `connections`, `email_verifications`, `revoked_tokens`)
**Testing**: JUnit 5 + Testcontainers (Postgres) + MockK/springmockk (backend); Vitest (frontend)
**Target Platform**: Linux server (Docker Compose); modern browsers
**Project Type**: Web application (Kotlin backend + Next.js frontend)
**Performance Goals**: Analytics dashboard renders per-model/per-conversation breakdowns over ≥1,000 responses in <2s; statistics capture adds no user-perceptible latency to response streaming (off hot path)
**Constraints**: No distributed locks; analytics read-only and off the hot write path; `provider_responses` immutable/append-only; share tokens opaque with zero identity; IP never in aggregate views; snake_case schema via Flyway only
**Scale/Scope**: Single-user-scoped analytics over append-only history; ~6 new migrations, 3 new backend packages + 2 extended, ~4 new frontend routes/views + 1 like control

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance |
|-----------|-----------|
| I. Provider-Agnostic Abstraction | ✅ No provider-specific logic touched; no changes to `LLMProvider` adapters. |
| II. API-First Design | ✅ Every capability exposed via REST first (`/api/v1/auth/*`, `/api/v2/me`, `/api/v2/.../reactions`, `/api/v2/shares`, `/api/v2/shared/{token}`, `/api/v2/analytics/*`); frontend consumes the same endpoints — no server-side DB access from Next.js. |
| III. Concurrent Execution & Streaming | ✅ Chat streaming path unchanged. Likes/shares/analytics are separate request flows; statistics capture (IP/connection snapshot) is recorded on the already-persisted turn/response, not in the streaming hot path. |
| IV. Data Integrity & Immutable Sessions | ✅ `provider_responses` stays INSERT-once/immutable; likes, anonymous likes, and shares live in **separate** tables referencing it. All schema changes via Flyway, snake_case. |
| V. Observability & Analytics | ✅ Analytics queries are read-only, user-scoped, and off the hot write path. IP + identity appear only in owner-visible views; no aggregate/cross-user surface is built (and IP is excluded by construction). |
| VI. Security & User Key Privacy | ✅ Share links use opaque tokens with no identity; anonymous view exposes no liker identity; IP visible only to the owner; password change invalidates all other sessions; auth required everywhere except the explicit anonymous shared read + anonymous like. No API-key handling changes. |
| VII. Simplicity & Horizontal Scalability | ✅ Reuses existing immutable table instead of a duplicated statistics table; anonymous-like dedup via a UNIQUE constraint (eventual, lock-free); bulk session invalidation via a per-user `sessions_valid_from` timestamp (no jti enumeration, no distributed lock). |
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
├── auth/                        # EXTEND: change-password, resend-verification, display_name,
│   │                            #         sessions_valid_from enforcement in JwtTokenService
│   ├── AuthController.kt         # + password-change, verify-email/resend
│   ├── AuthService.kt            # + changePassword(), resendVerification()
│   ├── MeController.kt           # + emailVerified, displayName; PATCH profile
│   ├── JwtTokenService.kt        # + reject tokens with iat < user.sessions_valid_from
│   └── User.kt                   # + displayName, sessionsValidFrom
├── chat/
│   ├── ChatService.kt            # EXTEND: capture client IP onto the turn; snapshot connection_id
│   ├── ChatControllerV2.kt       # EXTEND: pass ServerWebExchange for client IP; expose responseId
│   └── ProviderResponse.kt       # + connectionId (snapshot, nullable)
├── reaction/                     # NEW: named + anonymous likes
│   ├── ResponseLike.kt / ResponseLikeRepository.kt
│   ├── AnonymousResponseLike.kt / AnonymousResponseLikeRepository.kt
│   ├── ReactionService.kt
│   └── ReactionControllerV2.kt   # /api/v2/.../reactions (authenticated)
├── share/                        # NEW: session share links + public read
│   ├── SessionShare.kt / SessionShareRepository.kt
│   ├── ShareService.kt
│   ├── ShareControllerV2.kt      # /api/v2/chat/sessions/{id}/shares (owner)
│   └── SharedSessionController.kt# /api/v2/shared/{token} (+ anonymous like) — public
├── analytics/                    # NEW: read-only aggregations
│   ├── AnalyticsService.kt       # aggregate by model / by session; per-response detail
│   ├── AnalyticsController.kt    # /api/v2/analytics/*
│   └── ModelPricing.kt           # read-time cost = tokens × model_definitions pricing
└── model/                        # EXTEND: pricing columns on ModelDefinition

backend/src/main/resources/db/migration/
├── V021__user_profile_and_session_invalidation.sql   # users: display_name, sessions_valid_from
├── V022__model_pricing.sql                            # model_definitions: input/output price, currency
├── V023__response_ip_and_connection_snapshot.sql      # chat_turns.client_ip; provider_responses.connection_id
├── V024__response_likes.sql                           # named likes (UNIQUE user+response)
├── V025__anonymous_response_likes.sql                 # anonymous likes (UNIQUE token+response)
└── V026__session_shares.sql                           # opaque token, revocable, no expiry

frontend/src/
├── app/(app)/account/
│   ├── layout.tsx                # AccountShell wrapper (connected nav: Profile / Security / Analytics)
│   ├── page.tsx                  # Profile (display name, email + verification status/resend)
│   ├── security/page.tsx         # Change password
│   └── analytics/page.tsx        # Usage analytics dashboard
├── app/(app)/share/[token]/page.tsx   # public read-only shared session + anonymous likes
├── components/account/AccountShell.tsx
├── components/chat/ModelResponsePanel.tsx   # EXTEND: like button + count
└── lib/api/
    ├── account.ts                # profile, change-password, resend-verification
    ├── reactions.ts              # like/unlike
    ├── shares.ts                 # create/revoke share, fetch shared session, anonymous like
    └── analytics.ts              # dashboard data
```

**Structure Decision**: Web application (Option 2). Reuse the existing `backend/` (Kotlin/Spring
WebFlux) and `frontend/` (Next.js App Router) trees. Backend follows the established
package-per-feature convention (`admin`, `auth`, `chat`, `connection`, `analytics` new). Frontend
follows the existing `(app)` route-group + shared-shell pattern (`AccountShell` mirrors `AdminShell`),
and a public route group entry for the shareable view.

## Complexity Tracking

> No Constitution Check violations — this section is intentionally empty.
