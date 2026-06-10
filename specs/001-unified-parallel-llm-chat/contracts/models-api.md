# API Contract: Model Catalogue

**Base path**: `/api/v1/models`
**Feature**: 001-unified-parallel-llm-chat

Model catalogue endpoints are public (no authentication required). All responses use
`Content-Type: application/json`.

---

## GET /api/v1/models

List all active models in the platform catalogue with their Capability Matrix.

**Query parameters:**
- `provider_id` (optional): Filter by provider (e.g., `?provider_id=openai`)
- `input_modality` (optional): Filter to models supporting a given input modality
  (e.g., `?input_modality=image`)

**Response 200 OK:**
```json
{
  "models": [
    {
      "id": "gpt-4o-2024-11-20",
      "providerId": "openai",
      "displayName": "GPT-4o (Nov 2024)",
      "source": "CATALOGUE",
      "capabilityMatrix": {
        "inputModalities": ["text", "image"],
        "outputModalities": ["text"],
        "contextLengthTokens": 128000,
        "supportsStreaming": true,
        "supportsFunctionCalling": true,
        "supportsSystemPrompt": true,
        "supportsVideoInput": false
      }
    },
    {
      "id": "claude-3-5-sonnet-20241022",
      "providerId": "anthropic",
      "displayName": "Claude 3.5 Sonnet (Oct 2024)",
      "capabilityMatrix": {
        "inputModalities": ["text", "image"],
        "outputModalities": ["text"],
        "contextLengthTokens": 200000,
        "supportsStreaming": true,
        "supportsFunctionCalling": true,
        "supportsSystemPrompt": true,
        "supportsVideoInput": false
      }
    }
  ]
}
```

The `capabilityMatrix` object contains all known fields plus any additional fields stored
in the JSONB column (forward-compatible with new dimensions).

The `source` field indicates whether the model row comes from the seeded shared catalogue
(`CATALOGUE`), provider-side dynamic discovery (`DISCOVERED`), or a user-authored custom
model definition (`CUSTOM`).

---

## GET /api/v1/models/{modelId}

Retrieve a single model's details and Capability Matrix.

**Path parameter:** `modelId` — the model's string identifier (e.g., `gpt-4o-2024-11-20`)

**Response 200 OK:**
```json
{
  "id": "gpt-4o-2024-11-20",
  "providerId": "openai",
  "displayName": "GPT-4o (Nov 2024)",
  "capabilityMatrix": {
    "inputModalities": ["text", "image"],
    "outputModalities": ["text"],
    "contextLengthTokens": 128000,
    "supportsStreaming": true,
    "supportsFunctionCalling": true,
    "supportsSystemPrompt": true,
    "supportsVideoInput": false
  }
}
```

**Error responses:**
- `404` — model not found or inactive
