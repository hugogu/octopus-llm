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
| `redacted_by` | `UUID NOT NULL REFERENCES users(id)` | who deleted |
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
| `imported_from_label` | `VARCHAR(255) NULL` | informational, e.g. "Imported from a shared Quest" / original author display name |
| `imported_at` | `TIMESTAMPTZ NULL` | set when the Quest was created via import |

- Ownership stays `user_id` = importer. These columns are display-only.

**Migration `V034__quest_import_origin.sql`** adds the two nullable columns.

## Transient (not persisted) DTOs — Migration Bundle

Defined in `migration/MigrationBundle.kt`; serialized into `manifest.json` inside the ZIP. **Not** a
DB table.

```
MigrationBundle {
  formatVersion: Int            // = 1; import rejects unknown/incompatible versions
  exportedAt: Instant
  source: { instanceId?: String, version?: String }
  connections: [ ConnectionExport ]
  quests:      [ QuestExport ]
}
ConnectionExport {
  protocol, label, baseUrl, isBuiltin,
  apiKey: String,              // PLAINTEXT (Constitution VI exception; admin-only, warned)
  configuredModels: [ ConfiguredModelExport ]   // model id, display name, pricing, capability, params
}
QuestExport {
  title, createdAt, originalAuthorLabel?,
  turns: [ TurnExport ]
}
TurnExport {
  sequenceNum, promptText, attachments[], selectedModelIds[], selectedConfiguredModelIds[], createdAt,
  responses: [ ProviderResponseExport ]   // excludes redacted Dialogs at export time
}
ProviderResponseExport { modelId, modelDisplayName, protocol, status, responseText, reasoningText,
  errorMessage, tokens…, latencyMs, pricing…, createdAt }
```

- Media: each attachment referencing a `media` object → its bytes are added to the ZIP at
  `media/<media_id>` and the manifest keeps the reference; import re-stores via `MediaStorage` and
  rewrites references to the new media ids.

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
```

## Validation rules (from requirements)

- **FR-005/R8**: import validates `formatVersion == 1` and manifest schema **before** any insert;
  invalid → reject, zero rows written.
- **FR-004/R2**: imported Connection keys re-encrypted with target master key before persistence;
  plaintext never stored.
- **FR-021/R4**: `authenticated` shares require a principal; anonymous request returns 401 and leaks
  no content.
- **FR-030/031/R3**: redaction insert authorized to Quest owner/admin; idempotent via unique indexes.
- **Connection name collision on import** (edge case): imported Connection label suffixed (e.g.
  `" (imported)"`) when an identical label exists for the target owner; never overwrites.
