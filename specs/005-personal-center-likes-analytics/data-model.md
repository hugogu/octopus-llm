# Phase 1 Data Model: Personal Center, Response Likes & Usage Analytics

Schema changes are delivered via Flyway migrations `V021`–`V026` (snake_case, no direct DDL).
Existing tables are referenced by their current shape; only **new** columns/tables are described as
additions. Immutability of `provider_responses` is preserved — likes and shares live in separate
tables.

## Existing tables (reference only — already capture most analytics fields)

- **users**: `id, email, password_hash, email_verified, is_admin, is_active, is_disabled,
  session_epoch, created_at, updated_at`
- **chat_sessions**: `id, user_id→users, title, selected_model_id, created_at, updated_at`
- **chat_turns**: `id, session_id→chat_sessions, sequence_num, prompt_text, attachments,
  selected_model_ids[], selected_configured_model_ids[], client_request_id, created_at`
- **provider_responses** (INSERT-once, immutable): `id, turn_id→chat_turns, configured_model_id,
  model_id, model_display_name, protocol, connection_label, status('complete'|'error'),
  response_text, reasoning_text, error_message, input_tokens, output_tokens, latency_ms, created_at`
- **configured_models**: `id, user_id, connection_id, model_id, display_name, capability_overrides,
  custom_params, is_enabled, sort_order, created_at, updated_at`
- **email_verifications**: single-use email-verification tokens with expiry/used timestamps.
- **password_resets**: single-use password-reset tokens with expiry/used timestamps.
- **revoked_tokens**: per-jti revocation (explicit logout) — retained, complemented by D4.

## Additions to existing tables

### users (V021)
| Column | Type | Notes |
|--------|------|-------|
| `display_name` | `VARCHAR(255)` NULL | Editable profile name (FR-005). |

Password change reuses the existing `users.session_epoch`: increment it and return a replacement JWT
with the new epoch. No new invalidation column is added.

### auth_action_throttles (V021)
| Column | Type | Notes |
|--------|------|-------|
| `action` | `VARCHAR(50)` NOT NULL | `password_reset_request`, `verification_resend`, or other bounded auth action. |
| `key_hash` | `VARCHAR(64)` NOT NULL | HMAC of normalized email or trusted network key; raw email/IP is not stored in this table. |
| `window_started_at` | `TIMESTAMPTZ` NOT NULL | Fixed-window start. |
| `request_count` | `INTEGER` NOT NULL | Incremented atomically; non-negative. |
| `expires_at` | `TIMESTAMPTZ` NOT NULL | Allows periodic deletion of expired throttle rows. |

- Primary key: `(action, key_hash, window_started_at)`.
- Atomic upsert/count gives cross-instance throttling without in-memory coordination or distributed
  locks (FR-007/FR-031).

### configured_models (V022)
| Column | Type | Notes |
|--------|------|-------|
| `input_price_per_mtok` | `NUMERIC(12,4)` NULL | Price per 1M input tokens. NULL = unknown → cost `—`. |
| `output_price_per_mtok` | `NUMERIC(12,4)` NULL | Price per 1M output tokens. |
| `price_currency` | `VARCHAR(3)` NULL | ISO currency (e.g. `USD`); NULL when no price. |

### chat_turns (V023)
| Column | Type | Notes |
|--------|------|-------|
| `client_ip` | `INET` NULL | Originating IP resolved from direct peer/trusted proxy configuration (FR-019). Owner-visible detail only; never in aggregate (FR-025). |

### provider_responses (V023)
| Column | Type | Notes |
|--------|------|-------|
| `connection_id` | `UUID` NULL | **Snapshot** of the connection used (no FK/cascade → preserves immutability when a connection is later deleted). Nullable only for legacy rows; every new response populates it. |
| `input_price_per_mtok` | `NUMERIC(12,4)` NULL | Immutable configured-model input-price snapshot at dispatch. |
| `output_price_per_mtok` | `NUMERIC(12,4)` NULL | Immutable configured-model output-price snapshot at dispatch. |
| `price_currency` | `VARCHAR(3)` NULL | Immutable currency snapshot; NULL when pricing is unknown. |

## New tables

### response_likes (V024) — named likes
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK `DEFAULT gen_random_uuid()` | |
| `response_id` | `UUID` NOT NULL → `provider_responses(id)` `ON DELETE CASCADE` | FR-011 |
| `user_id` | `UUID` NOT NULL → `users(id)` `ON DELETE CASCADE` | |
| `created_at` | `TIMESTAMPTZ` NOT NULL `DEFAULT NOW()` | |

- `CONSTRAINT uq_response_like UNIQUE (response_id, user_id)` — at most one like per user per response (FR-009).
- `INDEX idx_response_likes_response ON (response_id)` — count/aggregate.
- Named like count = `COUNT(*) WHERE response_id = ?`; `likedByMe` = existence for `(response_id, user_id)`.
- **Lifecycle**: insert (like) / delete (un-like) — toggle.

### anonymous_response_likes (V025) — anonymous counter (best-effort dedup)
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK `DEFAULT gen_random_uuid()` | |
| `response_id` | `UUID` NOT NULL → `provider_responses(id)` `ON DELETE CASCADE` | |
| `visitor_key_hash` | `VARCHAR(64)` NOT NULL | Hex HMAC of server-issued browser cookie scoped to the share token; raw cookie is not stored and is not linkable to an account (FR-015/016). |
| `created_at` | `TIMESTAMPTZ` NOT NULL `DEFAULT NOW()` | |

- `CONSTRAINT uq_anon_response_like UNIQUE (response_id, visitor_key_hash)` — best-effort dedup (FR-016).
- `INDEX idx_anon_response_likes_response ON (response_id)`.
- Anonymous count = `COUNT(*) WHERE response_id = ?`. No identity stored.

### session_shares (V026) — opaque, revocable, no expiry
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK `DEFAULT gen_random_uuid()` | |
| `session_id` | `UUID` NOT NULL → `chat_sessions(id)` `ON DELETE CASCADE` | Deleting the conversation kills the link (FR-017, edge case). |
| `token` | `VARCHAR(64)` NOT NULL UNIQUE | High-entropy opaque token; no identity (FR-012). |
| `created_at` | `TIMESTAMPTZ` NOT NULL `DEFAULT NOW()` | |
| `revoked_at` | `TIMESTAMPTZ` NULL | NULL = active; set on revoke (FR-017). **No expiry column** (clarified). |

- `INDEX idx_session_shares_session ON (session_id)`.
- `UNIQUE INDEX uq_session_shares_active ON (session_id) WHERE revoked_at IS NULL` — create-share is
  idempotent and returns the existing active share.
- A share is accessible iff `revoked_at IS NULL` and the session still exists.
- **State**: active (`revoked_at IS NULL`) → revoked (`revoked_at` set). One-way.

## Derived / computed (not stored)

- **Estimated cost** (per response): `(input_tokens/1e6)*input_price_per_mtok +
  (output_tokens/1e6)*output_price_per_mtok`, computed at read time from the immutable response
  pricing snapshot.
  `NULL`/`—` when price or tokens are missing; excluded from cost sums.
- **Analytics aggregates** (per model / per session): `COUNT`, `AVG(latency_ms)` and a latency
  percentile, `SUM(input_tokens)`, `SUM(output_tokens)`, success rate
  (`COUNT(status='complete')/COUNT(*)`), and estimated costs grouped by currency — all
  `WHERE session.user_id = :caller`.
- **Public model aggregates**: grouped only by `provider_responses.protocol, provider_responses.model_id`;
  include response/latency/token/success and named/anonymous like totals. The query projection does not
  select user, session, IP, connection, configured-model, prompt, or response-content fields.

## Entity → requirement traceability

| Entity / column | Requirements |
|-----------------|--------------|
| users.display_name | FR-005 |
| users.session_epoch (existing) | FR-002 |
| auth_action_throttles | FR-007, FR-031 |
| email_verifications (existing) | FR-003, FR-004 |
| password_resets (existing) | FR-031 |
| chat_turns.client_ip | FR-019, FR-022, FR-024, FR-025 |
| provider_responses (existing + connection/pricing snapshots) | FR-019, FR-020, FR-022, FR-030 |
| configured_models pricing | FR-006, FR-030 |
| response_likes | FR-008, FR-009, FR-010, FR-011, FR-018 |
| anonymous_response_likes | FR-014, FR-015, FR-016 |
| session_shares | FR-012, FR-013, FR-017 |
| analytics aggregates (read-only) | FR-021, FR-023, FR-024, FR-025 |
| public model aggregate projection | FR-028, FR-029 |
