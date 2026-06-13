# Contract: Admin — Built-in Connections & Allocation

Base path `/api/v2/admin/connections`. Requires `ROLE_ADMIN`. Built-in connections reuse the connection/configured-model model (owned by the acting admin, `is_builtin = true`). Key material is never returned (FR-013).

## POST /api/v2/admin/connections

Create a built-in connection (FR-012). Body:

```jsonc
{ "protocol": "openai-compatible", "baseUrl": "https://api.example.com", "apiKey": "sk-...", "label": "Shared GPT" }
```

`201` → `BuiltinConnectionResponse` (below). Endpoint validated by `ConnectionEndpointPolicy` (FR-014); key encrypted via `ApiKeyEncryptionService`. `apiKey` never echoed.

## GET /api/v2/admin/connections

List all built-in connections (paged; `size` bounded 1..100 via `boundedPageRequest`). `200` → `PageResponse<BuiltinConnectionResponse>` (`items` wrapper):

```jsonc
{
  "items": [{
    "id": "uuid", "protocol": "openai-compatible", "label": "Shared GPT",
    "baseUrl": "https://api.example.com", "hasKey": true,
    "modelCount": 3, "allocatedUserCount": 12,
    "createdAt": "...", "updatedAt": "..."
  }],
  "page": 0, "size": 25, "totalElements": 1, "totalPages": 1
}
```

## PATCH /api/v2/admin/connections/{id}

Update `label` / `baseUrl` (FR-012). `200` → `BuiltinConnectionResponse`.

## PUT /api/v2/admin/connections/{id}/key

Rotate key (FR-020). Body `{ "apiKey": "sk-..." }`. `204`. All existing allocations keep working with the new key (no re-allocation).

## DELETE /api/v2/admin/connections/{id}

Delete built-in connection (FR-021). Cascades allocations and configured models; removes it from every allocated user's available models. Saved `provider_responses` retain immutable model/protocol/label snapshots. `204`.

## Built-in model management

Reuses configured-model semantics scoped to a built-in connection. The path parameter `{configuredModelId}` is the **configured-model UUID** (the operational model identity from feature 003), not the provider `modelId` string — duplicate provider `modelId` values are permitted, so only the UUID is unambiguous:

- `POST /api/v2/admin/connections/{id}/models` — add a model `{ modelId, displayName, isEnabled?, capabilityOverrides?, customParams? }` → `201` → `ConfiguredModelResponse` (includes the generated UUID `id`). Here `modelId` is the provider model string in the body.
- `GET /api/v2/admin/connections/{id}/models` — `200` → `PageResponse<ConfiguredModelResponse>` (`items` wrapper).
- `PATCH /api/v2/admin/connections/{id}/models/{configuredModelId}` — `200`. `{configuredModelId}` is a UUID.
- `DELETE /api/v2/admin/connections/{id}/models/{configuredModelId}` — `204`. `{configuredModelId}` is a UUID.

## Allocation

- `PUT /api/v2/admin/connections/{id}/allocations/{userId}` — allocate to a user (FR-015). Refused `422` if target not `is_active` (disabled status is orthogonal and does not block allocation; the allocation is inert while the account is disabled). Idempotent (FR-010). `204`.
- `DELETE /api/v2/admin/connections/{id}/allocations/{userId}` — revoke (FR-016). Other users' allocations unaffected (SC-004). `204`.
- `GET /api/v2/admin/connections/{id}/allocations` — `200` → `PageResponse<{ userId, email, createdAt }>` (`items` wrapper, `size` bounded 1..100).

## Audit

Create/update/delete/key-rotate/allocate/revoke each write an `admin_audit_log` row (`target_type=CONNECTION`), excluding key material (FR-025).
