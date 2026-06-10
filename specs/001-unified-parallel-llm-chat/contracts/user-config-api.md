# API Contract: User Model Configuration

**Base path**: `/api/v1/user`
**Feature**: 001-unified-parallel-llm-chat

All endpoints require authentication (`Authorization: Bearer <token>`). Responses use
`Content-Type: application/json`.

---

## API Key Management

### GET /api/v1/user/api-keys

List the current user's stored API keys (metadata only — no key values).

**Response 200 OK:**
```json
{
  "apiKeys": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "providerId": "openai",
      "label": "My OpenAI key",
      "createdAt": "2026-06-09T10:00:00Z"
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "providerId": "anthropic",
      "label": null,
      "createdAt": "2026-06-09T10:05:00Z"
    }
  ]
}
```

The actual key value is **never** returned by any endpoint.

---

### POST /api/v1/user/api-keys

Store a new API key for a provider.

**Request body:**
```json
{
  "providerId": "openai",
  "apiKey": "sk-proj-...",
  "label": "My OpenAI key"
}
```

**Validation:**
- `providerId`: must match a known provider ID in the model catalogue
- `apiKey`: format validated per-provider (not live-tested at storage time)
- `label`: optional, max 255 chars

**Response 201 Created:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "providerId": "openai",
  "label": "My OpenAI key",
  "createdAt": "2026-06-09T10:00:00Z"
}
```

**Error responses:**
- `400` — `VALIDATION_ERROR` (invalid format or unknown providerId)
- `401` — not authenticated

---

### DELETE /api/v1/user/api-keys/{keyId}

Delete a stored API key. All user model configs using this key are disabled immediately.

**Path parameter:** `keyId` — UUID of the stored key

**Response 204 No Content**

**Error responses:**
- `404` — key not found or does not belong to the current user

---

## Model Configuration

### GET /api/v1/user/model-configs

List all models the current user has configured, including enable/disable status.

**Response 200 OK:**
```json
{
  "modelConfigs": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440010",
      "modelId": "gpt-4o-2024-11-20",
      "providerApiKeyId": "550e8400-e29b-41d4-a716-446655440001",
      "isEnabled": true,
      "customParams": {
        "temperature": 0.2
      },
      "createdAt": "2026-06-09T10:10:00Z",
      "updatedAt": "2026-06-09T10:10:00Z"
    }
  ]
}
```

---

### POST /api/v1/user/model-configs

Add a model to the user's active configuration.

**Request body:**
```json
{
  "modelId": "gpt-4o-2024-11-20",
  "providerApiKeyId": "550e8400-e29b-41d4-a716-446655440001",
  "customParams": {
    "temperature": 0.2
  }
}
```

**Validation:**
- `modelId` must be an active model in the catalogue
- `apiKeyId` must belong to the current user and match the model's `providerId`

**Response 201 Created:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440010",
  "modelId": "gpt-4o-2024-11-20",
  "isEnabled": true,
  "customParams": {
    "temperature": 0.2
  },
  "createdAt": "2026-06-09T10:10:00Z"
}
```

**Error responses:**
- `400` — model not found, key not found, or key/provider mismatch
- `409` — model already configured for this user

---

### PATCH /api/v1/user/model-configs/{configId}

Update the API key binding, enable/disable state, or per-model request parameters.

**Request body:**
```json
{
  "providerApiKeyId": "550e8400-e29b-41d4-a716-446655440001",
  "isEnabled": false,
  "customParams": {
    "temperature": 0.1,
    "thinking": {
      "type": "enabled"
    }
  }
}
```

**Response 200 OK:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440010",
  "modelId": "gpt-4o-2024-11-20",
  "isEnabled": false,
  "customParams": {
    "temperature": 0.1,
    "thinking": {
      "type": "enabled"
    }
  },
  "updatedAt": "2026-06-09T10:15:00Z"
}
```

**Error responses:**
- `404` — model not configured for this user

---

### DELETE /api/v1/user/model-configs/{configId}

Remove a model from the user's configuration entirely.

**Response 204 No Content**

**Error responses:**
- `404` — model not configured for this user

---

## Dynamic Provider Models

### POST /api/v1/user/provider-models/sync

Use a stored API key to fetch the provider's current model list and upsert those models into
the shared runtime catalogue.

**Request body:**
```json
{
  "providerId": "moonshot",
  "providerApiKeyId": "550e8400-e29b-41d4-a716-446655440001"
}
```

**Response 200 OK:**
```json
{
  "models": [
    {
      "id": "kimi-k2.6",
      "providerId": "moonshot",
      "displayName": "Kimi K2.6",
      "source": "DISCOVERED"
    }
  ]
}
```

---

## Custom Models

### POST /api/v1/user/custom-models

Create a user-authored model definition for a provider model ID that is not yet in the shared
catalogue, then immediately create or update the user's model config for it.

**Request body:**
```json
{
  "providerId": "moonshot",
  "modelId": "kimi-k2.6-experimental",
  "displayName": "Kimi K2.6 Experimental",
  "providerApiKeyId": "550e8400-e29b-41d4-a716-446655440001",
  "isEnabled": true,
  "customParams": {
    "temperature": 0.3
  },
  "capabilityMatrix": {
    "input_modalities": ["text", "image"],
    "output_modalities": ["text"],
    "context_length_tokens": 256000,
    "supports_streaming": true,
    "supports_function_calling": true,
    "supports_system_prompt": true,
    "supports_video_input": false
  }
}
```
