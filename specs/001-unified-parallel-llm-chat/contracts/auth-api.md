# API Contract: Authentication

**Base path**: `/api/v1/auth`
**Feature**: 001-unified-parallel-llm-chat

All endpoints are unauthenticated. Requests and responses use `Content-Type: application/json`.
On validation errors, the response body follows the standard error schema.

---

## Standard Error Schema

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Human-readable summary",
  "details": {
    "field": "reason"
  }
}
```

Common error codes: `VALIDATION_ERROR`, `EMAIL_ALREADY_REGISTERED`, `INVALID_CREDENTIALS`,
`EMAIL_NOT_VERIFIED`, `TOKEN_EXPIRED`, `TOKEN_ALREADY_USED`, `RATE_LIMITED`.

---

## POST /api/v1/auth/register

Register a new account with email and password.

**Request body:**
```json
{
  "email": "user@example.com",
  "password": "MinLength8$ecure"
}
```

**Validation:**
- `email`: valid email format, max 255 chars, not already registered
- `password`: min 8 chars, must contain at least one number and one special char

**Response 201 Created:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "message": "Verification email sent. Please check your inbox."
}
```

**Error responses:**
- `400` — validation failure (with `details` per field)
- `409` — `EMAIL_ALREADY_REGISTERED`
- `429` — `RATE_LIMITED` (max 5 registrations per IP per hour)

---

## POST /api/v1/auth/verify-email

Verify the user's email address using the token from the verification email.

**Request body:**
```json
{
  "token": "a3f9b2c1d4e5..."
}
```

**Response 200 OK:**
```json
{
  "message": "Email verified successfully. You can now log in."
}
```

**Error responses:**
- `400` — `TOKEN_EXPIRED` or `TOKEN_ALREADY_USED`
- `404` — token not found

---

## POST /api/v1/auth/login

Authenticate with email and password. Returns a session token.

**Request body:**
```json
{
  "email": "user@example.com",
  "password": "MinLength8$ecure"
}
```

**Response 200 OK:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

The `accessToken` is a JWT. Clients include it as `Authorization: Bearer <token>` on all
authenticated requests.

**Error responses:**
- `401` — `INVALID_CREDENTIALS` (email/password mismatch; generic message, no field hint)
- `403` — `EMAIL_NOT_VERIFIED`
- `429` — `RATE_LIMITED` (max 10 failed attempts per email per 15 minutes; lockout with backoff)

---

## POST /api/v1/auth/logout

Invalidate the current JWT immediately by inserting its `jti` claim into the `revoked_tokens`
table. Subsequent requests bearing the same token return `401` even if the signature is valid
and the `exp` has not elapsed.

**Headers:** `Authorization: Bearer <token>`

**Response 204 No Content** (no body)

**Error responses:**
- `401` — missing or invalid token (already revoked tokens also return 401)

**Note on token design**: Every JWT issued by `POST /login` MUST contain a `jti` (UUID v4)
claim. The `jti` is the lookup key in `revoked_tokens`. Pure stateless JWTs without a `jti`
cannot support immediate logout and MUST NOT be issued.
