# Contract: Auth, Listing & Chat Changes (non-admin surface)

Changes to existing endpoints so allocated built-in connections work for regular users and account state is enforced.

## Security filter (all authenticated endpoints)

On every authenticated request the security context load now:

1. Validates JWT signature + expiry + per-JTI revocation (existing).
2. Loads the `User`; rejects with `401` if `is_disabled` or if the token's `session_epoch` claim `< user.session_epoch` (FR-007 / FR-024 / SC-002). Integer epoch comparison avoids the `iat` second-precision hazard (H6).
3. Grants `ROLE_USER`, plus `ROLE_ADMIN` when `is_admin`.

`/api/v2/admin/**` requires `ROLE_ADMIN`; everything else under `/api/v2/**` requires authentication (unchanged for `/auth/**`, `/health`, `/protocols`, `/catalogue`).

## POST /api/v1/auth/login  (CHANGED)

After verifying the password, if the account `is_disabled` the endpoint returns `401 Invalid credentials` and issues **no** token (FR-007 / FR-024 / H1). Successful login embeds the user's current `session_epoch` in the issued JWT.

## GET /api/v2/me  (NEW)

Returns the authenticated caller's own identity so the frontend can gate admin navigation (FR-026): `200` → `{ "id": "uuid", "email": "...", "isAdmin": false, "isActive": false }`. Requires authentication; never exposes other users or secret material. Non-admins receive `isAdmin: false` and MUST NOT be shown or able to reach admin routes.

## POST /api/v1/auth/password-reset/confirm  (NEW, public)

Body `{ "token": "...", "password": "<min 8>" }`. Consumes a single-use, unexpired `password_resets` token via an atomic conditional UPDATE (only one of two concurrent submissions wins — H5); sets a new bcrypt `password_hash`. `200` → `{ "status": "password_updated" }`. `400` for invalid/expired/used token (FR-009). The frontend reset page (FR-027) posts here.

## GET /api/v2/connections  (CHANGED)

Now returns the caller's own connections **plus** built-in connections allocated to the caller (FR-018 / FR-023). New fields on `ConnectionResponseV2`:

```jsonc
{ "...": "...", "builtin": false, "readOnly": false }
```

Allocated built-in connections appear with `builtin: true, readOnly: true`. Mutation endpoints (`PATCH`/`PUT key`/`DELETE`, add/edit models) continue to call `requireOwned` and therefore return `404` when a non-owner targets a built-in connection (FR-017).

## Chat turn submission  (CHANGED)

`ChatService.submitTurn` resolves `selectedConfiguredModelIds` via `requireSelectable(userId, ids)` = owned models **or** enabled models on a built-in connection allocated to the caller. A disabled/missing/foreign/unallocated id is rejected with no provider call (preserves 003 behavior). Dispatch decrypts the admin-supplied key server-side; the key never reaches the client or any log (FR-019).

## BYOK guarantee (unchanged behavior, asserted)

A registered, verified, non-disabled, **non-activated** account can still create and use its own connections/models (FR-022). Activation and allocation gate only built-in access.
