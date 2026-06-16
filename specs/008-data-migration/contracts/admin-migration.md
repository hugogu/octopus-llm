# Contract: Admin Migration (export / import)

All endpoints `ROLE_ADMIN` only. Prefix `/api/v2/admin/migration`. Error schema:
`{ "code": "...", "message": "...", "details": {} }`.

## `POST /api/v2/admin/migration/export`

Produces the migration ZIP bundle (see data-model `MigrationBundle`, `formatVersion: 1`).

**Request body**
```json
{ "confirmSecretsExport": true }
```
- `confirmSecretsExport` MUST be `true` — the UI sets it only after the admin acknowledges a styled
  warning that the bundle contains live plaintext provider keys. If missing/false → `403`
  `{ "code": "secrets_ack_required" }`, no bundle produced.

**Response** `200 OK`
- `Content-Type: application/zip`, `Content-Disposition: attachment; filename="octopus-export-<ts>.octopus"`
- Body: streamed ZIP (`manifest.json` + `media/…`). Never buffered fully in memory (R8).

**Behaviour**
- Includes every user's Quests (excluding redacted Dialogs), referenced media bytes, and all
  Connections with configured models + **plaintext** keys (decrypted at export time, R2).
- Writes an `admin_audit_log` entry `migration_export` (counts, actor).

## `POST /api/v2/admin/migration/import`

Imports a bundle; all Quests become owned by the **calling admin**.

**Request**: `multipart/form-data` with `file` = the `.octopus` ZIP.

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

**Errors**
- `400 invalid_bundle` — not a ZIP / missing manifest / schema mismatch. No data written.
- `409 incompatible_version` — `formatVersion` unknown/incompatible. No data written.
- `413 bundle_too_large` — exceeds configured limit (if any).

**Behaviour (R8 atomicity)**
- Validate `formatVersion` + manifest schema **before** any insert.
- Insert Quests/turns/responses as fresh append-only rows owned by the admin; set
  `imported_from_label` / `imported_at`.
- Re-encrypt each Connection key with the **target** master key before persistence (R2); plaintext
  never persisted.
- Connection label collision → import under a suffixed label (`connectionsRenamed` counts these);
  never overwrite existing Connections.
- Re-store media via `MediaStorage`; rewrite attachment references to new media ids.
- On any failure mid-batch → roll back to **no partial data**; respond with the relevant error.
- Writes `admin_audit_log` entry `migration_import`.
