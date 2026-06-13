# Contract: Personal Center (profile, password, email verification)

All endpoints require authentication (Bearer JWT) unless noted. Error schema (Constitution):
`{ "code": "...", "message": "...", "details": {} }`.

## GET /api/v2/me  (EXTENDED)

Returns the caller's own identity + profile. **Extends** the existing response with
`displayName` and `emailVerified`.

200:
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "Ada",
  "emailVerified": true,
  "isAdmin": false,
  "isActive": true
}
```

## PATCH /api/v2/me  (NEW)

Update editable profile attributes. Currently `displayName` only.

Request:
```json
{ "displayName": "Ada Lovelace" }
```
- `displayName`: optional, 1–255 chars; trims; empty string clears to null.

200: same shape as `GET /api/v2/me`.
400 `validation_error`: invalid length.

## POST /api/v1/auth/password-change  (NEW)

Authenticated password change. On success, invalidates **all other** sessions for this user
(`users.sessions_valid_from = now()`); the caller's current token may also be invalidated — clients
should treat a subsequent 401 as "re-login".

Request:
```json
{ "currentPassword": "…", "newPassword": "…" }
```
- `currentPassword`: required.
- `newPassword`: required, min 8 (matches existing register/reset policy), must differ from current.

200:
```json
{ "status": "password_updated" }
```
- 401 `invalid_credentials`: `currentPassword` incorrect.
- 400 `validation_error`: weak/identical new password.

**Behavioral contract**: after 200, any JWT issued before the change (other devices) MUST be rejected
(401) on its next use (FR-002).

## POST /api/v1/auth/verify-email/resend  (NEW)

Re-send the verification email to the authenticated user. Rate-limited (cooldown).

Request: empty body.

202:
```json
{ "status": "verification_sent" }
```
- 409 `already_verified`: email already verified.
- 429 `rate_limited` (`details.retryAfterSeconds`): cooldown not elapsed (FR-007).

## (Existing — unchanged, referenced for completeness)

- `POST /api/v1/auth/verify-email` — confirm via token (already implemented).
- `POST /api/v1/auth/password-reset/confirm` — reset via emailed token (already implemented).
- `POST /api/v1/auth/login`, `/logout`, `/register`.
