# Data Model: Admin Control Panel

Ships as Flyway migration `V019__admin_control_panel.sql`. All names `snake_case` (Constitution IV).

## users (ALTER)

| Column | Type | Rules |
|---|---|---|
| `is_admin` | BOOLEAN | NOT NULL DEFAULT FALSE. Grants `ROLE_ADMIN`. |
| `is_active` | BOOLEAN | NOT NULL DEFAULT FALSE. Activation gate; required to receive built-in allocations. |
| `is_disabled` | BOOLEAN | NOT NULL DEFAULT FALSE. When TRUE, both login and all authenticated requests are rejected. |
| `session_epoch` | INTEGER | NOT NULL DEFAULT 0. Monotonic counter embedded in issued JWTs; tokens whose epoch claim is `<` this value are rejected. Incremented on disable and on password reset. Integer comparison avoids the JWT-`iat` second-precision hazard. |

Partial index: `CREATE INDEX idx_users_enabled_admins ON users(id) WHERE is_admin AND NOT is_disabled;` (supports the usable-admin count used by the last-admin guard).

State semantics:

- **Activation** (`is_active`) is independent of `email_verified` and of `is_disabled`.
- **Disable** sets `is_disabled = TRUE` and `session_epoch = session_epoch + 1` (revokes all outstanding tokens); **enable** clears `is_disabled` only and does **not** change `session_epoch` (a freshly issued token after re-login carries the current epoch and is valid). Prior data intact.
- A user may be `is_active = TRUE` and `is_disabled = TRUE` simultaneously (allocations inert while disabled — spec edge case).
- Disable, demote, and password-reset of an admin are guarded by the concurrency-safe last-usable-admin invariant (see [research.md](research.md) Decision 7); the write is performed by a conditional UPDATE / `SERIALIZABLE` transaction, never by a bare count-then-update.

## connections (ALTER)

| Column | Type | Rules |
|---|---|---|
| `is_builtin` | BOOLEAN | NOT NULL DEFAULT FALSE. TRUE rows are platform-owned and exposed read-only to allocated users. |

- A built-in connection is owned (`user_id`) by an admin; its `configured_models` are owned by the same admin.
- Only `is_builtin = TRUE` connections may appear in `connection_allocations`.
- Existing endpoint-policy and encryption rules apply unchanged.

## connection_allocations (NEW)

| Column | Type | Rules |
|---|---|---|
| `connection_id` | UUID | FK `connections(id)` ON DELETE CASCADE. Must reference an `is_builtin` connection (enforced in service). |
| `user_id` | UUID | FK `users(id)` ON DELETE CASCADE. Recipient account. |
| `allocated_by` | UUID | FK `users(id)`. Acting admin. |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT NOW(). |

Constraints:

- PRIMARY KEY `(connection_id, user_id)` — allocation is idempotent (FR-010), many users per connection.
- Index `idx_allocations_user ON connection_allocations(user_id)` — drives per-user listing and chat resolution.

Rules: allocation refused unless target is `is_active = TRUE`. Revoke = delete the row; other rows unaffected (FR-016).

## password_resets (NEW)

| Column | Type | Rules |
|---|---|---|
| `id` | UUID | PK DEFAULT gen_random_uuid(). |
| `user_id` | UUID | FK `users(id)` ON DELETE CASCADE. |
| `token` | VARCHAR(255) | NOT NULL UNIQUE. Opaque random. |
| `expires_at` | TIMESTAMPTZ | NOT NULL. 24h from issue. |
| `used_at` | TIMESTAMPTZ | NULL until consumed. |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT NOW(). |

Mirrors `email_verifications`. **Single-use is enforced atomically**, not by read-then-write: consumption runs `UPDATE password_resets SET used_at = now() WHERE token = :token AND used_at IS NULL AND expires_at > now()` and proceeds only when exactly one row is affected, then sets the new bcrypt `password_hash`. Concurrent confirmations of the same token resolve to one winner (H5). A token is rejected when expired, already used, or unknown.

## admin_audit_log (NEW)

| Column | Type | Rules |
|---|---|---|
| `id` | UUID | PK DEFAULT gen_random_uuid(). |
| `admin_user_id` | UUID | FK `users(id)`. Acting admin. |
| `action` | VARCHAR(50) | One of: `ACTIVATE`, `DISABLE`, `ENABLE`, `RESET_PASSWORD`, `BUILTIN_CONNECTION_CREATE`, `BUILTIN_CONNECTION_UPDATE`, `BUILTIN_CONNECTION_DELETE`, `ALLOCATE`, `REVOKE`. |
| `target_type` | VARCHAR(20) | `USER` or `CONNECTION`. |
| `target_id` | UUID | Affected account or connection. |
| `metadata` | JSONB | NOT NULL DEFAULT '{}'. Never contains key material or plaintext password. |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT NOW(). |

Append-only; index `idx_audit_created ON admin_audit_log(created_at DESC)`.

## Derived / behavioral entities (no new table)

- **Selectable model set** (chat): `configured_models` where `user_id = caller` **UNION** `configured_models cm JOIN connections c ON cm.connection_id = c.id JOIN connection_allocations a ON a.connection_id = c.id WHERE c.is_builtin AND a.user_id = caller`, filtered to `is_enabled = TRUE`. Used by `ConfiguredModelService.requireSelectable`.
- **User-visible connections**: own connections **plus** built-in connections allocated to the caller (returned with `builtin = true, readOnly = true`).

## JWT claim change

`JwtTokenService.issue(userId, sessionEpoch)` embeds a `session_epoch` claim (integer). `JwtClaims` gains `sessionEpoch: Int`. Login already loads the `User`, so the epoch is available without an extra read. Existing tokens (no claim) are treated as epoch `0`.

## Validation rules summary

| Rule | Source |
|---|---|
| Allocation requires target `is_active = TRUE` (disabled is orthogonal; allocation may exist but is inert) | FR-015 / M1 |
| Allocation/activate/disable/enable are idempotent | FR-010 |
| Cannot disable, demote, **or reset-password** the last usable admin; enforced by conditional UPDATE / `SERIALIZABLE` (concurrency-safe) | FR-004 / SC-007 / C1 / C2 |
| Password-reset token consumed by atomic conditional UPDATE (single-use under concurrency) | FR-009 / H5 |
| Login rejects a disabled account before issuing a token | FR-007 / FR-024 / H1 |
| Session revocation via integer `session_epoch` (no `iat` precision dependency) | SC-002 / H6 |
| Built-in key encrypted, never in any DTO/log | FR-013 / Constitution VI |
| Built-in endpoint passes `ConnectionEndpointPolicy` | FR-014 |
| Disabled user rejected on every authenticated request | FR-007 / FR-024 / SC-002 |
| Deleting built-in connection preserves response snapshots | FR-021 (existing immutable `provider_responses`) |
| User-list query uses indexed search + deterministic order; first page < 1s at 10k users | SC-006 / H4 |
