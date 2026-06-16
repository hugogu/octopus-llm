# Contract: Share audience scope

Extends existing share endpoints with a `scope` of `authenticated` (logged-in users only) or
`public`. Tokens stay opaque (Constitution VI).

## `POST /api/v2/chat/sessions/{sessionId}/shares` (owner)

Create/ensure an active share. **Request** (new optional field):
```json
{ "scope": "authenticated" }   // "authenticated" (default) | "public"
```
**Response** `200/201` `{ "token": "…", "scope": "authenticated", "createdAt": "…" }`.

## `PATCH /api/v2/chat/sessions/{sessionId}/shares/{token}` (owner) — NEW

Change scope of an existing active share.
```json
{ "scope": "public" }
```
**200 OK** `{ "token": "…", "scope": "public" }`. **404** if token not an active share of the session.

## `DELETE /api/v2/chat/sessions/{sessionId}/shares/{token}` (owner)

Revoke — unchanged.

## `GET /api/v2/shared/{token}` (viewer) — scope-enforced

- `scope = public` → viewable without authentication (current behaviour).
- `scope = authenticated` → requires a valid auth principal:
  - Anonymous request → **401** `{ "code": "auth_required" }` and **no** Quest content or owner
    identity in the body (FR-021).
  - Authenticated request → returns the shared Quest (redacted Dialogs excluded).
- Revoked/unknown token → **404** `{ "code": "share_not_found" }` regardless of scope.
- Response includes `"scope"` and a `"canImport": true` hint so the share page can render the
  "Import to continue" affordance (see shared-import contract).
