# Contract: Admin Migration (export / import)

All endpoints `ROLE_ADMIN` only. Prefix `/api/v2/admin/migration`. Error schema:
`{ "code": "...", "message": "...", "details": {} }`.

## `POST /api/v2/admin/migration/export`

Produces the migration ZIP bundle (see data-model `MigrationBundle`, `formatVersion: 1`).

**Request body**
```json
{
  "acknowledgeSensitiveExport": true,
  "passphrase": "<admin-chosen artifact passphrase>"
}
```
- `acknowledgeSensitiveExport` MUST be `true`; otherwise `400 sensitive_export_ack_required`.
- `passphrase` is REQUIRED unless the server has a configured artifact passphrase
  (`MIGRATION_ARTIFACT_PASSPHRASE`), in which case it MAY be omitted and the configured value is used.
  When supplied it MUST contain at least 16 characters; the UI asks for confirmation locally and the
  confirmation value is not sent. The passphrase is memory-only and MUST NOT be persisted, logged,
  returned, or included in audit metadata. (`400 passphrase_required` when neither is present;
  `400 passphrase_too_short` when supplied < 16 chars.)

**Response** `200 OK`
- `Content-Type: application/zip`, `Content-Disposition: attachment; filename="octopus-export-<ts>.octopus"`
- Body: streamed ZIP (`envelope.json` + authenticated-encrypted `connections/*.enc`,
  `quests/*.enc`, and `media/*.enc`). The Next same-origin proxy and backend response remain
  streaming (R8).

**Behaviour**
- Includes every user's Quests (excluding redacted Dialogs), referenced media bytes, and all
  Connections with configured models. Quest data, sensitive custom parameters, media, and provider
  keys are encrypted before response bytes are written (R2).
- Writes a non-secret `migration_operations` record with actor, status, and counts.

## `POST /api/v2/admin/migration/import`

Imports a bundle; all Quests become owned by the **calling admin**.

**Request**
- Header: `Idempotency-Key: <opaque client-generated value>` (required; at least 128 bits of
  client-generated randomness).
- `multipart/form-data` fields:
  - `file` = the `.octopus` ZIP.
  - `passphrase` = the artifact passphrase (memory-only; never logged/persisted). MAY be omitted when
    the server's configured `MIGRATION_ARTIFACT_PASSPHRASE` matches the artifact; if omitted and the
    configured passphrase fails authentication, the response is `400 invalid_artifact_credentials`.

**Response** `200 OK`
```json
{
  "questsImported": 42,
  "connectionsImported": 7,
  "connectionsRenamed": 1,
  "mediaImported": 130,
  "formatVersion": 1
}
```
If the same idempotent import is already running, return `202 Accepted`
`{ "operationId": "…", "status": "in_progress" }` with `Retry-After`; a later retry returns the
recorded `200` result.

**Errors**
- `400 invalid_bundle` — not a ZIP / missing envelope or encrypted entry / schema mismatch. No data
  written.
- `400 invalid_artifact_credentials` — wrong passphrase or failed authentication/tamper check. No
  data written; response does not distinguish wrong passphrase from modified ciphertext.
- `400 unsafe_archive` — path traversal, duplicate entry, excessive entry count, or expanded-size
  limit violation.
- `409 incompatible_version` — `formatVersion` unknown/incompatible. No data written.
- `409 idempotency_conflict` — the idempotency key was already used for different request material.
- `413 bundle_too_large` — exceeds configured compressed, per-entry, or expanded-size limit.

**Behaviour (R8 atomicity)**
- Stream upload to a bounded temp file; validate ZIP safety, `formatVersion`, schema, authenticated
  decryption, media sizes/checksums, reference integrity, and every imported endpoint through
  `ConnectionEndpointPolicy` **before** any database insert.
- Insert Quests/turns/responses as fresh append-only rows owned by the admin; set
  `imported_from_label` / `imported_at`.
- Build artifact-id → new-id maps for Connections, configured models, Quests, turns, responses, and
  media; rewrite `selected_configured_model_ids` and attachment references.
- Re-encrypt each Connection key with the **target** master key before persistence (R2).
- Connection label collision → import under a suffixed label (`connectionsRenamed` counts these);
  never overwrite existing Connections.
- Do not recreate source users, Connection allocations, shares/tokens, reactions, anonymous visitor
  state, or aggregate analytics.
- Stage media under new opaque ids before one database transaction. On failure, delete staged blobs;
  an idempotent orphan sweep removes leftovers after process interruption.
- On any failure → roll back all Quest/Connection/configured-model/media rows. No user-visible
  partial import is permitted.
- Same actor + source digest + `Idempotency-Key` returns 202 while the original is in progress and
  the original final result afterward; it creates no duplicate rows.
- Writes status/counts to `migration_operations`; no passphrase, key material, sensitive custom
  parameter, or plaintext endpoint credential may enter the record.
