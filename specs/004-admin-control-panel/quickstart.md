# Quickstart: Admin Control Panel

Validation guide proving the feature end-to-end. Assumes the existing Docker-Compose / local backend + frontend setup. Details live in [data-model.md](data-model.md) and [contracts/](contracts/).

## Prerequisites

- Backend builds: `cd backend && ./gradlew build`
- Frontend type-checks: `cd frontend && npx tsc --noEmit`
- Flyway `V019__admin_control_panel.sql` applied (auto on backend startup).
- Config `app.admin.bootstrap-email` set to a registered account's email.

## Scenario A — Bootstrap & account lifecycle (US1)

1. Register the bootstrap email account, then restart the backend. Verify it is promoted (`is_admin = true`, `is_active = true`).
2. As admin, `GET /api/v2/admin/users` → the account list paginates and shows status fields; no `password_hash` present.
3. `POST /api/v2/admin/users/{id}/disable` a test account → its existing token is rejected on the next request (`401`); its rows still exist.
4. Re-login as the still-disabled test account at `POST /api/v1/auth/login` → `401`, no token (disable blocks login, not just authenticated requests).
5. `POST .../enable` → the account logs in again; a freshly issued token works immediately (epoch comparison, no clock-precision flake).
6. `POST .../reset-password` → `202`; the old password fails login; open the reset page with the emailed token, submit a new password (`POST /api/v1/auth/password-reset/confirm`), then log in with it. Submitting the same token twice → second attempt `400`.
7. Attempt to disable, demote, **or** reset-password the only usable admin → each refused (`409`). With two admins, two concurrent disables (or two concurrent resets) leave exactly one usable admin.
8. `GET /api/v2/me` as admin returns `isAdmin: true`; as a regular user returns `isAdmin: false` and the admin nav/routes are not reachable.
9. Any non-admin call to `/api/v2/admin/**` → `403`.

**Expected**: SC-001 (status change < 30s), SC-002 (disable within one request cycle, login included), SC-006 (user list first page < 1s at ≥10k — see perf test), SC-007 (last usable admin protected under concurrency for disable/demote/reset).

## Scenario B — Built-in connection allocation (US2)

1. As admin, `POST /api/v2/admin/connections` with a mock endpoint + key → `201`, response omits the key.
2. Add a model: `POST /api/v2/admin/connections/{id}/models`.
3. Activate user U1; allocate the connection to U1 (`PUT .../allocations/{U1}` → `204`). Allocating to a non-activated user → `422`.
4. As U1, `GET /api/v2/connections` → the built-in connection appears with `builtin: true, readOnly: true`; its model is selectable.
5. As U1, submit a chat turn selecting the built-in model → streams a response; the key never appears in any response, log, or analytics payload (SC-003).
6. As U1, attempt to `PATCH`/`DELETE` the built-in connection → `404` (not owned) (FR-017).
7. Revoke U1's allocation → U1 loses access; a second allocated user U2 is unaffected (SC-004).
8. Rotate the key (`PUT .../key`) → U1/U2 keep working without re-allocation (FR-020).
9. Delete the built-in connection → it disappears from allocated users; previously saved responses retain model/protocol/label snapshots (FR-021).

## Scenario C — BYOK always available (US3)

1. Register a fresh account, do **not** activate it.
2. Create a BYOK connection + model and chat with it → succeeds (SC-005, FR-022).
3. Confirm the account sees no built-in connections until an admin activates and allocates one.
4. Disable the account → all BYOK actions are refused until re-enabled (FR-024).

## Gate checks (Constitution)

- `./gradlew build` passes (unit + integration; ≥1 happy-path integration test per new admin endpoint).
- `npx tsc --noEmit` passes with zero errors.
- Flyway migration validated locally before commit.
- Grep logs during Scenario B step 5 to confirm zero key disclosures.
