# Quickstart: Validating 008 — Data Migration, Quest Sharing & Lifecycle

End-to-end validation scenarios. Run the stack via Docker Compose (frontend `:3001`, backend `:8080`,
db `:5432`) — check `docker compose ps` first; rebuild only what changed. Backend gate
`cd backend && ./gradlew build`; frontend `cd frontend && npm run build && npm run lint &&
npm run test:run`. Visually verify every UI surface
(Constitution VIII) before marking done. Contracts: [admin-migration](./contracts/admin-migration.md),
[quest-dialogs](./contracts/quest-dialogs.md), [shares-scope](./contracts/shares-scope.md),
[shared-import](./contracts/shared-import.md). Schema: [data-model](./data-model.md).

## Prerequisites

- Two deployments (or two DBs) for the migration round-trip: **source** (with several users' Quests +
  Connections) and **target** (empty). For a single-box check, export then import into a fresh DB.
- An admin account on each (see `ADMIN_BOOTSTRAP_EMAIL`).
- A normal user account for the share-import scenarios.
- Optionally set `MIGRATION_ARTIFACT_PASSPHRASE` on both deployments to skip the passphrase prompt;
  leave it unset on at least one run to exercise the manual passphrase path.

## Scenario A — Admin full migration round-trip (US1, FR-001..009, FR-015)

1. On **source**, sign in as admin → open **Admin ▸ Migration**.
2. Click **Export all data**; acknowledge the styled sensitive-data warning, then set/confirm a strong
   artifact passphrase (or, if `MIGRATION_ARTIFACT_PASSPHRASE` is configured, export without a prompt),
   and export.
   - **Expect**: a `.octopus` ZIP downloads; `migration_operations` records non-secret status/counts.
   - **Inspect**: `envelope.json` contains only version/crypto metadata; Quest/Connection payload and
     media are encrypted. Searching the artifact for a known provider key or Quest text finds no
     plaintext match.
3. On **target** (empty), sign in as admin → **Admin ▸ Migration ▸ Import**, upload the ZIP.
   Enter the artifact passphrase.
   - **Expect** `200` with counts; every Quest now appears under the admin's Quest list with full
     history + media rendering; Connections appear in settings and are immediately usable (keys
     re-encrypted with target master key — no re-entry).
   - **Verify SC-001/SC-002**: 100% of Quests present and owned by admin; histories complete.
4. Upload with a wrong passphrase, then upload a corrupted/old-version/unsafe ZIP → **Expect**
   `400 invalid_artifact_credentials`, `400 invalid_bundle`/`unsafe_archive`, or
   `409 incompatible_version`, and **no** business rows or durable media references.
5. Retry the successful request with the same `Idempotency-Key` → **Expect** the original result and
   unchanged Quest/Connection/media counts. Reuse that key with a different artifact → `409`.
6. Include a Connection endpoint rejected by `ConnectionEndpointPolicy` → **Expect** whole-artifact
   rejection and no partial import.

## Scenario B — Continue from a shared Quest (US2, FR-010..013)

1. As user A, open a Quest → **Share** → scope **Public** → copy link.
2. In a logged-out browser, open the link → **Expect** the "Import to continue" affordance is visible.
3. Click Import while logged out → **Expect** redirect to sign-in/register; after auth the import
   completes and lands on a **new Quest owned by the importer** with A's history.
4. Submit a new prompt in the imported Quest → **Expect** it streams from the importer's own selected
   models and appends (FR-012). **Verify SC-003**: import + first prompt < 30 s.
5. Delete the source Quest after import → **Expect** the imported Quest's media still renders because
   the import owns cloned media objects rather than source references.
6. Replay the same import with the same `Idempotency-Key` → **Expect** no duplicate Quest/media.

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

- [ ] `cd backend && ./gradlew build` green (incl. migration validation + endpoint integration tests).
- [ ] `cd frontend && npm run build && npm run lint && npm run test:run` green.
- [ ] Flyway `V032`–`V035` apply cleanly on a fresh DB and on a populated one.
- [ ] Every new/changed page visually verified; no native dialogs; reachable via in-app nav.
- [ ] `migration_operations` records non-secret export/import/share-import status and counts.
- [ ] Browser-facing artifact upload/download passes through the same-origin Next proxy without
      buffering the complete artifact; an HTTP test verifies the exact upstream path.
- [ ] Wrong-passphrase, tamper, unsafe-ZIP, endpoint-policy, rollback, idempotency, and interrupted
      media-staging cleanup tests pass.
