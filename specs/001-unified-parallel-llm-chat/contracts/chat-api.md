# API Contract: Chat

**Base path**: `/api/v1/chat`
**Feature**: 001-unified-parallel-llm-chat

All endpoints require authentication (`Authorization: Bearer <token>`).

---

## Session Management

### POST /api/v1/chat/sessions

Create a new chat session.

**Request body:**
```json
{
  "title": "Comparing code generation"
}
```
`title` is optional; if omitted, the backend auto-generates it from the first turn's prompt.

**Response 201 Created:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "title": "Comparing code generation",
  "createdAt": "2026-06-09T11:00:00Z"
}
```

---

### GET /api/v1/chat/sessions

List the current user's sessions, newest first.

**Query parameters:**
- `limit` (default 20, max 100)
- `offset` (default 0)

**Response 200 OK:**
```json
{
  "sessions": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "title": "Comparing code generation",
      "createdAt": "2026-06-09T11:00:00Z",
      "updatedAt": "2026-06-09T11:05:00Z"
    }
  ],
  "total": 1
}
```

---

### GET /api/v1/chat/sessions/{sessionId}

Retrieve a session with all turns and their responses (for loading session history).

**Response 200 OK:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "title": "Comparing code generation",
  "turns": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "sequenceNum": 1,
      "promptText": "Write a binary search in Python",
      "attachments": [],
      "selectedModelIds": ["gpt-4o-2024-11-20", "claude-3-5-sonnet-20241022"],
      "responses": [
        {
          "modelId": "gpt-4o-2024-11-20",
          "status": "complete",
          "responseText": "def binary_search...",
          "inputTokens": 12,
          "outputTokens": 80,
          "latencyMs": 1420
        },
        {
          "modelId": "claude-3-5-sonnet-20241022",
          "status": "complete",
          "responseText": "Here is a binary search...",
          "inputTokens": 12,
          "outputTokens": 95,
          "latencyMs": 1650
        }
      ],
      "createdAt": "2026-06-09T11:01:00Z"
    }
  ]
}
```

**Error responses:**
- `404` — session not found or not owned by current user

---

## Parallel Chat: Submit Prompt with Streaming

### POST /api/v1/chat/sessions/{sessionId}/turns

Submit a prompt to all selected models concurrently. The response is a real-time SSE stream.

**Request headers:**
```
Content-Type: application/json
Accept: text/event-stream
Authorization: Bearer <token>
```

**Request body:**
```json
{
  "promptText": "Explain quantum entanglement simply",
  "selectedModelIds": ["gpt-4o-2024-11-20", "claude-3-5-sonnet-20241022", "deepseek-chat"],
  "clientRequestId": "c7e3a1b2-9f4d-4e80-8b0d-123456789abc",
  "attachments": [
    {
      "type": "image",
      "data": "<base64-encoded-image>",
      "mimeType": "image/png"
    }
  ]
}
```

`clientRequestId` is an optional UUID supplied by the client. If provided, the server stores
it in `chat_turns.client_request_id` and uses it as an idempotency key: a second request to
the same session with the same `clientRequestId` returns `409 Conflict` with the existing
`turnId`, instead of creating a new turn and dispatching duplicate LLM calls.

`attachments` is optional. Each attachment is base64-encoded inline (max 10 MB per file).
Attachments are routed only to models whose `capabilityMatrix.inputModalities` includes the
attachment type; others receive only the text prompt.

**Validation:**
- `promptText`: non-empty, max 100,000 chars
- `selectedModelIds`: non-empty array; each ID must be enabled in the user's model-configs
- `clientRequestId`: optional; if present must be a valid UUID v4
- `attachments[].mimeType`: must be in `["image/png","image/jpeg","image/webp","image/gif",
  "video/mp4","video/webm"]`
- `attachments[].data`: base64-encoded, decoded size must not exceed 10 MB per file

---

**Response: `200 OK` with `Content-Type: text/event-stream`**

The connection remains open until all selected models respond or error. Events are newline-
delimited SSE (`data: <json>\n\n`).

### Event: `turn_created`

Sent immediately after the turn is persisted. Clients use `turnId` to later fetch full history.

```
data: {"event":"turn_created","turnId":"turn-550e8400-...","sequenceNum":1}
```

### Event: `capability_notice`

Sent once per model where an attachment was dropped due to missing capability. Clients display
a notice in that model's panel.

```
data: {"event":"capability_notice","modelId":"deepseek-chat","notice":"Image input not supported — text only sent"}
```

### Event: `token`

One or more tokens streamed from a model as they arrive.

```
data: {"event":"token","modelId":"gpt-4o-2024-11-20","delta":"Quantum"}
data: {"event":"token","modelId":"gpt-4o-2024-11-20","delta":" entanglement"}
data: {"event":"token","modelId":"claude-3-5-sonnet-20241022","delta":"Great"}
```

Events from different models are interleaved in arrival order — the client must buffer by
`modelId`.

### Event: `model_complete`

Sent when a model finishes streaming successfully.

```
data: {"event":"model_complete","modelId":"gpt-4o-2024-11-20","inputTokens":18,"outputTokens":112,"latencyMs":1340}
```

### Event: `model_error`

Sent when a model call fails. Other models' streams continue unaffected.

```
data: {"event":"model_error","modelId":"moonshot-v1-8k","error":"API key invalid or quota exceeded"}
```

### Event: `all_complete`

Sent after the last model produces a `model_complete` or `model_error` event.

```
data: {"event":"all_complete"}
```

The SSE connection closes after `all_complete`.

---

---

**Error responses (before stream opens):**
- `400` — `VALIDATION_ERROR` (invalid prompt, unknown modelId, attachment too large)
- `403` — one or more `selectedModelIds` not enabled for current user
- `404` — session not found or not owned by current user
- `409` — `DUPLICATE_REQUEST`: a turn with the supplied `clientRequestId` already exists in this session; response body includes `{"turnId":"<existing-turn-uuid>"}` so the client can fetch results

---

## Attachment Storage and Context Replay

Attachments are stored inline as base64 in the `chat_turns.attachments` JSONB column:

```json
[{"type":"image","data":"<base64>","mime_type":"image/png","size_bytes":12345}]
```

When a follow-up prompt is submitted (FR-019), prior turns are included as context. Prior-turn
attachments are re-sent to models that support the attachment type; they are omitted (with a
`capability_notice` event) for models that do not. This is consistent with how the original
turn was routed.

**Size limits**: Maximum 10 MB per attachment (decoded). Maximum total request body: 50 MB.
Large attachment storage in PostgreSQL BYTEA is acceptable for MVP; blob storage (S3-compatible)
is the correct path for production scale but is out of scope for this feature.
