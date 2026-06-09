<!--
SYNC IMPACT REPORT
==================
Version change: N/A → 1.0.0 (initial ratification)
Modified principles: none (first version)
Added sections:
  - Core Principles (7 principles)
  - Technology Stack & Constraints
  - Development Workflow
  - Governance
Templates status:
  ✅ .specify/templates/plan-template.md — Constitution Check section aligns
  ✅ .specify/templates/spec-template.md — Requirement/constraint sections align
  ✅ .specify/templates/tasks-template.md — Phase structure & task categories align
  ✅ AGENTS.md — pre-existing rules folded into principles where relevant
Deferred items: none
-->

# Octopus LLM Constitution

## Core Principles

### I. Provider-Agnostic Abstraction (NON-NEGOTIABLE)

Every LLM provider integration MUST be implemented behind a unified `LLMProvider` interface.
No provider-specific logic may appear outside its own adapter module.
New providers MUST be onboardable by adding a single adapter and configuration entry — zero
changes to core orchestration code. The interface MUST support: text generation, streaming,
token counting, and capability metadata. Feature flags MUST gate provider availability.

**Rationale**: The platform's core value is side-by-side comparison. If provider coupling bleeds
into orchestration code, adding or removing a provider breaks comparison semantics and forces
coordinated changes across the codebase.

### II. API-First Design (NON-NEGOTIABLE)

All platform functionality MUST be exposed via the public REST API before any UI is built on top
of it. The Next.js frontend MUST consume the same API endpoints as external API clients — no
internal shortcuts, server-side DB queries, or direct backend function calls from the frontend
layer. The API MUST be versioned (`/api/v1/`). Breaking changes MUST increment the major version.
API endpoints MUST be idempotent where semantics allow.

**Rationale**: API-first ensures the external API surface is a first-class product, not an
afterthought. It also prevents the frontend from accumulating privileged access that cannot be
replicated by third-party integrations.

### III. Concurrent Execution & Streaming

All LLM provider calls for a given prompt MUST be dispatched concurrently (not sequentially).
The platform MUST NOT wait for the slowest provider before returning partial results.
Streaming responses MUST be forwarded to clients in real time via SSE or WebSocket.
The backend MUST NOT impose artificial serialization across providers.
No distributed locks may be used in the hot path of concurrent execution.

**Rationale**: Latency is user-visible. Sequential calls would make multi-provider comparison
unusable at scale. Concurrency is the product's defining technical characteristic.

### IV. Data Integrity & Immutable Sessions

All database schema changes MUST be performed via versioned migrations (Flyway or Liquibase).
DDL statements MUST NOT be executed directly against any database instance.
Saved session records (prompt + all provider responses) MUST be immutable after creation;
append-only versioning MUST be used when a session is re-run.
Comparison between runs MUST operate on immutable snapshots, never on mutable live data.
Column names and table names MUST use `snake_case`.

**Rationale**: Immutability is required for reproducible comparison. If session data can be
edited in place, historical comparison loses meaning and trust.

### V. Observability & Analytics

Every LLM call MUST emit structured log events capturing: provider, model, latency,
token count, error code (if any), and user identifier (anonymized for cross-user analytics).
Satisfaction signals (explicit ratings, implicit engagement) MUST be captured per response.
The platform MUST maintain both user-scoped analytics (visible to the owning user) and
anonymous aggregate analytics (visible to all). Personal data MUST NOT appear in aggregate views.
All analytics queries MUST be read-only and MUST NOT touch the hot write path.

**Rationale**: The platform's secondary value proposition is surfacing which models perform
better in aggregate. Without instrumentation on every call, this is impossible.

### VI. Security & User Key Privacy (NON-NEGOTIABLE)

User-provided LLM API keys MUST be encrypted at rest using AES-256 or equivalent.
Keys MUST NOT appear in application logs, error messages, API responses, or analytics payloads.
Authentication is required for all endpoints that access personal data or consume user API keys.
Session sharing links MUST use opaque tokens — no user identity information in shareable URLs.
Rate limiting MUST be applied per user to prevent key abuse.

**Rationale**: Users are entrusting the platform with credentials that have real monetary cost.
A single key leak can result in significant financial harm to the user.

### VII. Simplicity & Horizontal Scalability

The system MUST adopt eventual consistency by default.
Distributed locks are prohibited; design MUST avoid state that requires cross-instance coordination.
The application MUST be deployable via Docker Compose for local and single-server deployments,
and MUST support horizontal scaling behind a load balancer without application changes.
Complexity MUST be justified: every additional abstraction layer requires an explicit rationale
documented in the plan. YAGNI applies — do not design for scale not yet required.

**Rationale**: Premature complexity is the primary risk in platform projects. Horizontal
scalability is non-negotiable for production viability; distributed-lock-free design enables it.

## Technology Stack & Constraints

### Backend

- **Language/Runtime**: Kotlin on JVM, Java 21 (virtual threads via Project Loom encouraged)
- **Framework**: Spring Boot (latest stable); Spring WebFlux for reactive/streaming endpoints
- **Database**: PostgreSQL (latest stable); migrations via Flyway
- **ORM**: Spring Data JPA / Hibernate or jOOQ — consistent within the project
- **Build**: Gradle (Kotlin DSL preferred)

### Frontend

- **Runtime**: Node.js 24
- **Framework**: Next.js (App Router); TypeScript; strict mode enabled
- **API contract**: All data fetching via the backend REST API — no direct DB access
- **State management**: Server Components where possible; client components only for interactivity
- **Streaming**: SSE or WebSocket client for real-time concurrent provider responses

### Infrastructure

- **Database**: PostgreSQL — all data including sessions, keys (encrypted), analytics
- **Deployment**: Docker Compose for all environments; Dockerfile for each service
- **Container images**: Explicit version tags; no `latest`; multi-stage builds required
- **Architecture**: ARM64 local development; AMD64 server builds via `--platform linux/amd64`

### API Design Standards

- RESTful; versioned at `/api/v1/`; JSON request/response bodies
- Idempotent operations wherever semantically valid
- Consistent error response schema: `{ "code": "...", "message": "...", "details": {} }`
- Pagination for all list endpoints

## Development Workflow

### Before Writing Code

1. Read `tsconfig.json` (frontend) and verify strict flags before writing TypeScript.
2. Read existing migration files before writing new ones — never duplicate column names.
3. Verify all `import` dependencies are declared in the module's own `package.json`/`build.gradle`.

### Code Quality Gates (mandatory before marking any task complete)

- Backend: `./gradlew build` MUST pass (compilation + unit tests).
- Frontend: `npx tsc --noEmit` MUST pass with zero errors.
- New API endpoints MUST have at least one integration test covering the happy path.
- Flyway migrations MUST be validated locally before committing.

### Complexity Justification

Any plan that introduces more than three backend service layers, more than two inter-service
communication patterns, or a cross-cutting concern not listed in the Technology Stack section
MUST include an explicit Complexity Tracking table in `plan.md`.

### Commit Convention

Conventional Commits format. Footer MUST include model/agent attribution.
Submodule changes committed to submodule first, then parent repo updated.

## Governance

This constitution supersedes all other project-level practices where they conflict.
The AGENTS.md rules are subordinate to this constitution; conflicts resolve in the
constitution's favor. Amendments require: (1) a written rationale, (2) a migration plan for
existing code if the principle affects live systems, and (3) a version bump per the policy below.

**Versioning policy**:
- PATCH: Clarifications, wording, non-semantic refinements.
- MINOR: New principle or section added; materially expanded guidance.
- MAJOR: Principle removed, renamed, or redefined in a backward-incompatible way.

All pull requests touching files governed by a principle MUST include a Constitution Check in
the plan confirming compliance. Violations MUST be justified in the Complexity Tracking table.

**Version**: 1.0.0 | **Ratified**: 2026-06-09 | **Last Amended**: 2026-06-09
