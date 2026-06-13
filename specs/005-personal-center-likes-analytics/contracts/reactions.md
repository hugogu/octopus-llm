# Contract: Response Likes (named)

Named likes by authenticated users, targeting one `provider_response`. Idempotent + toggleable.
Error schema: `{ "code", "message", "details" }`.

A "response" is identified by its `provider_responses.id` (`responseId`). The owning session is
verified server-side; a user may like any response they are authorized to view (their own session,
or — when authenticated and viewing a shared session — that session's responses, recorded as a named
like per FR-018).

## PUT /api/v2/responses/{responseId}/like  (NEW)

Like a response (idempotent — repeating is a no-op that returns the current state).

200:
```json
{ "responseId": "uuid", "likeCount": 4, "likedByMe": true }
```
- `likeCount`: distinct registered-user likes for this response (FR-010).
- 404 `not_found`: response does not exist or caller may not view it.

## DELETE /api/v2/responses/{responseId}/like  (NEW)

Un-like (idempotent). 

200:
```json
{ "responseId": "uuid", "likeCount": 3, "likedByMe": false }
```

## Like state surfaced in existing reads (EXTENDED)

`GET /api/v2/chat/sessions/{sessionId}` — each item in `responses[]` gains:
```json
{ "responseId": "uuid", "likeCount": 4, "likedByMe": true, "...": "existing fields" }
```
(`responseId` is the `provider_responses.id`; previously not surfaced.)

**Behavioral contracts**:
- At most one like per (user, response) — second PUT does not increase the count (FR-009).
- Likes persist across reloads (FR-010) and are removed when the session/response is deleted (FR-011).
- Anonymous like counts are NOT included in `likeCount` here; they are a separate figure surfaced only
  to the owner (see analytics / owner views) and on the shared anonymous endpoint.
