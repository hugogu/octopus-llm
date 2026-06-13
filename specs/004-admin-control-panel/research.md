# Research: Admin Control Panel

All Technical Context items were resolved against the existing codebase (features 001–003). No open `NEEDS CLARIFICATION` remain; the two scope ambiguities were resolved with the user during `/speckit-specify` (activation gates built-in only; allocations are read-only and shared).

## Decision 1 — Account state model

**Decision**: Add four columns to `users`: `is_admin BOOLEAN`, `is_active BOOLEAN`, `is_disabled BOOLEAN` (all `NOT NULL DEFAULT FALSE`), and `session_epoch INTEGER NOT NULL DEFAULT 0`.

**Rationale**: The existing `users` table (`V001`) carries only `email_verified`. The spec separates three independent axes — admin role, activation (built-in eligibility), and enabled/disabled (authentication). Booleans map directly to the spec's status columns and the user-list DTO (FR-005). `is_active` defaults `false`, which is correct: existing and new accounts keep BYOK (FR-022) but are not built-in-eligible until an admin activates them (US3).

**Alternatives considered**: A single `status` enum (`PENDING/ACTIVE/DISABLED`) was rejected because activation and disabled are orthogonal (a disabled account can be activated-but-blocked), which an enum conflates.

## Decision 2 — Immediate disable + session revocation via integer epoch (no locks, no clock precision)

**Decision**: Enforce account state in the reactive security filter. `SecurityConfig.jwtSecurityContextRepository.load` already validates the JWT (and per-JTI revocation via `revoked_tokens`). `JwtTokenService.issue` embeds the user's current `session_epoch` as a claim (login already loads the user, so no extra read). `JwtTokenService.validate` returns that epoch; the filter loads the `User` and rejects the request (`401`) when `is_disabled = true` or `token.session_epoch < user.session_epoch`. Grant `ROLE_ADMIN` in addition to `ROLE_USER` when `is_admin = true`. On disable and on password reset, increment `session_epoch` (`+1`).

**Rationale**: SC-002 requires disable to take effect within one request cycle, which the current per-JTI revocation (only the token presented at logout) cannot deliver. A monotonic integer `session_epoch` carried in the token invalidates every previously issued token for a user in O(1) with no need to enumerate or store outstanding tokens, satisfying Constitution VII (no distributed locks, no cross-instance coordination). An integer comparison is exact, avoiding the timestamp-vs-`iat` precision hazard (H6): JWT `iat` is second-granular while a `TIMESTAMPTZ` is sub-second, so a token issued in the same second as an enable/reset could compare "older" than a wall-clock cutoff and be wrongly rejected. Epoch increments remove the time axis entirely. The added cost is one indexed PK read per authenticated request, already adjacent to the existing `existsByJti` read.

**Alternatives considered**: (a) A `sessions_invalid_before TIMESTAMPTZ` cutoff compared to `iat` — rejected for the second-granularity precision hazard above. (b) Revoking each outstanding JTI — impossible without tracking all issued tokens. (c) Shortening token TTL — does not give immediate effect. (d) A Redis denylist — adds cross-instance state the constitution discourages.

**Test**: Issue a token, increment epoch (disable→enable), re-login immediately, and confirm the fresh token's authenticated request succeeds while the pre-increment token is rejected within the same second.

## Decision 3 — Built-in connections reuse the connection model

**Decision**: A built-in connection is a `connections` row owned by an admin user with a new `is_builtin BOOLEAN NOT NULL DEFAULT FALSE` flag. Its models are ordinary `configured_models` rows owned by that admin. Allocation is a new `connection_allocations(connection_id, user_id, allocated_by, created_at)` join table (PK `(connection_id, user_id)`), valid only for built-in connections and **activated** target users. Disabled status is orthogonal to allocation: an allocation may exist on a disabled account but is inert while the account cannot authenticate (spec edge case). The single allocation precondition is `is_active = TRUE` — consistent across spec, data-model, contracts, and tasks (resolves M1).

**Rationale**: `connections`/`configured_models` already provide AES-256-GCM key encryption (`ApiKeyEncryptionService`), endpoint-safety validation (`ConnectionEndpointPolicy`), adapter dispatch (`ProtocolAdapterRegistry`), and immutable response snapshots. Reusing them satisfies FR-012–FR-014, FR-019, FR-021 with no duplicated security-critical code (Constitution VI/VII). Ownership-by-admin keeps the existing composite FK `configured_models(user_id, connection_id) → connections(user_id, id)` intact.

**Alternatives considered**: Separate `builtin_connections` / `builtin_configured_models` tables — rejected because they would fork encryption, endpoint policy, adapter dispatch, and chat orchestration, multiplying the surface that must uphold key privacy.

## Decision 4 — Chat & listing resolution scope

**Decision**: Replace the owner-only `ConfiguredModelService.requireOwned(userId, ids)` used by `ChatService.submitTurn` with `requireSelectable(userId, ids)` = models owned by the user **or** models on a built-in connection allocated to the user (and enabled, and the user not disabled). The user connection list (`/api/v2/connections`) additionally returns built-in connections allocated to the caller, flagged `builtin = true, readOnly = true`. Mutation endpoints already call `requireOwned`, so they naturally reject built-in connections the caller does not own.

**Rationale**: Satisfies FR-017 (read-only use), FR-019 (dispatch with admin key — `decryptAndValidate` is owner-agnostic), and FR-023 (own + allocated models appear together). Keeps mutation safety automatic via the existing ownership checks.

**Alternatives considered**: Materializing per-user copies of built-in configured models on allocation — rejected (chosen "shared, read-only" model in `/speckit-specify`; copies would desync on key/endpoint rotation, violating FR-020).

## Decision 5 — Password reset flow

**Decision**: Admin reset (`POST /api/v2/admin/users/{id}/reset-password`) sets the user's `password_hash` to an unusable random value, increments `session_epoch`, creates a single-use `password_resets` token (24h expiry), and emails a reset link via `EmailService`. A new public endpoint `POST /api/v1/auth/password-reset/confirm { token, password }` consumes the token and sets a new bcrypt hash. The admin never chooses or sees the password.

**Single-use atomicity (H5)**: Token consumption is a single conditional UPDATE — `UPDATE password_resets SET used_at = now() WHERE token = :token AND used_at IS NULL AND expires_at > now()` — and proceeds only when exactly one row is affected. Two concurrent confirmations race on the same row; the database guarantees only one update affects the row, so only one wins. `findByToken`-then-save (read-modify-write) is **not** used because two readers could both observe `used_at IS NULL`.

**Last-admin safety (C2)**: An admin reset that would lock out the only usable administrator is refused. Resetting an admin's password invalidates their credential and sessions, so reset-of-an-admin is gated by the same last-usable-admin guard as disable/demote (Decision 7). Resetting a non-admin is always allowed.

**Rationale**: Satisfies FR-009 (current password stops working immediately; user must set a new one) and the spec assumption that admins never learn user passwords. Reuses the established token-table pattern from `email_verifications` and the existing `EmailService`/`JavaMailSender`.

**Alternatives considered**: Admin sets a temporary password — rejected (admin would learn a working credential). A `password_reset_required` flag checked at login — rejected as redundant once the hash is already unusable.

## Decision 6 — Admin role bootstrap

**Decision**: An `AdminBootstrap` `ApplicationRunner` reads a configured admin email (e.g. `app.admin.bootstrap-email`); on startup, if a user with that email exists and is not yet admin, it sets `is_admin = true` and `is_active = true` (idempotent). No-op when unset or already satisfied.

**Rationale**: Satisfies FR-002 (designate an initial admin without a pre-existing admin) using runtime configuration consistent with the project's Docker-Compose env model. Idempotent and safe across horizontal instances.

**Alternatives considered**: A dedicated CLI/migration insert — rejected; the admin account must first register normally (to have a password hash), so promotion-by-config is simpler than data-seeding a credential.

## Decision 7 — Last-admin protection (concurrency-safe)

**Decision**: Any action that would leave zero *usable* administrators — disabling an admin, clearing an admin's admin flag (demote), or resetting an admin's password — is refused. The check is **not** a plain count-then-update (which races: two concurrent disables can each observe two admins and both proceed, reaching zero — C1). Two acceptable concurrency-safe mechanisms, in order of preference:

1. **Conditional UPDATE** that re-evaluates the invariant inside the same statement, e.g. for disable:
   ```sql
   UPDATE users SET is_disabled = TRUE, session_epoch = session_epoch + 1
   WHERE id = :id AND is_disabled = FALSE
     AND (is_admin = FALSE
          OR (SELECT count(*) FROM users WHERE is_admin AND NOT is_disabled) > 1);
   ```
   Run inside a `SERIALIZABLE` transaction; if zero rows are affected, the action is refused (`409`). Demote uses the analogous guard.
2. Where a single conditional statement is awkward (e.g. password reset spans multiple tables), wrap the guard + mutation in a `SERIALIZABLE` transaction and **retry on serialization failure** (`40001`) a bounded number of times.

`AdminUserService` exposes `countUsableAdmins()` only for read-side display; the write-side invariant is enforced by mechanism (1)/(2), never by the count alone.

**Rationale**: Satisfies FR-004 / SC-007 (never reach zero enabled admins) under concurrency without distributed locks — `SERIALIZABLE` isolation (Postgres SSI) detects the conflicting read-write pattern and aborts one transaction, which we retry. Constitution VII permits database-level isolation; it prohibits only application-level distributed locks.

**Test**: Two concurrent disable (and two concurrent reset) requests against the last two enabled admins must leave exactly one enabled admin; one request succeeds and one is refused `409`.

## Decision 9 — Disable enforcement at the login endpoint (H1)

**Decision**: `AuthService.login` rejects a disabled account *before* issuing a token. After the password matches, if `user.is_disabled` is true it returns the same `401 Invalid credentials` (non-disclosing) instead of a JWT.

**Rationale**: The security-filter check (Decision 2) only governs *authenticated* requests; the public `POST /api/v1/auth/login` path bypasses it and would otherwise mint a fresh token for a disabled user, contradicting FR-007/FR-024 ("blocked from authenticating"). Enforcing at login closes the only token-minting path. The session-epoch check still backstops already-issued tokens.

**Test**: A disabled account that supplies correct credentials to `/api/v1/auth/login` receives `401` and no token.

## Decision 8 — Auditing

**Decision**: `admin_audit_log(id, admin_user_id, action, target_type, target_id, metadata jsonb, created_at)`, append-only, written by `AdminAuditService` within each admin mutation. Metadata excludes any key material or plaintext password.

**Rationale**: Satisfies FR-025 and Constitution V without touching the analytics hot path.
