# API Contract: Chat Sessions

**Version**: v1  
**Base Path**: `/api/v1/chat/sessions`

## Endpoints

### GET /api/v1/chat/sessions

List sessions for the current user.

**Query Parameters**:
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| limit | integer | 20 | Max sessions to return (max 100) |
| offset | integer | 0 | Pagination offset |

**Response** (200 OK):
```json
{
  "sessions": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Python Best Practices",
      "selectedModelId": "gpt-4o",
      "createdAt": "2026-06-10T08:30:00Z",
      "updatedAt": "2026-06-10T09:15:00Z"
    }
  ],
  "total": 42
}
```

**Notes**:
- Sessions are sorted by `updated_at` descending (most recent first)
- `title` may be null for new sessions; UI should show a placeholder like "New Chat" or first message preview
- `selectedModelId` indicates the primary model used in the session

---

### POST /api/v1/chat/sessions

Create a new chat session.

**Request Body** (optional):
```json
{
  "title": "My Session"
}
```

**Response** (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "title": "My Session",
  "selectedModelId": null,
  "createdAt": "2026-06-10T10:00:00Z",
  "updatedAt": "2026-06-10T10:00:00Z"
}
```

**Notes**:
- If `title` is omitted, backend stores null; UI generates placeholder
- `selectedModelId` is initially null and set on first turn submission

---

### GET /api/v1/chat/sessions/{sessionId}

Get a single session with full turn history.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| sessionId | UUID | Session identifier |

**Response** (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Python Best Practices",
  "selectedModelId": "gpt-4o",
  "createdAt": "2026-06-10T08:30:00Z",
  "updatedAt": "2026-06-10T09:15:00Z",
  "turns": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440010",
      "sequenceNum": 1,
      "promptText": "What are Python best practices?",
      "selectedModelIds": ["gpt-4o"],
      "responses": [
        {
          "modelId": "gpt-4o",
          "status": "success",
          "responseText": "# Python Best Practices\n\n1. Use virtual environments...",
          "errorMessage": null,
          "inputTokens": 15,
          "outputTokens": 250,
          "latencyMs": 1200
        }
      ],
      "createdAt": "2026-06-10T08:30:05Z"
    }
  ]
}
```

**Error Responses**:
- `404 Not Found`: Session does not exist or belongs to another user
- `401 Unauthorized`: User not authenticated

---

### DELETE /api/v1/chat/sessions/{sessionId}

Delete a session and all its turns/responses.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| sessionId | UUID | Session identifier |

**Response**: `204 No Content`

**Error Responses**:
- `404 Not Found`: Session does not exist or belongs to another user
- `401 Unauthorized`: User not authenticated

**Notes**:
- Deletion is permanent (hard delete)
- Cascades to all turns and provider responses

---

### POST /api/v1/chat/sessions/{sessionId}/turns

Submit a new turn (user message) and stream responses.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| sessionId | UUID | Session identifier |

**Request Body**:
```json
{
  "promptText": "Explain recursion",
  "selectedModelIds": ["gpt-4o", "claude-3-opus"],
  "clientRequestId": "req-123",
  "attachments": []
}
```

**Validation**:
- `promptText`: required, non-empty string
- `selectedModelIds`: required, non-empty array of strings
- `clientRequestId`: optional, max 100 chars, used for deduplication
- `attachments`: optional, array of attachment metadata objects

**Response** (SSE Stream):

Content-Type: `text/event-stream`

Events:

1. **Turn Created** (system event):
```
event: message
data: {"event":"turn_created","turnId":"550e8400-e29b-41d4-a716-446655440020","sequenceNum":2}
```

2. **Token Stream** (per model):
```
event: message
data: {"event":"token","modelId":"gpt-4o","delta":"Recursion "}
```

3. **Capability Notice** (if attachments dropped):
```
event: message
data: {"event":"capability_notice","modelId":"gpt-4o","notice":"Images not supported"}
```

4. **Model Complete**:
```
event: message
data: {"event":"model_complete","modelId":"gpt-4o","inputTokens":10,"outputTokens":150,"latencyMs":800}
```

5. **Model Error**:
```
event: message
data: {"event":"model_error","modelId":"claude-3-opus","error":"Rate limit exceeded"}
```

6. **All Complete** (stream terminator):
```
event: message
data: {"event":"all_complete"}
```

**Error Responses**:
- `409 Conflict`: Duplicate request (same `clientRequestId`); response body contains `{"turnId":"..."}`
- `404 Not Found`: Session does not exist
- `400 Bad Request`: Invalid request body
- `401 Unauthorized`: User not authenticated

**Notes**:
- Each model runs concurrently; tokens from different models are interleaved in the stream
- The frontend must parse and route tokens to the correct model's response panel
- Response text is NOT included in SSE tokens; frontend accumulates deltas to reconstruct the full response

## Data Types

### SessionResponse

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Session identifier |
| title | string | Optional session title |
| selectedModelId | string | Primary model for this session |
| createdAt | ISO 8601 timestamp | Creation time |
| updatedAt | ISO 8601 timestamp | Last update time |

### TurnDto

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Turn identifier |
| sequenceNum | integer | Order within session |
| promptText | string | User prompt |
| selectedModelIds | string[] | Models queried |
| responses | ProviderResponseDto[] | Model responses |
| createdAt | ISO 8601 timestamp | Creation time |

### ProviderResponseDto

| Field | Type | Description |
|-------|------|-------------|
| modelId | string | Responding model |
| status | string | `success` or `error` |
| responseText | string | Full response (null if error) |
| errorMessage | string | Error details (null if success) |
| inputTokens | integer | Input token count |
| outputTokens | integer | Output token count |
| latencyMs | integer | Response time in milliseconds |

## Changes from Previous Version

- Added `selectedModelId` to `SessionResponse`
- Added `DELETE /api/v1/chat/sessions/{sessionId}` endpoint
- Clarified SSE event types and their payloads
- Documented `clientRequestId` deduplication behavior

## Error Schema

All errors follow the standard platform format:

```json
{
  "code": "SESSION_NOT_FOUND",
  "message": "Session 550e8400-e29b-41d4-a716-446655440000 not found",
  "details": {}
}
```