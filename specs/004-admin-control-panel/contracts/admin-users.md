# Contract: Admin — User Management

Base path `/api/v2/admin/users`. All endpoints require `ROLE_ADMIN`; non-admins receive `403` with the standard error schema `{ code, message, details }`. No response ever includes `password_hash` or key material (FR-011).

## GET /api/v2/admin/users

List/search accounts (FR-005).

Query: `page` (default 0), `size` (default 25; bounded to 1..100 via `boundedPageRequest`), `q` (optional email substring, case-insensitive). Ordering MUST be deterministic (`createdAt ASC, id ASC`) and backed by an index supporting the search.

`200` → `PageResponse<AdminUserResponse>` (existing `PageResponse` shape — `items`, not `content`):

```jsonc
{
  "items": [{
    "id": "uuid",
    "email": "user@example.com",
    "emailVerified": true,
    "isActive": false,
    "isDisabled": false,
    "isAdmin": false,
    "createdAt": "2026-06-13T10:00:00Z"
  }],
  "page": 0, "size": 25, "totalElements": 1, "totalPages": 1
}
```

First page MUST return in < 1s at ≥10k accounts (SC-006); a seeded-scale test verifies this.

## POST /api/v2/admin/users/{id}/activate

Set `is_active = TRUE` (FR-006). Idempotent (FR-010). `200` → `AdminUserResponse`. `404` if unknown.

## POST /api/v2/admin/users/{id}/disable

Set `is_disabled = TRUE` and increment `session_epoch` (FR-007). Idempotent. Refused `409` if the target is the only usable admin — enforced by a conditional UPDATE / `SERIALIZABLE` transaction so concurrent disables cannot both succeed (FR-004 / SC-007 / C1). Effect within one request cycle (SC-002). `200` → `AdminUserResponse`.

## POST /api/v2/admin/users/{id}/enable

Clear `is_disabled` (FR-008). Idempotent. Does **not** change `session_epoch`. Prior data intact. `200` → `AdminUserResponse`.

## POST /api/v2/admin/users/{id}/reset-password

Invalidate current password, increment `session_epoch`, issue a single-use reset token, email a reset link (FR-009). Admin never sees/sets the password. Refused `409` when the target is the only usable admin (resetting it would lock out all administration — same guard as disable, C2). `202 Accepted` → `{ "status": "reset_email_sent" }`. `404` if unknown.

## Authorization & audit

- Every mutation writes an `admin_audit_log` row (`action`, `target_type=USER`, `target_id`, acting admin) (FR-025).
- A non-admin calling any of the above → `403`, non-disclosing (FR-003).
