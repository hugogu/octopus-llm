# Contract: Session Sharing & Anonymous Likes

Owner endpoints require authentication. Shared-session `GET` and anonymous-like `POST` require no
auth and expose zero identity; token-scoped named-like `PUT`/`DELETE` require authentication. Error
schema: `{ "code", "message", "details" }`.

## POST /api/v2/chat/sessions/{sessionId}/shares  (NEW, owner)

Create (or return the existing active) opaque share link for a session the caller owns.

201 when created; 200 when returning the existing active share:
```json
{ "token": "opaque-string", "shareUrl": "/share/opaque-string", "createdAt": "…", "revokedAt": null }
```
- 404 `not_found`: session not owned by caller / missing.

## GET /api/v2/chat/sessions/{sessionId}/shares  (NEW, owner)

List share links for the session (active + revoked) so the owner can manage them.

Query params: `page=0`, `size=25`; `size` MUST be 1–100.

200:
```json
{
  "items": [ { "token": "…", "shareUrl": "/share/…", "createdAt": "…", "revokedAt": null } ],
  "page": 0,
  "size": 25,
  "totalElements": 1,
  "totalPages": 1
}
```

## DELETE /api/v2/chat/sessions/{sessionId}/shares/{token}  (NEW, owner)

Revoke a share link (`revoked_at = now()`). Idempotent.

204 No Content. After this, the public endpoints below return 404 (FR-017).

## GET /api/v2/shared/{token}  (NEW, PUBLIC — no auth)

Read-only shared session through an **anonymous-safe** DTO. MUST NOT include `user_id`, `client_ip`,
configured-model UUID, connection details, or any named-liker **identity** (FR-013, FR-015). It MAY
include the aggregate `namedLikeCount` (a count carries no identity) so loves made in chat are
visible on the share. When the request carries a valid bearer token, `likedByMe` reflects whether
that signed-in viewer has loved the response; it is always `false` for anonymous visitors.

200:
```json
{
  "title": "…",
  "turns": [
    {
      "sequenceNum": 1,
      "promptText": "…",
      "responses": [
        {
          "responseId": "uuid",
          "modelDisplayName": "…",
          "status": "complete",
          "responseText": "…",
          "reasoningText": null,
          "namedLikeCount": 4,
          "likedByMe": false,
          "anonymousLikeCount": 12,
          "likedByThisVisitor": false
        }
      ]
    }
  ]
}
```
- If the browser lacks the anonymous visitor cookie, the response issues a random HttpOnly,
  SameSite=Lax cookie with `Path=/api/v2/shared` and `Secure` in production.
  `likedByThisVisitor` is resolved from its share-scoped HMAC digest.
- 404 `not_found`: token missing, revoked, or session deleted.

## POST /api/v2/shared/{token}/responses/{responseId}/like  (NEW, PUBLIC — no auth)

Anonymous like for a response within a shared session. Request body is empty. The server-issued
visitor cookie is created if absent and is the only accepted de-duplication input.

200:
```json
{ "responseId": "uuid", "anonymousLikeCount": 13, "likedByThisVisitor": true }
```
- Repeating from the same recognized browser is a no-op (count unchanged) (FR-016).
- 404 `not_found`: token revoked/missing, response not in this session.

## PUT /api/v2/shared/{token}/responses/{responseId}/like  (NEW, AUTHENTICATED)

Named like by a logged-in visitor who does not own the shared conversation. The valid share token is
the authorization capability for viewing the target response; the authenticated user identity is
used for the named like.

200:
```json
{ "responseId": "uuid", "likeCount": 4, "likedByMe": true }
```

`DELETE` on the same path removes that named like idempotently.

**Behavioral contracts**:
- An authenticated caller on a shared session uses the token-scoped `PUT`/`DELETE` endpoint and is
  recorded as a named like (FR-018), not anonymous.
- The anonymous endpoint never reveals who liked (FR-015); only counts + this-visitor state.
- The public frontend page lives outside the authenticated `(app)` route group and all browser API
  calls use same-origin `/api/...` proxy paths.
