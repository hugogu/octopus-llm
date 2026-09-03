# Data Model: Anonymous Chat and Model Access Management

## Existing tables reused

| Table | Use in this feature | Integrity rule |
|---|---|---|
| `connections` | Determine whether a model is administrator-controlled through `is_builtin` and dispatch through the existing encrypted connection configuration. | Public queries must join only built-in connections; credentials remain server-side. |
| `configured_models` | Store the normal model state and the independent anonymous allowlist flag. | `is_enabled` keeps its existing authenticated meaning. |
| `chat_sessions`, `chat_turns`, `provider_responses` | Store imported conversations after registration. | Imported records use the existing authenticated ownership and immutable response-snapshot rules. |
| `admin_audit_logs` | Record administrator model policy and lifecycle changes. | Extend action/target constraints through Flyway; metadata contains only safe IDs, counts, and outcomes. |
| `auth_action_throttles` | Store anonymous fixed-window request counts using an HMAC-derived client key. | Raw client IP is never stored. |

## New or changed schema

All identifiers and columns use the repository's `snake_case` database convention. The anonymous-access tables and flag are added by `V041__anonymous_model_access.sql`; Guest default selection is added by `V042__anonymous_default_models.sql`. No application startup DDL is used.

### `configured_models`

```text
is_anonymous_allowed BOOLEAN NOT NULL DEFAULT FALSE
is_anonymous_default BOOLEAN NOT NULL DEFAULT FALSE
```

`is_anonymous_default` may be true only for an enabled, anonymous-allowed built-in model and is capped at three through the administrator service. Add an index for default rows. The public query still joins `connections.is_builtin = true`; indexes are execution aids, not authorization rules. Public ordering puts defaults first, followed by the existing stable ordering fields.

### `anonymous_request_leases`

| Column | Type/constraint | Purpose |
|---|---|---|
| `client_key_hash` | `VARCHAR(64) NOT NULL` | HMAC-derived client bucket; never a raw IP. |
| `slot_no` | `SMALLINT NOT NULL` | One of the bounded concurrency slots for the client bucket. |
| `lease_id` | `UUID NOT NULL` | Identifies the owner of a claimed slot. |
| `expires_at` | `TIMESTAMPTZ NOT NULL` | Allows abandoned streams to recover capacity. |
| `created_at` | `TIMESTAMPTZ NOT NULL` | Operational observation. |

Primary key: `(client_key_hash, slot_no)`. Add an expiry index. Claim/release operations are atomic SQL updates/inserts; they must not use an application-level distributed lock. This table is ephemeral protection state and contains no conversation data.

### `anonymous_conversation_imports`

| Column | Type/constraint | Purpose |
|---|---|---|
| `id` | `UUID PRIMARY KEY` | Import record identity. |
| `user_id` | `UUID NOT NULL REFERENCES users(id)` | Destination account. |
| `source_conversation_id` | `UUID NOT NULL` | Stable browser-local conversation identity. |
| `session_id` | `UUID NULL REFERENCES chat_sessions(id)` | Imported account session; nullable while a transaction is being completed. |
| `source_digest` | `VARCHAR(64) NOT NULL` | Canonical local payload digest for conflict detection. |
| `status` | `VARCHAR(32) NOT NULL` | `IMPORTED`, `SKIPPED`, or `FAILED`. An in-progress row is not committed outside its transaction. |
| `last_error` | `VARCHAR(1000) NULL` | Safe, user-actionable failure code/message; no provider payload. |
| `created_at` / `updated_at` | `TIMESTAMPTZ NOT NULL` | Lifecycle timestamps. |
| `synced_at` | `TIMESTAMPTZ NULL` | Successful import timestamp. |

Unique constraints: `(user_id, source_conversation_id)` and `session_id` when non-null. The transaction reserves the source identity, creates the session/turns/snapshots, and marks the import in one transaction. No conversation body is stored in this table; it remains in the normal authenticated tables after import.

### `admin_model_bulk_operations`

| Column | Type/constraint | Purpose |
|---|---|---|
| `id` | `UUID PRIMARY KEY` | Preview and execution identity. |
| `admin_user_id` | `UUID NOT NULL REFERENCES users(id)` | Administrator who previewed the operation. |
| `action` | `VARCHAR(32) NOT NULL` | `ALLOW_ANONYMOUS`, `REVOKE_ANONYMOUS`, `SHOW`, `HIDE`, or `DELETE`. |
| `selection_mode` | `VARCHAR(16) NOT NULL` | `IDS` or `FILTER`. |
| `selection_filter` | `JSONB NULL` | Validated filter and exclusions for traceability; never contains secrets. |
| `status` | `VARCHAR(24) NOT NULL` | `PREVIEWED`, `EXECUTING`, `COMPLETED`, `PARTIAL`, or `EXPIRED`. |
| `target_count` | `INTEGER NOT NULL` | Frozen target count. |
| `changed_count` / `already_satisfied_count` / `failed_count` | `INTEGER NOT NULL` | Aggregate outcomes. |
| `idempotency_key_hash` | `VARCHAR(64) NULL` | HMAC/hash of the caller's idempotency key. |
| `created_at` / `expires_at` / `completed_at` | `TIMESTAMPTZ` | Operation lifecycle. |

### `admin_model_bulk_operation_items`

| Column | Type/constraint | Purpose |
|---|---|---|
| `operation_id` | `UUID NOT NULL REFERENCES admin_model_bulk_operations(id)` | Parent operation. |
| `configured_model_id` | `UUID NOT NULL` | Target ID without a foreign key, so a later deletion remains reportable. |
| `model_id_snapshot` / `display_name_snapshot` / `connection_label_snapshot` | `VARCHAR(...) NOT NULL` | Safe confirmation/audit context captured at preview time. |
| `previous_is_enabled` / `previous_is_anonymous_allowed` | `BOOLEAN NULL` | Before-state for outcome diagnostics. |
| `outcome` | `VARCHAR(24) NOT NULL` | `PENDING`, `CHANGED`, `ALREADY_SATISFIED`, `ALREADY_DELETED`, or `FAILED`. |
| `error_code` / `error_message` | `VARCHAR(...) NULL` | Safe per-item failure detail. |
| `processed_at` | `TIMESTAMPTZ NULL` | Item completion timestamp. |

Primary key: `(operation_id, configured_model_id)`. Add an index on `(operation_id, outcome)`. The operation and item rows retain only safe metadata; API keys, endpoints, custom parameters, prompts, and provider response bodies are excluded.

## Browser-local model

`localStorage` uses one versioned envelope, for example `octopus.anonymous-conversations.v1`:

```json
{
  "schemaVersion": 1,
  "conversations": [
    {
      "id": "browser-conversation-uuid",
      "title": "first prompt-derived title",
      "createdAt": "2026-09-02T00:00:00.000Z",
      "updatedAt": "2026-09-02T00:00:00.000Z",
      "syncStatus": "LOCAL_ONLY",
      "turns": [
        {
          "id": "browser-turn-uuid",
          "clientRequestId": "browser-request-uuid",
          "promptText": "...",
          "createdAt": "2026-09-02T00:00:00.000Z",
          "responses": [
            {
              "configuredModelId": "configured-model-uuid",
              "modelId": "provider-model-id",
              "modelDisplayName": "safe display name snapshot",
              "protocol": "protocol-key",
              "status": "COMPLETE",
              "responseText": "...",
              "errorMessage": null
            }
          ]
        }
      ]
    }
  ]
}
```

The implementation must validate the envelope before use, cap total serialized bytes and history size, and handle unavailable/full storage without losing the in-memory view. The digest sent to the backend is computed from canonicalized conversation data, not from a mutable timestamp or array order chosen by a different representation. No token, URL, key, private configuration, or share identifier is stored in this envelope.

## State transitions

```text
local conversation: LOCAL_ONLY
  ├─ unsupported/failed sync → LOCAL_ONLY (retain and show retry)
  └─ imported/already imported + server session id → SYNCED (local copy may be removed)

bulk operation: PREVIEWED → EXECUTING → COMPLETED | PARTIAL
                          └──────────────→ EXPIRED

anonymous lease: AVAILABLE/expired → CLAIMED → RELEASED/expired
```
