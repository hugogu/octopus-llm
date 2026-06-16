# Contract: Continue-from-share import (US2 / US3)

Lets a logged-in user copy a Quest they can view (via share token) into their own account to continue
it. Same endpoint backs both the share-page "Import to continue" button and the sidebar combined
button's "Import" action (same-deployment shares).

## `POST /api/v2/shared/{token}/import` (auth required)

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

**Behaviour (R5)**
- Server-side deep-copy of the shared Quest into a new `chat_sessions` row owned by the caller:
  - copies turns + provider responses as fresh append-only rows (new ids),
  - **skips** Dialogs that are redacted in the source,
  - copies attachment references (media objects reused by id within the same deployment — no blob
    duplication),
  - sets `imported_from_label` / `imported_at` (R6); ownership = caller.
- The new Quest is independent: subsequent turns the user submits stream from **their own** selected
  models via the existing `POST /api/v2/chat/sessions/{id}/turns` flow (FR-012).
- Duplicate import of the same share → a new independent copy each time (no dedupe; spec edge case).

## Frontend surface (FR-010, FR-014)

- **Share page** (`/share/[token]`): a clearly labelled "Import to continue" button with a one-line
  explanation; visible to viewers, gated to sign-in for anonymous users.
- **Quest sidebar**: the primary action is a **combined button** — primary "New Quest", attached
  secondary "Import" that opens a dialog accepting a share link/token and calls this endpoint, then
  navigates to the new Quest.
