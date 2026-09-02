# Contract: Anonymous Model Catalogue and Chat

These routes are public, same-origin calls under `/api/v2/anonymous`. They do not require a bearer token and return `Cache-Control: no-store`. They must be added to the explicit public security allowlist; no other chat route becomes public.

## Public model catalogue

```http
GET /api/v2/anonymous/models?page=0&size=100
```

```json
{
  "items": [
    {
      "id": "configured-model-uuid",
      "modelId": "provider-model-id",
      "displayName": "Public model",
      "protocol": "openai-compatible",
      "capabilities": { "streaming": true, "vision": false, "tools": false }
    }
  ],
  "page": 0,
  "size": 100,
  "totalElements": 1,
  "totalPages": 1
}
```

The endpoint uses the repository collection shape and constrains `size` to `1..100`; the frontend can request subsequent pages or maintain the current selection while paging. Only models satisfying `is_builtin`, `is_enabled`, and `is_anonymous_allowed` are returned. The endpoint omits connection IDs, URLs, credentials, owners, custom parameters, private capability overrides, and account data. An empty `items` array is a valid response.

## Stream an anonymous turn

```http
POST /api/v2/anonymous/chat/turns
Accept: text/event-stream
Content-Type: application/json
```

```json
{
  "clientConversationId": "browser-conversation-uuid",
  "clientRequestId": "browser-request-uuid",
  "promptText": "Compare these approaches...",
  "selectedConfiguredModelIds": ["configured-model-uuid"],
  "history": [
    { "role": "USER", "content": "Earlier question" },
    { "role": "ASSISTANT", "content": "Earlier answer" }
  ]
}
```

Validation rules:

- `promptText` is non-blank and within the configured anonymous prompt-byte limit.
- `selectedConfiguredModelIds` is non-empty, deduplicated, and within the configured anonymous model-count limit.
- `history` contains only `USER` and `ASSISTANT` content and stays within the configured turn and byte limits.
- Attachments, tool calls, tool results, system messages, media IDs, session IDs, and provider configuration are rejected.
- Every selected configured-model UUID is re-queried immediately before dispatch and must still be a built-in, enabled, anonymous-allowed model. A stale or tampered ID returns a safe authorization error without revealing whether a private model exists.

The service applies the dedicated anonymous request-rate limit and expiring per-client stream lease before committing SSE headers. It also applies the provider execution deadline. The rate/concurrency key is derived from the trusted client IP with an HMAC and is never returned to the client.

## SSE events

Events reuse the normalized authenticated stream shape where possible so the existing client parser can be shared. Each model-specific event includes `configuredModelId`.

```text
event: status
data: {"state":"STARTED"}

event: token
data: {"configuredModelId":"configured-model-uuid","text":"..."}

event: reasoning
data: {"configuredModelId":"configured-model-uuid","text":"..."}

event: model_complete
data: {"configuredModelId":"configured-model-uuid","status":"COMPLETE","responseText":"..."}

event: model_error
data: {"configuredModelId":"configured-model-uuid","status":"ERROR","errorCode":"PROVIDER_TIMEOUT","errorMessage":"The model did not finish in time."}

event: result
data: {"state":"COMPLETE"}
```

The server may finish or fail an already-started stream according to its lifecycle when policy changes. No new anonymous turn or retry is accepted for a revoked/disabled model. Stream errors after headers use safe `model_error`/`error` events and never include keys, endpoints, prompts from another user, raw provider payloads, or stack traces.

## HTTP error behavior

- `400`: invalid prompt/history/model count or unsupported attachment/tool input.
- `401`: not used for these two public endpoints; authenticated account routes remain protected.
- `404` or safe `403`: no currently eligible selected model; do not reveal private-model existence.
- `429`: request-rate or active-stream limit exceeded.
- `503`: public catalogue or provider execution temporarily unavailable.

All errors are safe JSON before SSE headers and include a retryable, human-readable message plus a stable non-sensitive error code. Anonymous execution emits structured metrics with an anonymous marker but writes no server conversation records.
