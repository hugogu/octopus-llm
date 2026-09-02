# Contract: Administrator Model Access Management

All routes are same-origin browser calls proxied to the backend and live under `/api/v2`. Every response is `Cache-Control: no-store`. These endpoints require an authenticated administrator (`ROLE_ADMIN`).

## List models

```http
GET /api/v2/admin/models?page=0&size=50&q=vision&connectionId=<uuid>&protocol=openai&enabled=true&anonymousAllowed=false&sort=displayName&direction=asc
```

Parameters are optional except for `page` and `size` defaults. `size` is constrained to `1..100`; `page` is zero-based. `q` searches the model ID, display name, and built-in connection label. `sort` and `direction` are allowlisted, and the service adds model UUID as a stable tie-breaker.

```json
{
  "items": [
    {
      "id": "configured-model-uuid",
      "connection": { "id": "connection-uuid", "label": "OpenAI" },
      "modelId": "provider-model-id",
      "displayName": "GPT model",
      "protocol": "openai-compatible",
      "capabilities": { "streaming": true, "vision": false, "tools": false },
      "isEnabled": true,
      "isAnonymousAllowed": false
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1,
  "totalPages": 1
}
```

The response never contains a base URL, API key, encrypted value, custom parameter, owner, or private connection setting. The list includes administrator-controlled models only; user-owned models are not in this management surface.

## Preview a bulk operation

```http
POST /api/v2/admin/model-bulk-operations/preview
Content-Type: application/json
```

```json
{
  "action": "ALLOW_ANONYMOUS",
  "selection": {
    "mode": "FILTER",
    "filter": {
      "q": "vision",
      "connectionId": "connection-uuid",
      "protocol": "openai-compatible",
      "enabled": true,
      "anonymousAllowed": false
    },
    "excludeIds": []
  }
}
```

`mode: IDS` uses `ids` instead of `filter`. Explicit IDs and exclusions are deduplicated. The service rejects an empty selection and selections above the configured operation maximum (initially 1,000). The preview stores a frozen target snapshot and expires after a short operational window.

```json
{
  "operationId": "operation-uuid",
  "action": "ALLOW_ANONYMOUS",
  "targetCount": 87,
  "expiresAt": "2026-09-02T01:05:00Z",
  "summary": {
    "alreadySatisfied": 12,
    "eligible": 75,
    "unavailable": 0
  }
}
```

## Execute and inspect a bulk operation

```http
POST /api/v2/admin/model-bulk-operations/{operationId}/execute
Idempotency-Key: stable-client-operation-key
```

Execution is idempotent. Repeating the same request returns the stored operation result; reusing the key with a different operation returns a conflict. Each item is evaluated against its current state and returns one of:

- `CHANGED`: requested state was applied;
- `ALREADY_SATISFIED`: the item already had the requested state;
- `ALREADY_DELETED`: a delete target was removed by another administrator;
- `FAILED`: item was invalid, changed outside the frozen scope, or could not be updated.

Actions have isolated meaning:

| Action | Mutation |
|---|---|
| `ALLOW_ANONYMOUS` | Set `is_anonymous_allowed = true`; do not change `is_enabled`. |
| `REVOKE_ANONYMOUS` | Set `is_anonymous_allowed = false`; do not change `is_enabled`. |
| `SHOW` | Set `is_enabled = true`; do not change anonymous policy. |
| `HIDE` | Set `is_enabled = false`; do not change anonymous policy. |
| `DELETE` | Remove the configured model after authorization; preserve historical response snapshots. |

```json
{
  "operationId": "operation-uuid",
  "status": "COMPLETED",
  "action": "ALLOW_ANONYMOUS",
  "targetCount": 87,
  "changedCount": 75,
  "alreadySatisfiedCount": 12,
  "failedCount": 0,
  "items": [
    {
      "configuredModelId": "configured-model-uuid",
      "displayName": "GPT model",
      "outcome": "CHANGED",
      "errorCode": null,
      "errorMessage": null
    }
  ]
}
```

`GET /api/v2/admin/model-bulk-operations/{operationId}` returns the same safe summary and item outcomes. A failed item can be retried by creating a new preview with only that item; the UI must not silently resubmit successful items.

## Errors and security

- `400`: invalid filter, action, page size, empty selection, or operation too large.
- `401`: unauthenticated.
- `403`: authenticated user is not an administrator.
- `404`: operation is unknown or expired.
- `409`: idempotency or preview state conflict.

Error messages contain no provider credentials, endpoints, custom parameters, request prompts, or response text. Mutations and their safe counts are written to the admin audit log.
