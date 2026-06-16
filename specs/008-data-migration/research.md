# Phase 0 Research: Data Migration, Quest Sharing & Lifecycle

All open questions resolved. No `NEEDS CLARIFICATION` remain (the one spec clarification — secret
handling — was decided by the user: plaintext, admin-only, warned).

## R1 — Migration bundle format

- **Decision**: A streamed **ZIP bundle** (suggested extension `.octopus`) containing:
  - `manifest.json` — `{ formatVersion: 1, exportedAt, source, connections[], quests[] }` where each
    quest embeds its turns and provider responses, and each connection embeds its configured models
    plus the **plaintext** `apiKey`.
  - `media/<media_id>.<ext>` — bytes for every media object referenced by exported turns.
- **Rationale**: ZIP streams to/from disk without buffering GB in memory; a single file is the
  simplest admin artifact; manifest+blobs cleanly separates structured data from binaries. Uses JDK
  `java.util.zip` — no new dependency (Constitution VII simplicity).
- **Alternatives**: NDJSON-only (can't carry media binaries); tar.gz (extra dep / less ubiquitous on
  Windows admins); multi-file download (worse UX, ordering/atomicity harder).

## R2 — Secret handling on export/import

- **Decision**: Export **decrypts** each Connection key via `ApiKeyEncryptionService.decrypt` and
  writes plaintext into `manifest.json`. Import **re-encrypts** with the target server's master key
  via `ApiKeyEncryptionService.encrypt` immediately, storing only ciphertext (`encrypted_key`,
  `key_iv`) — plaintext is never persisted on the target.
- **Compensating controls** (Constitution VI exception): endpoint is `ROLE_ADMIN`-only; the export
  request body must include an explicit acknowledgement token (e.g. `confirmSecretsExport: true`)
  that the UI only sets after a typed/checkbox warning; the response/file is marked sensitive; the
  action is written to `admin_audit_log`.
- **Rationale**: Master keys differ per deployment, so ciphertext is non-portable; re-encryption is
  the only way imported Connections work without manual key entry, which the user required.

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
  `authenticated | public`. `SharedSessionController` (`/api/v2/shared/{token}`) checks scope: for
  `authenticated`, require a valid auth principal (401/redirect for anonymous) and reveal nothing
  before auth; for `public`, behave as today. Owner can `PATCH` scope and revoke as today. Default for
  newly created shares = `authenticated` (more private; matches spec assumption).
- **Rationale**: Minimal, backward-compatible column; token stays opaque (VI). Existing rows get the
  safer `authenticated` default — except we keep current public behaviour for already-issued links by
  backfilling existing rows to `public` in the migration (see data-model) so we don't silently break
  live links.
- **Alternatives**: Separate ACL table / named-user allowlist — rejected (spec says "any logged-in
  user", not a named list; YAGNI per VII).

## R5 — Continue-from-share & combined-button import

- **Decision**: `POST /api/v2/shared/{token}/import` (auth required) server-side **deep-copies** the
  shared Quest into a new `chat_sessions` row owned by the caller, copying turns + provider responses
  as fresh append-only rows (new ids), skipping redacted Dialogs, and copying referenced media
  references (media objects are shared by id; no blob duplication needed within the same deployment).
  The combined-button "Import" in the sidebar accepts a share link/token and calls the same endpoint.
  Cross-deployment "import a shared link from another server" is **out of scope** for the combined
  button (that path is the admin bundle); the combined button imports shares on the *same* deployment.
- **Rationale**: Reuses the immutable-copy semantics already implied by the model; importer continues
  with their own selected models on new turns. Single endpoint serves both US2 and US3.
- **Open follow-up**: original author shown as informational metadata via R6.

## R6 — Imported-Quest origin metadata

- **Decision**: Add nullable `imported_from_label VARCHAR(255)` and `imported_at TIMESTAMPTZ` to
  `chat_sessions` (V034) to display "Imported from <author/share>" without transferring ownership.
  Ownership is always the importer (admin for bundle import, user for share import) per spec.
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

- **Decision**: Export streams the ZIP directly to the HTTP response (`StreamingResponseBody`-style),
  reading Quests/media in pages; never materializes the whole bundle in memory. Import streams the
  uploaded ZIP to a temp location, validates `formatVersion` + manifest schema first, then performs
  all inserts in **one transaction per logical batch** so a failure rolls back to no partial data
  (FR-005). Media blobs are written via the existing `MediaStorage` strategy on the target.
- **Rationale**: Meets the "potentially GB-scale, atomic, no partial state" constraints without locks
  (VII). Validation-before-insert gives clean rejection of malformed/incompatible artifacts.
- **Alternatives**: Buffer-then-insert (OOM risk on large exports — rejected).

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
