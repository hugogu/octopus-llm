# Phase 1 Data Model: Data Migration, Quest Sharing & Lifecycle

Extends the existing schema. New Flyway migrations are **forward-only** (Constitution IV) and start at
`V032` (latest existing is `V031__media.sql`). All names `snake_case`.

## Changed entity: `session_shares` (share audience scope)

| Column | Type | Notes |
|--------|------|-------|
| `scope` | `VARCHAR(20) NOT NULL DEFAULT 'authenticated'` | `authenticated` \| `public`. New shares default to `authenticated`. |

- **Backfill**: existing rows set to `public` (preserve current behaviour of already-issued links).
- Entity `share/SessionShare.kt` gains `var scope: String` (or an enum mapped to varchar).
- No change to token opacity, uniqueness, or the active-share partial unique index.

**Migration `V032__session_share_scope.sql`** (shape):
- `ALTER TABLE session_shares ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'authenticated';`
- `UPDATE session_shares SET scope = 'public';`  *(backfill existing → public)*
- `CHECK (scope IN ('authenticated','public'))`.

## New entity: `dialog_redactions` (per-Dialog deletion, append-only)

Append-only markers that hide a turn (user-prompt Dialog) or a single provider response (model-answer
Dialog) from Quest/share reads, **without** mutating the immutable `chat_turns` / `provider_responses`.

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID PK` | `gen_random_uuid()` |
| `scope` | `VARCHAR(16) NOT NULL` | `turn` \| `response` |
| `turn_id` | `UUID NOT NULL REFERENCES chat_turns(id) ON DELETE CASCADE` | the owning turn (always set) |
| `response_id` | `UUID NULL REFERENCES provider_responses(id) ON DELETE CASCADE` | set when `scope='response'` |
| `redacted_by` | `UUID NULL REFERENCES users(id) ON DELETE SET NULL` | who deleted; retained marker does not block later user deletion |
| `redacted_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | |

- **Constraints**:
  - `CHECK ((scope='turn' AND response_id IS NULL) OR (scope='response' AND response_id IS NOT NULL))`
  - `CREATE UNIQUE INDEX uq_dialog_redactions_turn ON dialog_redactions(turn_id) WHERE scope='turn';`
  - `CREATE UNIQUE INDEX uq_dialog_redactions_response ON dialog_redactions(response_id) WHERE scope='response';`
    (idempotent re-delete)
  - Index `idx_dialog_redactions_turn ON dialog_redactions(turn_id)` for read-time filtering.
- **Read semantics**: `getSession` and shared reads exclude (a) turns with a `turn` redaction, and
  (b) responses with a `response` redaction. Analytics ignores this table entirely (Constitution V).
- **Authorization**: only the Quest owner (or admin) may insert a redaction for that Quest's Dialogs.

**Migration `V033__dialog_redactions.sql`** creates the table + constraints/indexes above.

## Changed entity: `chat_sessions` (import origin metadata)

| Column | Type | Notes |
|--------|------|-------|
| `imported_from_label` | `VARCHAR(255) NULL` | generic for shared import; source author label allowed for admin artifact import |
| `imported_at` | `TIMESTAMPTZ NULL` | set when the Quest was created via import |

- Ownership stays `user_id` = importer. These columns are display-only.

**Migration `V034__quest_import_origin.sql`** adds the two nullable columns.

## Transient (not persisted) DTOs — Migration Bundle

Defined in `migration/MigrationBundle.kt`; serialized into independently encrypted structured ZIP
entries. **Not** a DB table.

```
MigrationBundle {
  formatVersion: Int            // = 1; import rejects unknown/incompatible versions
  exportedAt: Instant
  source: { instanceId?: String, version?: String }
  connections: [ ConnectionExport ]
  quests:      [ QuestExport ]
}
ConnectionExport {
  artifactConnectionId, protocol, label, baseUrl, isBuiltin,
  apiKey: String,              // exists only inside authenticated-encrypted payload / process memory
  configuredModels: [ ConfiguredModelExport ]   // model id, display name, pricing, capability, params
}
QuestExport {
  artifactQuestId, title, createdAt, originalAuthorLabel?,
  turns: [ TurnExport ]
}
TurnExport {
  artifactTurnId, sequenceNum, promptText, attachments[], selectedModelIds[],
  selectedArtifactConfiguredModelIds[], createdAt,
  responses: [ ProviderResponseExport ]   // excludes redacted Dialogs at export time
}
ProviderResponseExport { artifactResponseId, artifactConfiguredModelId, artifactConnectionId?,
  attemptNumber, modelId, modelDisplayName, protocol, connectionLabel, status, responseText,
  reasoningText, errorMessage, input/output/cache tokens, latencyMs, pricing, createdAt }
```

- Each Connection DTO is serialized to authenticated-encrypted
  `connections/<artifact_connection_id>.enc`; each Quest DTO is serialized to
  `quests/<artifact_quest_id>.enc`. Bounded independent entries keep aggregate export streaming and
  are never stored as plaintext in the artifact. `envelope.json` contains only non-sensitive
  version/KDF metadata plus the encrypted-entry inventory.
- Media: each attachment referencing a `media` object → authenticated-encrypted bytes are added at
  `media/<artifact_media_id>.enc`; the payload records MIME type, expected size, and SHA-256. Import
  verifies, re-stores via `MediaStorage`, creates new media ids owned by the importer, and rewrites
  attachment references.

## New entity: `migration_operations` (idempotency and non-secret result audit)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID PK` | operation id returned to the client |
| `actor_user_id` | `UUID NOT NULL REFERENCES users(id)` | administrator or shared-Quest importer |
| `operation_type` | `VARCHAR(32) NOT NULL` | `admin_export` \| `admin_import` \| `share_import` |
| `idempotency_key_hash` | `BYTEA NULL` | SHA-256 for imports; raw key is not stored |
| `source_digest` | `BYTEA NULL` | artifact digest or share-token/request digest; null for export audit |
| `status` | `VARCHAR(16) NOT NULL` | `in_progress` \| `succeeded` \| `failed` |
| `result` | `JSONB NOT NULL DEFAULT '{}'` | counts/created ids only; no passphrase, provider key, endpoint secret, or sensitive custom parameter |
| `created_at` / `updated_at` | `TIMESTAMPTZ NOT NULL` | lifecycle timestamps |

- Partial unique `(actor_user_id, operation_type, idempotency_key_hash) WHERE idempotency_key_hash IS
  NOT NULL` claims an import retry atomically.
- Reusing a key with a different `source_digest` returns `409 idempotency_conflict`.
- **Migration `V035__migration_operations.sql`** creates this table and indexes; it does not expand
  the business scope of `admin_audit_log`.

## New entity: `migration_staged_media` (crash-safe external-side-effect tracking)

| Column | Type | Notes |
|--------|------|-------|
| `operation_id` | `UUID NOT NULL REFERENCES migration_operations(id) ON DELETE CASCADE` | owning import |
| `media_id` | `UUID NOT NULL` | preallocated final opaque media id |
| `storage_backend` | `VARCHAR(16) NOT NULL` | selected backend |
| `storage_key` | `TEXT NOT NULL` | deterministic `<media_id>.<ext>` key recorded before write |
| `created_at` | `TIMESTAMPTZ NOT NULL` | cleanup age |

- Primary key `(operation_id, media_id)`.
- Insert the staging row in a small committed transaction **before** writing the corresponding
  filesystem/S3 object. A crash before the write leaves a harmless delete-no-op; a crash after the
  write leaves enough information for cleanup.
- The artifact commit transaction creates the final `media` rows and deletes all staging rows for
  the operation. A retry-safe sweep deletes tracked objects for failed/stale operations.
- `V035__migration_operations.sql` creates both migration tables.

## Entity relationships (after this feature)

```
User 1───* ChatSession(Quest)         (owner; importer on import)
ChatSession 1───* ChatTurn            (immutable, append-only)
ChatTurn 1───* ProviderResponse       (write-once; one per model attempt)
ChatTurn 1───0..1 dialog_redactions(turn)        (hides whole turn)
ProviderResponse 1───0..1 dialog_redactions(response)  (hides one model Dialog)
ChatSession 1───* SessionShare        (now carries scope)
User 1───* Connection 1───* ConfiguredModel       (export/import targets)
ChatTurn.attachments ──ref──> media
MigrationOperation 1───* migration_staged_media (temporary cleanup ledger)
```

Not migrated: users, Connection allocations, shares/tokens, likes/reactions, anonymous visitor
state, and aggregate analytics. `client_ip`, source request-id/idempotency fields, ciphertext/IVs,
and other deployment-local identifiers are deliberately excluded. Provider response
usage/pricing/latency and retry-attempt snapshots are retained.

## Validation rules (from requirements)

- **FR-004/005/R2**: import authenticates/decrypts with the artifact passphrase and re-encrypts
  Connection keys with the target master key before persistence; plaintext never enters the
  artifact, logs, errors, audit metadata, or database.
- **FR-006/R8**: import validates ZIP safety, `formatVersion == 1`, schema, checksums, and endpoints
  before any database insert; invalid → reject, zero business rows written. Staged media is
  compensated immediately and swept after interrupted imports.
- **FR-009/R10**: imported endpoints pass `ConnectionEndpointPolicy` before commit.
- **FR-015/R9**: idempotency uniqueness prevents retry duplication across instances.
- **Unresolved historical configured-model reference**: allocate one import-local snapshot UUID,
  rewrite the copied turn/response consistently, and do not create a selectable configured model.
- **FR-021/R4**: `authenticated` shares require a principal; anonymous request returns 401 and leaks
  no content.
- **FR-030/031/R3**: redaction insert authorized to Quest owner/admin; idempotent via unique indexes.
- **Connection name collision on import** (edge case): imported Connection label suffixed (e.g.
  `" (imported)"`) when an identical label exists for the target owner; never overwrites.
