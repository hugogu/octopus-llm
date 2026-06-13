# Phase 1 Data Model: Personal Center, Response Likes & Usage Analytics

Schema changes are delivered via Flyway migrations `V021`–`V026` (snake_case, no direct DDL).
Existing tables are referenced by their current shape; only **new** columns/tables are described as
additions. Immutability of `provider_responses` is preserved — likes and shares live in separate
tables.

## Existing tables (reference only — already capture most analytics fields)

- **users**: `id, email, password_hash, email_verified, is_admin, is_active, created_at, updated_at`
- **chat_sessions**: `id, user_id→users, title, selected_model_id, created_at, updated_at`
- **chat_turns**: `id, session_id→chat_sessions, sequence_num, prompt_text, attachments,
  selected_model_ids[], selected_configured_model_ids[], client_request_id, created_at`
- **provider_responses** (INSERT-once, immutable): `id, turn_id→chat_turns, configured_model_id,
  model_id, model_display_name, protocol, connection_label, status('complete'|'error'),
  response_text, reasoning_text, error_message, input_tokens, output_tokens, latency_ms, created_at`
- **model_definitions**: `id, provider_id, display_name, capability_matrix, is_active, …`
- **revoked_tokens**: per-jti revocation (explicit logout) — retained, complemented by D4.

## Additions to existing tables

### users (V021)
| Column | Type | Notes |
|--------|------|-------|
| `display_name` | `VARCHAR(255)` NULL | Editable profile name (FR-005). |
| `sessions_valid_from` | `TIMESTAMPTZ` NULL | Tokens with `iat <` this are rejected. Set to `now()` on password change to invalidate all other sessions (FR-002, D4). |

### model_definitions (V022)
| Column | Type | Notes |
|--------|------|-------|
| `input_price_per_mtok` | `NUMERIC(12,4)` NULL | Price per 1M input tokens. NULL = unknown → cost `—`. |
| `output_price_per_mtok` | `NUMERIC(12,4)` NULL | Price per 1M output tokens. |
| `price_currency` | `VARCHAR(3)` NULL | ISO currency (e.g. `USD`); NULL when no price. |

### chat_turns (V023)
| Column | Type | Notes |
|--------|------|-------|
| `client_ip` | `INET` NULL | Originating IP captured at turn submission (FR-019). Owner-visible only; never in aggregate (FR-025). |

### provider_responses (V023)
| Column | Type | Notes |
|--------|------|-------|
| `connection_id` | `UUID` NULL | **Snapshot** of the connection used (no FK/cascade → preserves immutability when a connection is later deleted). Complements existing `connection_label`. |

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
| `visitor_token` | `VARCHAR(64)` NOT NULL | Opaque client-generated token; **not** linkable to any account (FR-015). |
| `created_at` | `TIMESTAMPTZ` NOT NULL `DEFAULT NOW()` | |

- `CONSTRAINT uq_anon_response_like UNIQUE (response_id, visitor_token)` — best-effort dedup (FR-016).
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
- A share is accessible iff `revoked_at IS NULL` and the session still exists.
- **State**: active (`revoked_at IS NULL`) → revoked (`revoked_at` set). One-way.

## Derived / computed (not stored)

- **Estimated cost** (per response): `(input_tokens/1e6)*input_price_per_mtok +
  (output_tokens/1e6)*output_price_per_mtok`, computed at read time from `model_definitions`.
  `NULL`/`—` when price or tokens are missing; excluded from cost sums.
- **Analytics aggregates** (per model / per session): `COUNT`, `AVG(latency_ms)` and a latency
  percentile, `SUM(input_tokens)`, `SUM(output_tokens)`, success rate
  (`COUNT(status='complete')/COUNT(*)`), summed estimated cost — all `WHERE session.user_id = :caller`.

## Entity → requirement traceability

| Entity / column | Requirements |
|-----------------|--------------|
| users.display_name | FR-005 |
| users.sessions_valid_from | FR-002 |
| chat_turns.client_ip | FR-019, FR-022, FR-024, FR-025 |
| provider_responses (existing + connection_id) | FR-019, FR-020, FR-022 |
| model_definitions pricing | FR-019, FR-021 (cost) |
| response_likes | FR-008, FR-009, FR-010, FR-011, FR-018 |
| anonymous_response_likes | FR-014, FR-015, FR-016 |
| session_shares | FR-012, FR-013, FR-017 |
| analytics aggregates (read-only) | FR-021, FR-023, FR-024, FR-025 |
