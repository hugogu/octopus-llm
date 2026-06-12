# API v2 Contract

All errors use:

```json
{
  "code": "CONNECTION_NOT_FOUND",
  "message": "Connection not found",
  "details": {}
}
```

Common codes include `VALIDATION_ERROR`, `DUPLICATE_REQUEST`, `400 BAD_REQUEST`,
`404 NOT_FOUND`, and `INTERNAL_ERROR`. Duplicate idempotency requests include
the existing turn ID in `details.turnId`. Validation errors include field
messages in `details`. Key material is never included in errors.

All collection responses use:

```json
{
  "items": [],
  "page": 0,
  "size": 25,
  "totalElements": 0,
  "totalPages": 0
}
```

`size` is limited to 1-100.

Invalid `page` or `size` returns 400. Ordering is deterministic:

- connections: `createdAt, id`
- configured models: `sortOrder, createdAt, id`
- sessions: `createdAt DESC`
- protocols/catalogue: stable code-defined identifiers and labels

## Version Cutover

This feature is an atomic backend/frontend major-version cutover for model configuration and chat:

- New model configuration and chat clients use `/api/v2`.
- Affected v1 model catalogue, API-key, model-config, provider-sync, and chat routes are removed in the same coordinated deployment after migration tests pass.
- Existing `/api/v1/auth/**` endpoints are unaffected and may remain available.
- Mixed deployment of the old frontend with the v2-only backend is unsupported; deployment must update backend and frontend together.

## Protocols

`GET /api/v2/protocols?page=0&size=25`

Public. Returns `id`, `displayName`, `defaultBaseUrl`, and conservative `capabilities`.

## Catalogue

`GET /api/v2/catalogue?protocol=openai-compatible&page=0&size=25`

Public. Invalid protocol returns 400.

## Connections

`GET /api/v2/connections?page=0&size=25`

`POST /api/v2/connections`

```json
{
  "protocol": "openai-compatible",
  "label": "Work endpoint",
  "baseUrl": "https://api.example.com/v1",
  "apiKey": "secret"
}
```

Response:

```json
{
  "id": "uuid",
  "protocol": "openai-compatible",
  "label": "Work endpoint",
  "baseUrl": "https://api.example.com/v1",
  "hasKey": true,
  "modelCount": 0,
  "createdAt": "2026-06-12T00:00:00Z",
  "updatedAt": "2026-06-12T00:00:00Z"
}
```

`PATCH /api/v2/connections/{id}` accepts `label` and `baseUrl`.

`PUT /api/v2/connections/{id}/key`

```json
{ "apiKey": "replacement-secret" }
```

Returns 204. The response and errors never echo any key substring.

`DELETE /api/v2/connections/{id}` returns 204 and cascades current configured models only.

## Configured Models

`GET /api/v2/configured-models?enabled=true&page=0&size=25`

`POST /api/v2/configured-models`

```json
{
  "connectionId": "uuid",
  "modelId": "custom-model-id",
  "displayName": "Custom Model",
  "capabilityOverrides": {
    "supports_streaming": true
  },
  "customParams": {
    "temperature": 0.2
  },
  "isEnabled": true
}
```

`PATCH /api/v2/configured-models/{id}` accepts:

```json
{
  "displayName": "Renamed",
  "isEnabled": false,
  "capabilityOverrides": {
    "supports_streaming": null
  },
  "customParams": {
    "temperature": null,
    "max_tokens": 2048
  },
  "sortOrder": 2
}
```

Null map values remove stored keys. Omitted fields are unchanged.

`DELETE /api/v2/configured-models/{id}` returns 204.

Example response:

```json
{
  "id": "configured-model-uuid",
  "connectionId": "connection-uuid",
  "connectionLabel": "Work endpoint",
  "protocol": "openai-compatible",
  "baseUrl": "https://api.example.com/v1",
  "modelId": "custom-model-id",
  "displayName": "Custom Model",
  "capabilityOverrides": {},
  "capabilityMatrix": {
    "input_modalities": ["text"],
    "output_modalities": ["text"],
    "context_length_tokens": null,
    "supports_streaming": true,
    "supports_function_calling": false,
    "supports_system_prompt": true,
    "supports_video_input": false
  },
  "customParams": {"temperature": 0.2},
  "isEnabled": true,
  "sortOrder": 0,
  "createdAt": "2026-06-12T00:00:00Z",
  "updatedAt": "2026-06-12T00:00:00Z"
}
```

## Chat

`POST /api/v2/chat/sessions/{sessionId}/turns`

```json
{
  "promptText": "Hello",
  "selectedConfiguredModelIds": ["uuid-1", "uuid-2"],
  "clientRequestId": "optional",
  "attachments": []
}
```

Duplicate UUIDs are rejected with 400. Missing, disabled, or foreign IDs are rejected before dispatch.

SSE model events contain both identities:

```json
{
  "event": "model_complete",
  "configuredModelId": "uuid-1",
  "modelId": "gpt-4o",
  "inputTokens": 10,
  "outputTokens": 20,
  "latencyMs": 500
}
```

`configuredModelId` keys UI stream state. `modelId` is display and provider-request metadata.

Session history responses include immutable `configuredModelId`, `modelId`,
`modelDisplayName`, `protocol`, and `connectionLabel` snapshots. Loading
history does not require the configured model or connection to still exist.

## Preferences

`GET|PUT|PATCH /api/v2/user/preferences`

```json
{
  "lastSelectedConfiguredModelId": "configured-model-uuid",
  "themePreference": "system",
  "sidebarCollapsed": false
}
```

`PUT` accepts an explicit null configured-model ID to clear the persisted
selection. Supported themes are `system`, `light`, and `dark`.

## Outbound Redirects

Connection URLs are normalized and validated on create/update and immediately
before dispatch. OpenAI-compatible and Anthropic transports disable automatic
HTTP and HTTPS redirect following; MiniMax uses Reactor Netty's default
no-redirect behavior. A 3xx response is surfaced as a provider error rather
than contacting an unvalidated redirect target.
