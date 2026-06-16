# Quickstart: Validating 008 — Data Migration, Quest Sharing & Lifecycle

End-to-end validation scenarios. Run the stack via Docker Compose (frontend `:3001`, backend `:8080`,
db `:5432`) — check `docker compose ps` first; rebuild only what changed. Backend gate
`./gradlew build`; frontend `npx tsc --noEmit` + `npx vitest run`. Visually verify every UI surface
(Constitution VIII) before marking done. Contracts: [admin-migration](./contracts/admin-migration.md),
[quest-dialogs](./contracts/quest-dialogs.md), [shares-scope](./contracts/shares-scope.md),
[shared-import](./contracts/shared-import.md). Schema: [data-model](./data-model.md).

## Prerequisites

- Two deployments (or two DBs) for the migration round-trip: **source** (with several users' Quests +
  Connections) and **target** (empty). For a single-box check, export then import into a fresh DB.
- An admin account on each (see `ADMIN_BOOTSTRAP_EMAIL`).
- A normal user account for the share-import scenarios.

## Scenario A — Admin full migration round-trip (US1, FR-001..008)

1. On **source**, sign in as admin → open **Admin ▸ Migration**.
2. Click **Export all data**; confirm the styled warning about live keys (sets `confirmSecretsExport`).
   - **Expect**: a `.octopus` ZIP downloads; `admin_audit_log` has `migration_export`.
   - **Inspect**: `manifest.json` lists all Quests + Connections; `media/` holds referenced blobs.
3. On **target** (empty), sign in as admin → **Admin ▸ Migration ▸ Import**, upload the ZIP.
   - **Expect** `200` with counts; every Quest now appears under the admin's Quest list with full
     history + media rendering; Connections appear in settings and are immediately usable (keys
     re-encrypted with target master key — no re-entry).
   - **Verify SC-001/SC-002**: 100% of Quests present and owned by admin; histories complete.
4. Upload a corrupted/old-version file → **Expect** `400 invalid_bundle` / `409 incompatible_version`
   and **no** partial data (FR-005).

## Scenario B — Continue from a shared Quest (US2, FR-010..013)

1. As user A, open a Quest → **Share** → scope **Public** → copy link.
2. In a logged-out browser, open the link → **Expect** the "Import to continue" affordance is visible.
3. Click Import while logged out → **Expect** redirect to sign-in/register; after auth the import
   completes and lands on a **new Quest owned by the importer** with A's history.
4. Submit a new prompt in the imported Quest → **Expect** it streams from the importer's own selected
   models and appends (FR-012). **Verify SC-003**: import + first prompt < 30 s.

## Scenario C — Combined New/Import button (US3, FR-014)

1. In the Quest sidebar, confirm the primary control is a **combined button**: primary **New Quest**,
   attached secondary **Import**.
2. Use **Import**, paste a share link you can access → **Expect** a new Quest appears and opens.

## Scenario D — Share scopes (US4, FR-020..023)

1. Create a share with scope **Logged-in users only** (default).
2. Open the link **anonymous** → **Expect** auth required; **no** Quest content/owner identity shown
   (verify response body + page). **Verify SC-005**.
3. Open the same link **logged in** → **Expect** the Quest renders.
4. Owner switches scope to **Public** → anonymous load now renders. Revoke → link 404s.

## Scenario E — Per-Dialog deletion with confirmation (US5, FR-030..033)

1. In a Quest turn with multiple model responses, delete one model's response → confirm in the styled
   dialog → **Expect** that response disappears; siblings remain; reload persists.
2. Delete a user-prompt Dialog → confirm → **Expect** the whole turn disappears from the Quest and any
   share of it.
3. Cancel a delete confirmation → **Expect** nothing changes. **Verify SC-004**: every destructive
   action confirmed; no native browser dialog anywhere.
4. Confirm analytics totals are unchanged after deletion (redaction preserves snapshots).

## Scenario F — Quest reframing (US6, FR-040..042)

1. Walk the app (sidebar, headers, share page, admin, empty states) → **Expect** every former
   "Chat"/"Conversation" label now reads **Quest** with a task-oriented icon. **Verify SC-006**.
2. `/chat` and `/chat/<id>` redirect to `/quests` equivalents (existing bookmarks keep working).
3. No copy implies the platform itself can converse.

## Gate checklist before "done"

- [ ] `./gradlew build` green (incl. new migration validation + integration tests per new endpoint).
- [ ] `npx tsc --noEmit` zero errors; `npx vitest run` green.
- [ ] Flyway `V032`–`V034` apply cleanly on a fresh DB and on a populated one.
- [ ] Every new/changed page visually verified; no native dialogs; reachable via in-app nav.
- [ ] `admin_audit_log` records export + import.
