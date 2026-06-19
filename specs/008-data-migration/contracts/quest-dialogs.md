# Contract: Per-Dialog deletion

Owner-authenticated (Quest owner or admin). Deletion is a **redaction** (append-only marker, R3) —
the underlying immutable rows are not modified. Idempotent. Each is confirmed in the UI via
`confirmDialog` before the call (FR-032).

Existing base path: `/api/v2/chat/sessions/{sessionId}`.

## `DELETE /api/v2/chat/sessions/{sessionId}/turns/{turnId}`

Delete a **user-prompt Dialog** → redacts the whole turn (prompt + its responses disappear from the
Quest and its shares).

- **204 No Content**
- Repeated call → still `204` (idempotent; unique index prevents duplicate markers).
- **403** if caller is not the Quest owner/admin. **404** if turn not in session.

## `DELETE /api/v2/chat/sessions/{sessionId}/turns/{turnId}/responses/{responseId}`

Delete a **model-response Dialog** → redacts one response; sibling responses in the same turn remain.

- **204 No Content**
- Idempotent; **403/404** as above; **404** if `responseId` not under `turnId`.

## Read impact

- `GET /api/v2/chat/sessions/{sessionId}` and `GET /api/v2/shared/{token}` exclude redacted turns and
  redacted responses.
- A turn whose every response is redacted still shows the prompt unless the turn itself is redacted.
- Shared reaction/like endpoints reject redacted turns/responses as not found.
- Analytics endpoints are unaffected (read immutable snapshots, ignore redactions).
