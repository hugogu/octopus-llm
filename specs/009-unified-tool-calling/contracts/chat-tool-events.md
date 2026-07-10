# API Contract: Chat Tool Events

**Feature**: Unified Tool Calling and Time Awareness  
**Date**: 2026-07-10  
**Endpoint**: `POST /api/v2/chat/sessions/{sessionId}/turns` (Server-Sent Events, `text/event-stream`)  
**Scope**: This contract extends the existing chat streaming API with new event types for tool calling. No new REST endpoints are introduced in the first release.

## Event Format

All events are JSON objects delivered as SSE `data:` lines. Each event has a `type` field.

### Existing Event Types (unchanged)

- `token`
- `reasoning`
- `model_complete`
- `error`
- `capability_notice`

### New Event Types

- `tool_call`
- `tool_result`
- `tool_status`

## Tool Definition Attached to the Turn

Before the model emits a `tool_call` event, the backend sends the available tool definitions to the model as part of the `LlmRequest`. The frontend does not receive tool definitions in the SSE stream; it only receives event notifications.

## Tool Call Event

Sent when a model decides to invoke a tool.

```json
{
  "type": "tool_call",
  "modelId": "configured-model-uuid",
  "providerResponseId": "provider-response-uuid",
  "callId": "tool-call-uuid",
  "toolName": "stock_quote",
  "arguments": {
    "symbol": "600519"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| type | string | `tool_call` |
| modelId | UUID | The configured model that emitted the call |
| providerResponseId | UUID | The provider response snapshot this call belongs to |
| callId | UUID | Unique identifier for this tool call |
| toolName | string | Name of the tool being called |
| arguments | object | Arguments provided by the model |

## Tool Status Event

Sent to inform the client about the execution state of a tool call.

```json
{
  "type": "tool_status",
  "callId": "tool-call-uuid",
  "status": "running",
  "toolName": "stock_quote"
}
```

Possible `status` values: `pending`, `running`, `completed`, `failed`, `timeout`.

## Tool Result Event

Sent after a tool has been executed. The result is fed back to the requesting model(s) and also emitted to the client for transparency.

```json
{
  "type": "tool_result",
  "callId": "tool-call-uuid",
  "toolName": "stock_quote",
  "status": "success",
  "result": {
    "symbol": "600519",
    "price": 1680.5,
    "currency": "CNY",
    "updatedAt": "2026-07-10T10:30:00+08:00"
  }
}
```

For a failed or timed-out call:

```json
{
  "type": "tool_result",
  "callId": "tool-call-uuid",
  "toolName": "stock_quote",
  "status": "failed",
  "error": "External stock API returned 503 after retry."
}
```

## Model Complete Event (extended)

The existing `model_complete` event may now include a summary of tool calls that contributed to the response.

```json
{
  "type": "model_complete",
  "modelId": "configured-model-uuid",
  "providerResponseId": "provider-response-uuid",
  "toolCalls": ["tool-call-uuid-1", "tool-call-uuid-2"]
}
```

The `toolCalls` array is omitted or empty when no tools were invoked.

## Error Handling

- If a tool call fails after retry, the backend emits a `tool_result` event with `status: failed` (or `timeout`) and continues the conversation so the model can decide how to respond.
- If the model cannot recover, the final response includes a user-facing explanation of the unavailable data.
- Client-side errors (e.g., malformed event) are handled through the existing SSE error handling path.

## Notes

- The unified event format is the contract between the backend and the frontend. Provider-specific differences (OpenAI `tool_calls`, Anthropic `tool_use`, etc.) are normalized by the adapter layer before events reach the application layer.
- Deduplication happens on the backend: the client may receive multiple `tool_call` events (one per model) but only one `tool_result` event for identical calls, and all requesting models share that result.
