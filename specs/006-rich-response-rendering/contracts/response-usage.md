# Contract: Response Usage & Cache Fields

Additive, backward-compatible field additions to existing response payloads. No new endpoints; no
removed/renamed fields. Surfaces the normalized cache-token pair (clarification Q3) for the per-response
details affordance (FR-012) on both the in-app conversation and the public share view (FR-016/FR-018).

## Normalized cache fields

Two nullable integers added wherever per-response usage is already exposed:

| Field | Type | Meaning |
|-------|------|---------|
| `cacheReadTokens` | `number \| null` | Cache-read (cache-hit) input tokens, where the provider reports it. |
| `cacheWriteTokens` | `number \| null` | Cache-write/creation tokens, where the provider reports it. |

`null` ⇒ rendered as "—" (provider didn't report, or response predates cache capture / V029).

## Affected payloads (EXTEND)

### 1. `GET /api/v2/chat/sessions/{id}` → `ProviderResponseV2`

Add `cacheReadTokens`, `cacheWriteTokens` alongside the existing `inputTokens`, `outputTokens`,
`latencyMs`.

### 2. Chat SSE `model_complete` event

Add `cacheReadTokens`, `cacheWriteTokens` to the existing terminal event:

```
{ "event": "model_complete", "configuredModelId": "...", "modelId": "...",
  "inputTokens": 1234, "outputTokens": 567, "latencyMs": 890,
  "cacheReadTokens": 1024, "cacheWriteTokens": null, "responseId": "..." }
```

### 3. Shared session response DTO (`GET /api/v2/shared/{token}` via `ShareService`)

Add `cacheReadTokens`, `cacheWriteTokens` to each shared response. **Privacy boundary unchanged**
(FR-018): these are usage figures only — no owner id, IP, or connection details are added to the shared
payload.

## Out of scope for this contract

- 005's analytics aggregate payloads (`ModelAnalytics`, `SessionAnalytics`, `PublicModelAnalytics`) are
  **not** required to add cache fields for this feature; US4 targets the per-response details affordance
  in the conversation/share views. Adding cache to dashboards later remains additive and compatible.

## Compatibility

- Additive only: existing clients ignore the new fields; the new UI degrades to "—" when absent.
- No version bump (still `/api/v2`); no breaking change (Constitution II).
