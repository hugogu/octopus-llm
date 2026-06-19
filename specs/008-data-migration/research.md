# Phase 0 Research: Data Migration, Quest Sharing & Lifecycle

All open questions resolved. No `NEEDS CLARIFICATION` remain.

## R1 — Migration bundle format

- **Decision**: A streamed **ZIP bundle** (suggested extension `.octopus`) containing:
  - `envelope.json` — non-sensitive format/crypto metadata (`formatVersion`, KDF salt and encryption
    metadata); it contains no Quest content, Connection data, passphrase, or key material.
  - `connections/<artifact_connection_id>.enc` plus `quests/<artifact_quest_id>.enc` — independently
    authenticated-encrypted structured entries containing Connection definitions, configured
    models, Quest snapshots, artifact-local reference ids, and provider secrets.
  - `media/<artifact_media_id>.enc` — authenticated-encrypted bytes for every referenced media
    object, with size and SHA-256 recorded in the encrypted payload.
- **Rationale**: ZIP streams to/from disk without buffering GB in memory; a single file is the
  simplest admin artifact; envelope + encrypted entries cleanly separates metadata from bounded
  structured/binary chunks. Uses JDK `java.util.zip` — no new packaging dependency.
- **Alternatives**: NDJSON-only (can't carry media binaries); tar.gz (extra dep / less ubiquitous on
  Windows admins); multi-file download (worse UX, ordering/atomicity harder).

## R2 — Artifact encryption and secret handling

- **Decision**: The admin supplies a strong artifact passphrase for export and import. Export
  decrypts each Connection key only in memory and encrypts each bounded structured/media entry
  before writing it using Spring Security Crypto's password-based authenticated encryption. Import
  authenticates/decrypts one entry at a time and immediately re-encrypts each provider key with the
  target deployment's `ApiKeyEncryptionService`.
- Passphrases and decrypted provider keys are never persisted, logged, returned, included in errors,
  or included in operation/audit metadata. Sensitive configured-model custom parameters travel only
  inside the encrypted payload.
- **Passphrase source**: a server-configured environment variable (e.g. `MIGRATION_ARTIFACT_PASSPHRASE`)
  supplies a default so routine export/import need not prompt the admin; when it is unset the admin
  sets one on export and enters it on import. An explicitly supplied passphrase always overrides the
  configured default, so an artifact from a deployment with a different configured passphrase can still
  be imported. The configured value is treated like other deployment secrets (`ENCRYPTION_MASTER_KEY`,
  `JWT_SECRET`): held in memory at runtime, never logged or echoed back.
- **Rationale**: Server-master-key ciphertext is not portable across deployments. A passphrase
  (configured or admin-supplied) provides portability without exposing plaintext credentials in an API
  response, satisfying Constitution VI.

## R3 — Per-Dialog deletion model (immutability-preserving)

- **Decision**: New append-only table `dialog_redactions(id, scope, turn_id, response_id, redacted_by,
  redacted_at)` where `scope ∈ {turn, response}`.
  - Deleting a **user-prompt Dialog** → insert a `turn` redaction for that `turn_id` (hides the turn
    and, by reads, its responses).
  - Deleting a **model-response Dialog** → insert a `response` redaction for that `response_id`.
- Quest reads (`getSession`) and shared reads exclude redacted turns/responses. Analytics queries are
  unchanged (they read `provider_responses` directly, ignoring redactions).
- **Rationale**: Honors Constitution IV — no UPDATE/DELETE on `provider_responses`/`chat_turns`. Marker
  rows are cheap and auditable. Re-deletion is idempotent (unique constraint on the target id).
- **Alternatives**: `deleted_at` columns on the immutable tables (mutates write-once rows — rejected);
  physically deleting rows (destroys analytics + immutability — rejected).

## R4 — Share audience scope

- **Decision**: Add `scope VARCHAR(20) NOT NULL DEFAULT 'authenticated'` to `session_shares`, values
  `authenticated | public`. A centralized share-access check applies before every
  `/api/v2/shared/{token}/...` read/reaction/import operation: for `authenticated`, require a valid
  principal (401/redirect for anonymous) and reveal nothing before auth; for `public`, behave as
  today. Redacted response ids are not valid reaction targets. Owner can `PATCH` scope and revoke as
  today. Default for newly created shares = `authenticated` (more private; matches spec assumption).
- **Rationale**: Minimal, backward-compatible column; token stays opaque (VI). Existing rows get the
  safer `authenticated` default — except we keep current public behaviour for already-issued links by
  backfilling existing rows to `public` in the migration (see data-model) so we don't silently break
  live links.
- **Alternatives**: Separate ACL table / named-user allowlist — rejected (spec says "any logged-in
  user", not a named list; YAGNI per VII).

## R5 — Continue-from-share & combined-button import

- **Decision**: `POST /api/v2/shared/{token}/import` (auth required) server-side **deep-copies** the
  shared Quest into a new `chat_sessions` row owned by the caller, copying turns + provider responses
  as fresh append-only rows (new ids), skipping redacted Dialogs, and cloning referenced media into
  new `media` rows/storage objects owned by the importer.
  The combined-button "Import" in the sidebar accepts a share link/token and calls the same endpoint.
  Cross-deployment "import a shared link from another server" is **out of scope** for the combined
  button (that path is the admin bundle); the combined button imports shares on the *same* deployment.
- **Rationale**: Reuses immutable-copy semantics and makes the imported Quest independent. Reusing
  source media ids is incorrect because deleting the source Quest cascades its media rows and would
  break the imported Quest. The importer continues with their own selected models on new turns.
  Single endpoint serves both US2 and US3.
- Shares/tokens, likes/reactions, anonymous visitor state, and source ownership are not copied.
- Shared imports use a generic origin label; admin artifacts may preserve a source author label as
  informational metadata without recreating source ownership.

## R6 — Imported-Quest origin metadata

- **Decision**: Add nullable `imported_from_label VARCHAR(255)` and `imported_at TIMESTAMPTZ` to
  `chat_sessions` (V034). Shared imports use a generic "Imported from a shared Quest" label; admin
  artifacts may use a source author label. Ownership is always the importer.
- **Rationale**: Lightweight, satisfies the "informational metadata" assumption; avoids a join table.

## R7 — Chat → Quest rename scope

- **Decision**: Rename **user-facing copy and icons** to "Quest" with task-oriented iconography
  (lucide `ListChecks`/`Swords`/`Target`-style instead of `MessageSquare`). Rename the frontend route
  segment `/(app)/chat` → `/(app)/quests` with a redirect from `/chat` (and `/chat/[id]` →
  `/quests/[id]`) to preserve existing bookmarks. **Keep** backend API paths (`/api/v2/chat/*`) and DB
  table names (`chat_sessions`, etc.) unchanged.
- **Rationale**: Renaming `/api/v2/chat/*` is a breaking API change (Constitution II → would force a
  major version bump and break external clients); table renames are churn with no user benefit and
  risk migration breakage (IV forward-only). Internal names are not user-facing. Route rename + copy/
  icon swap satisfies FR-040/041/042 with bounded risk.
- **Alternatives**: Full rename incl. API/DB — rejected (breaking, high-risk, no user value).

## R8 — Bundle size / streaming & atomicity

- **Decision**: Export streams the ZIP directly to the WebFlux response, reading Quests/media in
  pages; never materializes the whole artifact in memory. The same-origin Next proxy must forward
  request and response streams without `request.arrayBuffer()`. Import streams the upload to a
  bounded temp file, rejects unsafe ZIPs (path traversal, duplicate entries, entry/expanded-size
  limits), authenticates/decrypts every payload, validates schema/version/checksums/endpoints, and
  only then stages media under new opaque ids. One database transaction inserts the complete
  artifact and references already-present staged media. Before each object write, a
  `migration_staged_media` cleanup-ledger row records its deterministic final key. On failure,
  staged blobs are deleted; a retry-safe sweep removes tracked leftovers after process interruption.
- **Rationale**: Database rollback cannot roll back S3/filesystem writes. Staging media before one DB
  commit guarantees no committed rows reference missing blobs; compensation/orphan cleanup handles
  external side effects without distributed locks. Validation-before-staging rejects malformed,
  incompatible, tampered, or unsafe artifacts cleanly.
- **Alternatives**: Buffer-then-insert (OOM risk on large exports — rejected).

## R9 — Idempotent import retries

- **Decision**: Admin artifact import and shared-Quest import require `Idempotency-Key`. A
  `migration_operations` row stores actor, operation type, source digest/token digest, key hash,
  status, and non-sensitive result counts. A uniqueness constraint makes retry claiming atomic.
  The same key with different request material is `409 idempotency_conflict`; the same request
  returns the recorded in-progress/final result. A deliberate duplicate import uses a new key.
- **Rationale**: Both imports create new rows, so transport retries otherwise duplicate data.
  Database uniqueness provides horizontal safety without a distributed lock.

## R10 — Imported Connection safety and reference mapping

- **Decision**: Bundle DTOs carry artifact-local ids for Connections, configured models, Quests,
  turns, responses, and media. Import builds explicit old→new maps so
  `selected_configured_model_ids`, response snapshot ordering, and media references remain coherent.
  A historical configured-model reference with no exported Connection is mapped to a new
  import-local snapshot UUID used only by the copied turn/response; it is not selectable for future
  dispatch.
  Every Connection `baseUrl` is validated with existing `ConnectionEndpointPolicy` before any
  commit; production imports require public HTTPS endpoints. Provider transports retain manual
  redirect handling and revalidation before dispatch.
- **Rationale**: Literal provider model ids are display/request metadata, while configured-model UUID
  is the operational identity. Import must not accidentally preserve source deployment UUIDs or
  bypass the same SSRF policy used by normal Connection creation.

## Existing-code anchors (for implementers)

- Keys: `userconfig/ApiKeyEncryptionService.kt` (`encrypt`/`decrypt`), `connection/Connection.kt`
  (`encryptedKey`/`keyIv`), `connection/ConnectionService.kt` (decrypt usage at ~L101).
- Quests/Dialogs: `chat/ChatSession.kt`, `chat/ChatTurn.kt`, `chat/ProviderResponse.kt`
  (`latestProviderResponses` ordering helper), `chat/ChatControllerV2.kt` (`/api/v2/chat/sessions`).
- Shares: `share/SessionShare.kt`, `share/ShareControllerV2.kt`
  (`/api/v2/chat/sessions/{id}/shares`), `share/SharedSessionController.kt` (`/api/v2/shared/{token}`).
- Admin pattern: `admin/StorageSettingsController.kt` + `components/admin/AdminShell.tsx` (new admin
  page mirrors this).
- Confirmations: `@/lib/ui/confirm` `confirmDialog`. Latest migration: `V031__media.sql` → new ones
  start at `V032`.
