# Data Model: Unified Parallel LLM Chat

**Phase**: 1 — Design
**Feature**: 001-unified-parallel-llm-chat
**Date**: 2026-06-09

---

## Entity Overview

```
users
  └─── email_verifications (1:N, token-based)
  └─── revoked_tokens (1:N, JWT blocklist)
  └─── provider_api_keys (1:N, encrypted)
  └─── user_model_configs (1:N)
       └─── model_definitions (N:1, platform catalogue)
       └─── provider_api_keys (N:1, nullable — SET NULL on key delete)
  └─── chat_sessions (1:N)
       └─── chat_turns (1:N, immutable after insert)
            └─── provider_responses (1:N, one per model)
```

---

## Table: `users`

The platform account identity.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | PK, DEFAULT gen_random_uuid() | |
| `email` | `VARCHAR(255)` | UNIQUE, NOT NULL | Lowercase-normalised on insert |
| `password_hash` | `VARCHAR(255)` | NOT NULL | bcrypt hash (cost ≥ 12) |
| `email_verified` | `BOOLEAN` | NOT NULL, DEFAULT false | Set to true on token verification |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

**Validation rules:**
- `email`: RFC 5321-compliant format; max 255 chars; unique after lowercasing
- `password_hash`: never stored in plaintext; bcrypt with cost factor ≥ 12
- `email_verified`: must be `true` before login is permitted

---

## Table: `email_verifications`

Tokens issued during registration and email-change flows.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | PK, DEFAULT gen_random_uuid() | |
| `user_id` | `UUID` | NOT NULL, FK → users(id) ON DELETE CASCADE | |
| `token` | `VARCHAR(255)` | UNIQUE, NOT NULL | Opaque random token (64 hex chars) |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL | Registration tokens expire after 24h |
| `used_at` | `TIMESTAMPTZ` | NULLABLE | Set on first use; repeated use rejected |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

**Validation rules:**
- `token`: must not be re-used after `used_at` is set
- `expires_at`: must not be in the past at time of use
- Only one active (unused, non-expired) token per user at a time

---

## Table: `provider_api_keys`

User-supplied API keys for LLM providers, stored encrypted.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | PK, DEFAULT gen_random_uuid() | |
| `user_id` | `UUID` | NOT NULL, FK → users(id) ON DELETE CASCADE | |
| `provider_id` | `VARCHAR(100)` | NOT NULL | e.g. `"openai"`, `"anthropic"`, `"moonshot"` |
| `encrypted_key` | `BYTEA` | NOT NULL | AES-256-GCM ciphertext |
| `key_iv` | `BYTEA` | NOT NULL | 12-byte GCM IV |
| `label` | `VARCHAR(255)` | NULLABLE | User-facing nickname |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

**Index:** `(user_id, provider_id)` — supports lookup of keys per user per provider.

**Validation rules:**
- `provider_id` must match a value in the `model_definitions.provider_id` catalogue
- Decrypted key is validated against provider API format before `encrypted_key` is stored
- `encrypted_key` and `key_iv` are never returned in API responses — only `id`, `provider_id`,
  `label`, `created_at` are exposed

---

## Table: `model_definitions`

Platform-managed catalogue of LLM models. Seeded and updated via Flyway migrations.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `VARCHAR(100)` | PK | e.g. `"gpt-4o-2024-11-20"`, `"claude-3-5-sonnet-20241022"` |
| `provider_id` | `VARCHAR(100)` | NOT NULL | e.g. `"openai"`, `"anthropic"` |
| `display_name` | `VARCHAR(255)` | NOT NULL | e.g. `"GPT-4o (Nov 2024)"` |
| `capability_matrix` | `JSONB` | NOT NULL | See Capability Matrix schema below |
| `is_active` | `BOOLEAN` | NOT NULL, DEFAULT true | Soft-disable without deleting |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

**Index:** `(provider_id)` — supports listing models by provider.

### Capability Matrix JSONB Schema

```json
{
  "input_modalities": ["text", "image"],
  "output_modalities": ["text"],
  "context_length_tokens": 128000,
  "supports_streaming": true,
  "supports_function_calling": true,
  "supports_system_prompt": true,
  "supports_video_input": false
}
```

**Known fields and their types:**

| Field | Type | Default if absent | Description |
|-------|------|-------------------|-------------|
| `input_modalities` | `string[]` | `["text"]` | `"text"`, `"image"`, `"video"` |
| `output_modalities` | `string[]` | `["text"]` | `"text"`, `"image"` |
| `context_length_tokens` | `integer` | `null` | Max context window |
| `supports_streaming` | `boolean` | `false` | Provider supports token streaming |
| `supports_function_calling` | `boolean` | `false` | |
| `supports_system_prompt` | `boolean` | `true` | |
| `supports_video_input` | `boolean` | `false` | |

**Extension rule**: Unknown fields in the JSONB are preserved and exposed as-is; new fields
can be added to future Flyway migration seeds without any code change in existing adapters.

---

## Table: `user_model_configs`

Per-user activation status for each model, linking to their API key.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | PK, DEFAULT gen_random_uuid() | |
| `user_id` | `UUID` | NOT NULL, FK → users(id) ON DELETE CASCADE | |
| `model_id` | `VARCHAR(100)` | NOT NULL, FK → model_definitions(id) | |
| `provider_api_key_id` | `UUID` | **NULLABLE**, FK → provider_api_keys(id) **ON DELETE SET NULL** | NULL when the key was deleted |
| `is_enabled` | `BOOLEAN` | NOT NULL, DEFAULT true | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

**Unique constraint:** `(user_id, model_id)` — one config per user per model.

**Validation rules:**
- At creation time, `provider_api_key_id` MUST NOT be null and MUST reference a key whose
  `provider_id` matches the model's `provider_id`.
- When `provider_api_keys` row is deleted, the DB sets `provider_api_key_id = NULL` via
  `ON DELETE SET NULL`. The application MUST also set `is_enabled = false` on the same
  transaction for every affected row (a DB trigger or application-level hook is acceptable).
- A config row with `provider_api_key_id IS NULL` is always treated as disabled and MUST NOT
  be included in parallel call selection regardless of `is_enabled`.

---

## Table: `chat_sessions`

A named conversation owned by a user.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | PK, DEFAULT gen_random_uuid() | |
| `user_id` | `UUID` | NOT NULL, FK → users(id) ON DELETE CASCADE | |
| `title` | `VARCHAR(500)` | NULLABLE | Auto-generated from first prompt if null |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

---

## Table: `chat_turns`

One exchange within a session: the user's prompt plus all model responses.
Turns are immutable after creation (append-only; no UPDATE on existing rows).

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | PK, DEFAULT gen_random_uuid() | |
| `session_id` | `UUID` | NOT NULL, FK → chat_sessions(id) ON DELETE CASCADE | |
| `sequence_num` | `INTEGER` | NOT NULL | 1-based within session; unique per session |
| `prompt_text` | `TEXT` | NOT NULL | User's text prompt |
| `attachments` | `JSONB` | NULLABLE | `[{"type":"image","data":"<base64>","mime_type":"image/png","size_bytes":12345}]` |
| `selected_model_ids` | `TEXT[]` | NOT NULL | Array of model IDs selected for this turn |
| `client_request_id` | `VARCHAR(100)` | NULLABLE, UNIQUE | Client-supplied idempotency key (UUID recommended) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

**Unique constraint:** `(session_id, sequence_num)`.

**Unique index:** `client_request_id` (sparse — only non-NULL values are indexed).

**Index:** `(session_id, sequence_num)` — supports ordered retrieval of turns in a session.

**Idempotency rule**: If a `POST /turns` request supplies a `clientRequestId` that already
exists in this column, the backend MUST return `409 Conflict` with the existing `turnId`
instead of creating a duplicate turn. This prevents duplicate LLM calls on network retries.

**Immutability rule**: Once a row is inserted, it is never updated. Re-runs create a new turn
in the same session. This is the basis for cross-run comparison (future feature).

---

## Table: `provider_responses`

One model's final response to a single chat turn. Rows are **inserted once** when streaming
completes or errors — never updated in place. During streaming, response text is accumulated
in application memory; the DB row is written only when the outcome is known. This ensures
immutability as required by Constitution Principle IV.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `UUID` | PK, DEFAULT gen_random_uuid() | |
| `turn_id` | `UUID` | NOT NULL, FK → chat_turns(id) ON DELETE CASCADE | |
| `model_id` | `VARCHAR(100)` | NOT NULL, FK → model_definitions(id) | |
| `status` | `VARCHAR(50)` | NOT NULL | `complete` or `error` — set at insert time, never updated |
| `response_text` | `TEXT` | NULLABLE | Full accumulated response text; NULL when `status = 'error'` |
| `error_message` | `TEXT` | NULLABLE | Set when `status = 'error'` |
| `input_tokens` | `INTEGER` | NULLABLE | Reported by provider on completion |
| `output_tokens` | `INTEGER` | NULLABLE | |
| `latency_ms` | `INTEGER` | NOT NULL | Wall-clock time from dispatch to final INSERT |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | Timestamp of the INSERT (i.e., completion time) |

**Unique constraint:** `(turn_id, model_id)`.

**Index:** `(turn_id)` — supports loading all responses for a given turn.

**Streaming behaviour (application layer, not DB):**
Tokens are appended to an in-memory buffer per `(turnId, modelId)` as they arrive.
On `model_complete` event: INSERT one row with `status='complete'`, full `response_text`, token counts, and latency.
On `model_error` event: INSERT one row with `status='error'`, `error_message`, and latency.
No intermediate rows, no UPDATE statements.

---

## Table: `revoked_tokens`

JWT blocklist for immediate logout invalidation. A token present in this table is rejected
on all authenticated requests regardless of its signature validity.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `jti` | `VARCHAR(255)` | PK | JWT `jti` claim (unique token identifier) |
| `user_id` | `UUID` | NOT NULL, FK → users(id) ON DELETE CASCADE | |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL | JWT expiry; rows can be purged after this time |
| `revoked_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

**Index:** `(expires_at)` — supports periodic cleanup of expired rows.

**Behaviour:**
- Every JWT issued by the login endpoint MUST include a `jti` (UUID) claim.
- Logout inserts a row here; all subsequent requests bearing the same `jti` return `401`.
- Rows whose `expires_at` is in the past are safe to delete (token is already expired).
- This table is the single source of truth for session revocation; no server-side session
  store is required beyond this.

---

## Session State Management

Auth sessions use short-lived JWTs (1-hour `exp`) plus the `revoked_tokens` table above for
immediate logout. There is no other server-side session store. This is the definitive design;
Spring Session JDBC is not used.

---

## Migration File Naming Convention

```
V001__create_users.sql
V002__create_email_verifications.sql
V003__create_provider_api_keys.sql
V004__create_model_definitions.sql
V005__create_user_model_configs.sql
V006__create_chat_sessions.sql
V007__create_chat_turns.sql
V008__create_provider_responses.sql
V009__seed_model_catalogue.sql
V010__create_revoked_tokens.sql
```
