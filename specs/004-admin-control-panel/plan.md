# Implementation Plan: Admin Control Panel

**Branch**: `004-admin-control-panel` | **Date**: 2026-06-13 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/004-admin-control-panel/spec.md`

## Summary

Add an administrator role and control panel that lets administrators (1) manage account lifecycle — list/search accounts, activate them, disable/re-enable them, and trigger password resets — and (2) manage platform-owned "built-in" connections (protocol + endpoint + admin-supplied encrypted key) and allocate them read-only to specific activated accounts for chat. Regular BYOK self-service stays fully available to any registered, verified, non-disabled account regardless of activation.

Technical approach reuses the feature-003 connection/configured-model model rather than duplicating it: a built-in connection is a `connections` row owned by an admin with `is_builtin = true`; allocation is a `connection_allocations` join table; chat model resolution is widened from "owned by user" to "owned by user OR on a built-in connection allocated to the user". Account state adds `is_admin`, `is_active`, `is_disabled`, and an integer `session_epoch` column to `users`; disable and password-reset both increment `session_epoch` (carried as a JWT claim) to revoke all outstanding tokens without enumerating them and without any timestamp-precision dependency (no distributed lock). Login also rejects disabled accounts before issuing a token, and the last-usable-admin invariant (covering disable, demote, and password-reset) is enforced by a conditional UPDATE / `SERIALIZABLE` transaction so concurrent requests cannot drive the admin count to zero. The initial admin is seeded at startup from configuration.

## Technical Context

**Language/Version**: Kotlin on JVM, Java 21 (backend); TypeScript 5 / Node.js 24 (frontend)
**Primary Dependencies**: Spring Boot WebFlux, Spring Security (reactive), Spring Data JPA/Hibernate, Flyway, jjwt; Next.js App Router, React
**Storage**: PostgreSQL (Flyway migrations; existing `users`, `connections`, `configured_models`, `email_verifications`, `revoked_tokens`)
**Testing**: JUnit 5 + Spring Boot Test (backend, `./gradlew build`); Vitest + Playwright (frontend, `npx tsc --noEmit`)
**Target Platform**: Linux server (Docker Compose), ARM64 dev / AMD64 prod
**Project Type**: Web application (backend service + Next.js frontend)
**Performance Goals**: User-list first page < 1s at ≥10k accounts; disable takes effect within one request cycle (SC-002)
**Constraints**: API keys encrypted at rest, never returned/logged (Constitution VI); no distributed locks in auth hot path (VII); all schema via Flyway (IV); API-first, all behavior under `/api/v2/admin/**` consumed by the same frontend (II)
**Scale/Scope**: Single-server / horizontally scalable; admin user base small, regular accounts up to tens of thousands

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Provider-Agnostic Abstraction | PASS — built-in connections reuse the existing `ProtocolAdapterRegistry`; no provider-specific branching is added. |
| II. API-First Design | PASS — all admin capability is exposed under versioned `/api/v2/admin/**` and consumed by the frontend through the existing proxy; no server-side shortcuts. |
| III. Concurrent Execution & Streaming | PASS — chat dispatch is unchanged; only model-resolution scope is widened. No new serialization. |
| IV. Data Integrity & Immutable Sessions | PASS — schema change ships as Flyway `V019`; `ProviderResponse` snapshots already preserve model/protocol/label so deleting a built-in connection leaves history intact (FR-021). |
| V. Observability & Analytics | PASS — admin actions emit an `admin_audit_log` row; existing `llm_call` structured logs are unchanged and never include keys. |
| VI. Security & User Key Privacy | PASS — built-in keys reuse `ApiKeyEncryptionService` (AES-256-GCM), excluded from all DTOs; admin endpoints gated by `ROLE_ADMIN`; password reset never reveals or sets a known password. |
| VII. Simplicity & Horizontal Scalability | PASS — reuses existing tables/services instead of a parallel built-in stack; session revocation via a timestamp column avoids distributed locks and cross-instance state. One extra per-request user lookup is justified below. |

**Complexity justification**: Per-request user load in the security filter (to enforce immediate disable + admin authority + session epoch) adds one indexed primary-key read per authenticated request. This is required by SC-002 (disable within one request cycle) and cannot be met by the existing per-JTI revocation alone. No new service layer or inter-service pattern is introduced, so no Complexity Tracking table is required.

## Project Structure

### Documentation (this feature)

```text
specs/004-admin-control-panel/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── admin-users.md
│   ├── admin-builtin-connections.md
│   └── auth-and-listing-changes.md
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
backend/src/main/kotlin/com/octopusllm/
├── admin/                         # NEW feature package
│   ├── AdminUserController.kt     # /api/v2/admin/users
│   ├── AdminUserService.kt        # activate / disable / enable / reset-password / list
│   ├── AdminConnectionController.kt   # /api/v2/admin/connections (+ models, allocate)
│   ├── AdminConnectionService.kt
│   ├── ConnectionAllocation.kt + ConnectionAllocationRepository.kt
│   ├── AdminAuditLog.kt + AdminAuditLogRepository.kt + AdminAuditService.kt
│   └── AdminBootstrap.kt          # seed initial admin from config at startup
├── auth/
│   ├── User.kt                    # + isAdmin, isActive, isDisabled, sessionEpoch
│   ├── UserRepository.kt          # + search/paging, countByIsAdminTrueAndIsDisabledFalse, conditional disable update
│   ├── JwtTokenService.kt         # issue/validate carry session_epoch claim
│   ├── AuthService.kt             # login rejects disabled; admin reset (epoch++, last-admin guard); atomic confirm
│   ├── AuthController.kt          # + public POST /api/v1/auth/password-reset/confirm
│   ├── MeController.kt            # NEW: GET /api/v2/me (id, email, isAdmin, isActive)
│   ├── PasswordReset.kt + PasswordResetRepository.kt   # NEW (atomic single-use consume)
│   └── EmailService.kt            # + sendPasswordResetEmail
├── config/
│   └── SecurityConfig.kt          # per-request user load → ROLE_ADMIN, disabled/epoch check, /admin/** gate
├── connection/
│   ├── Connection.kt              # + isBuiltin
│   ├── ConnectionRepository.kt    # built-in + allocation-aware queries
│   ├── ConfiguredModelService.kt  # requireSelectable (owned ∪ allocated built-in)
│   └── ConnectionService.kt       # list now includes allocated built-in (read-only)
└── chat/ChatService.kt            # use requireSelectable instead of requireOwned

backend/src/main/resources/db/migration/
└── V019__admin_control_panel.sql  # users columns, connections.is_builtin,
                                    # connection_allocations, password_resets, admin_audit_log

frontend/src/
├── app/(app)/admin/               # NEW admin route group (users + built-in connections), guarded by /me isAdmin
├── app/(auth)/reset-password/     # NEW public password-reset completion page
├── components/admin/              # NEW admin UI components + admin nav entry (shown only when isAdmin)
├── components/settings/connections/  # mark built-in connections read-only
├── components/chat/               # model selector shows allocated built-in models
└── lib/api/admin.ts               # NEW admin API client (incl. me()); connections.ts gains built-in flags
```

**Structure Decision**: Web-application layout (existing `backend/` Spring service + `frontend/` Next.js). A new `com.octopusllm.admin` backend package owns all admin-only logic; account-state and built-in-connection concerns extend the existing `auth` and `connection` packages to avoid duplicating encryption, endpoint policy, adapter dispatch, and chat orchestration. The frontend adds an `admin` route group and reuses existing connection/model components in read-only mode.

## Complexity Tracking

> No constitution violations require justification. The single per-request user lookup is justified inline in the Constitution Check above and introduces no new architectural layer.
