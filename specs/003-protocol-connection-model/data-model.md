# Data Model

## ProtocolDefinition

Static application data, not persisted.

| Field | Type | Rules |
|---|---|---|
| `id` | String | Unique protocol identifier |
| `displayName` | String | UI label |
| `defaultBaseUrl` | String? | Optional form default |
| `baseline` | CapabilityMatrix | Conservative protocol defaults |

## Connection

| Column | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id` | UUID | FK `users(id)` ON DELETE CASCADE |
| `protocol` | VARCHAR(50) | Must match a supported protocol |
| `label` | VARCHAR(255) | Optional |
| `base_url` | VARCHAR(500) | Normalized, endpoint-policy approved |
| `encrypted_key` | BYTEA | AES-256-GCM ciphertext |
| `key_iv` | BYTEA | Encryption IV |
| `created_at` | TIMESTAMPTZ | Immutable |
| `updated_at` | TIMESTAMPTZ | Updated on mutation |

Constraints:

- `UNIQUE(user_id, id)` supports composite ownership FK.
- Key material is excluded from API DTOs.

## ConfiguredModel

| Column | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key and operational model identity |
| `user_id` | UUID | Owner |
| `connection_id` | UUID | Parent connection |
| `model_id` | VARCHAR(255) | Literal provider model value |
| `display_name` | VARCHAR(255) | User-facing name |
| `capability_overrides` | JSONB | Validated partial CapabilityMatrix |
| `custom_params` | JSONB | Adapter request parameters |
| `is_enabled` | BOOLEAN | Defaults true |
| `sort_order` | INTEGER | Non-negative |
| `created_at` | TIMESTAMPTZ | Immutable |
| `updated_at` | TIMESTAMPTZ | Updated on mutation |

Constraints:

- Composite FK `(user_id, connection_id) -> connections(user_id, id)` ON DELETE CASCADE.
- Index `(user_id, is_enabled, sort_order, created_at, id)`.
- Duplicate `model_id` values are permitted.

## ProviderResponse Snapshot Changes

Add:

| Column | Type | Rules |
|---|---|---|
| `configured_model_id` | UUID | Immutable snapshot identity; no cascading FK |
| `model_display_name` | VARCHAR(255) | Immutable snapshot |
| `protocol` | VARCHAR(50) | Immutable snapshot |
| `connection_label` | VARCHAR(255) | Nullable immutable snapshot |

Change:

- Expand `model_id` to `VARCHAR(255)`.
- Replace `UNIQUE(turn_id, model_id)` with `UNIQUE(turn_id, configured_model_id)`.
- Remove FK from `model_id` to `model_definitions`.

## ChatTurn Changes

- Retain `selected_model_ids TEXT[]` as immutable display/provider snapshots.
- Add `selected_configured_model_ids UUID[]`.
- New v2 turns populate both arrays in request order.

## UserPreference Changes

- Add nullable `last_selected_configured_model_id UUID`.
- Do not add a cascading FK; deleting configuration clears the preference in application logic.
- Remove `last_selected_model_id` only after the v2 frontend no longer reads it.

## Migration Audit

`configuration_migration_audit` records:

- migrated connection count
- migrated configured-model count
- skipped model configs without keys
- unmapped provider IDs
- execution timestamp

No API key content is recorded.

## V017 Validation

Before dropping old configuration tables:

1. Migrated connection count equals old provider key count for mapped protocols.
2. Every migrated configured model has matching owner and connection.
3. Every old usable model config has one migrated configured model.
4. Historical response count is unchanged.
5. Every historical response has non-null model and protocol snapshots.

Any failed assertion aborts the migration transaction.
