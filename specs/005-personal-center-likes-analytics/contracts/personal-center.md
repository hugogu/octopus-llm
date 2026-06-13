# Contract: Personal Center (profile, password, email verification)

All endpoints require authentication (Bearer JWT) unless noted. Error schema (Constitution):
`{ "code": "...", "message": "...", "details": {} }`.

## GET /api/v2/me  (EXTENDED)

Returns the caller's own identity + profile. **Extends** the existing response with
`displayName`, `emailVerified`, and derived `emailVerificationStatus`.

200:
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "Ada",
  "emailVerified": true,
  "emailVerificationStatus": "verified",
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
- `displayName`: optional, 1–255 chars after trimming; explicit `null` clears it; blank strings are
  rejected.

200: same shape as `GET /api/v2/me`.
400 `validation_error`: invalid length.

## Configured-model pricing fields  (EXTENDED)

The existing configured-model create/patch/response contracts gain nullable:

```json
{
  "inputPricePerMtok": 2.50,
  "outputPricePerMtok": 10.00,
  "priceCurrency": "USD"
}
```

Prices MUST be non-negative. Currency MUST be a three-letter uppercase ISO code when either price is
present. Omitting all three fields means cost is unknown. The same fields are supported by the
administrator's built-in-model create/patch contracts; allocated users can read but not edit them.

## POST /api/v2/me/password  (NEW)

Authenticated password change. On success, increments the existing `users.session_epoch`, invalidates
all previously issued JWTs, and returns a replacement token at the new epoch for the current client.

Request:
```json
{ "currentPassword": "…", "newPassword": "…" }
```
- `currentPassword`: required.
- `newPassword`: required, min 8 (matches existing register/reset policy), must differ from current.

200:
```json
{
  "status": "password_updated",
  "token": "replacement-jwt",
  "expiresAt": "2026-06-14T12:00:00Z"
}
```
- 401 `invalid_credentials`: `currentPassword` incorrect.
- 400 `validation_error`: weak/identical new password.

**Behavioral contract**: after 200, any JWT issued before the change MUST be rejected (401) on its
next use. The frontend MUST atomically replace its stored token with the returned token before making
another authenticated request (FR-002).

## POST /api/v2/me/email-verification/resend  (NEW)

Re-send the verification email to the authenticated user. Rate-limited (cooldown).

Request: empty body.

202:
```json
{ "status": "verification_sent" }
```
- 409 `already_verified`: email already verified.
- 429 `rate_limited` (`details.retryAfterSeconds`): cooldown not elapsed (FR-007).

## POST /api/v1/auth/password-reset/request  (NEW, PUBLIC)

Request:
```json
{ "email": "user@example.com" }
```

202 for existing, unknown, disabled, and internally rate-limited addresses:
```json
{ "status": "accepted" }
```

The public response and observable behavior MUST not reveal whether an account exists. Rate limiting
is keyed internally by normalized email and trusted network source; this endpoint does not return an
account-dependent `429` response (FR-007, FR-031).

## Registration and verification behavior  (EXTENDED)

- `POST /api/v1/auth/register` creates the user with `emailVerified:false`, issues one verification
  token, and sends the verification email.
- Resend invalidates prior unused verification tokens before issuing a replacement.
- `POST /api/v1/auth/verify-email` remains public and single-use.
- Login remains allowed for an activated, unverified account so it can reach Personal Center and
  resend; verification status is visible in `GET /api/v2/me`.

## (Existing — unchanged, referenced for completeness)

- `POST /api/v1/auth/verify-email` — confirm via token (already implemented).
- `POST /api/v1/auth/password-reset/confirm` — reset via emailed token; on success it also increments
  `session_epoch`, invalidating all prior JWTs.
- `POST /api/v1/auth/login`, `/logout`, `/register`.
