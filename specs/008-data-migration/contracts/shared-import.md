# Contract: Continue-from-share import (US2 / US3)

Lets a logged-in user copy a Quest they can view (via share token) into their own account to continue
it. Same endpoint backs both the share-page "Import to continue" button and the sidebar combined
button's "Import" action (same-deployment shares).

## `POST /api/v2/shared/{token}/import` (auth required)

**Headers**: `Idempotency-Key: <opaque client-generated value>` with at least 128 bits of
client-generated randomness is required.

**Auth**: a valid principal is required. Anonymous → **401** `{ "code": "auth_required" }`; the UI then
sends the user to sign in / register and resumes the import (FR-013).

**Access**: caller must be allowed to view the share:
- `public` share → any authenticated user may import.
- `authenticated` share → any authenticated user may import.
- revoked/unknown token → **404** `{ "code": "share_not_found" }`.

**Response** `201 Created`
```json
{ "sessionId": "<new quest id>", "title": "…", "importedFromLabel": "Imported from a shared Quest" }
```
An idempotent replay returns the same body with `200 OK`. If the original import is still running,
return `202 Accepted` with operation id/status and `Retry-After`. Reusing the key for different
request material returns `409 idempotency_conflict`.

**Behaviour (R5)**
- Server-side deep-copy of the shared Quest into a new `chat_sessions` row owned by the caller:
  - copies turns + provider responses as fresh append-only rows (new ids),
  - **skips** Dialogs that are redacted in the source,
  - clones referenced media bytes into new storage objects and `media` rows owned by the importer,
    then rewrites attachment references,
  - sets `imported_from_label` / `imported_at` (R6); ownership = caller.
- Media cloning uses the same staging ledger/compensation path as admin import, so a failed or
  interrupted copy creates no partial Quest/media rows and leaves cleanup retry-safe.
- The new Quest is independent: subsequent turns the user submits stream from **their own** selected
  models via the existing `POST /api/v2/chat/sessions/{id}/turns` flow (FR-012).
- Transport retry with the same idempotency key → the original Quest/result; no duplicate rows.
- Intentional second import → use a new idempotency key and create a new independent copy.

## Frontend surface (FR-010, FR-014)

- **Share page** (`/share/[token]`): a clearly labelled "Import to continue" button with a one-line
  explanation; visible to viewers, gated to sign-in for anonymous users.
- **Quest sidebar**: the primary action is a **combined button** — primary "New Quest", attached
  secondary "Import" that opens a dialog accepting a share link/token and calls this endpoint, then
  navigates to the new Quest.
