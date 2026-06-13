# Contract: Session Sharing & Anonymous Likes

Owner endpoints require authentication. The public `shared` endpoints require **no** auth and MUST
expose zero identity. Error schema: `{ "code", "message", "details" }`.

## POST /api/v2/chat/sessions/{sessionId}/shares  (NEW, owner)

Create (or return the existing active) opaque share link for a session the caller owns.

201:
```json
{ "token": "opaque-string", "shareUrl": "/share/opaque-string", "createdAt": "…", "revokedAt": null }
```
- 404 `not_found`: session not owned by caller / missing.

## GET /api/v2/chat/sessions/{sessionId}/shares  (NEW, owner)

List share links for the session (active + revoked) so the owner can manage them.

200: `{ "items": [ { "token", "shareUrl", "createdAt", "revokedAt" } ] }`

## DELETE /api/v2/chat/sessions/{sessionId}/shares/{token}  (NEW, owner)

Revoke a share link (`revoked_at = now()`). Idempotent.

204 No Content. After this, the public endpoints below return 404 (FR-017).

## GET /api/v2/shared/{token}  (NEW, PUBLIC — no auth)

Read-only shared session through an **anonymous-safe** DTO. MUST NOT include `user_id`, `client_ip`,
named-liker identity, or named-like breakdown (FR-013, FR-015).

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
          "connectionLabel": "…",
          "status": "complete",
          "responseText": "…",
          "reasoningText": null,
          "anonymousLikeCount": 12,
          "likedByThisVisitor": false
        }
      ]
    }
  ]
}
```
- `likedByThisVisitor` is resolved from the `visitor_token` the client sends (header/query), if any.
- 404 `not_found`: token missing, revoked, or session deleted.

## POST /api/v2/shared/{token}/responses/{responseId}/like  (NEW, PUBLIC — no auth)

Anonymous like for a response within a shared session. Best-effort de-duplicated by `visitorToken`.

Request:
```json
{ "visitorToken": "client-generated-opaque-uuid" }
```
- `visitorToken`: required, opaque, client-generated; carries no identity (FR-015).

200:
```json
{ "responseId": "uuid", "anonymousLikeCount": 13, "likedByThisVisitor": true }
```
- Repeating with the same `visitorToken` is a no-op (count unchanged) (FR-016).
- 404 `not_found`: token revoked/missing, response not in this session.

**Behavioral contracts**:
- An authenticated caller who likes a response on a shared session SHOULD use the named-like endpoint
  (`PUT /api/v2/responses/{responseId}/like`) → recorded as a named like (FR-018), not anonymous.
- The anonymous endpoint never reveals who liked (FR-015); only counts + this-visitor state.
