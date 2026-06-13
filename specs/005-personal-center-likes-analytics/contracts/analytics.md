# Contract: Usage Analytics Dashboard

Personal endpoints require authentication and are **strictly scoped to the caller's own data**.
The public endpoint is unauthenticated and uses a separate anonymous projection. All queries are
read-only (Constitution V). Error schema: `{ "code", "message", "details" }`.

Common query params (all optional):
- `from`, `to`: ISO-8601 timestamps — time-range filter (FR-023).
- `configuredModelId`: filter personal analytics to one configured-model UUID (FR-023).
- Collection endpoints also accept `page` and `size`; defaults are `0` and `25`, and `size` MUST be
  between 1 and 100.

`cost` fields are estimates derived from immutable response pricing snapshots. Unknown estimates are
`null` (display `—`). Aggregate totals use `estimatedCostsByCurrency`; different currencies are never
summed together.

## GET /api/v2/analytics/summary  (NEW)

Top-line metrics for the caller across the selected range.

200:
```json
{
  "totalResponses": 1234,
  "successRate": 0.97,
  "avgLatencyMs": 1820,
  "totalInputTokens": 982334,
  "totalOutputTokens": 1233889,
  "estimatedCostsByCurrency": { "USD": 12.43, "CNY": 4.20 }
}
```

## GET /api/v2/analytics/by-model  (NEW)

Per-model breakdown (FR-021).

200:
```json
{
  "items": [
    {
      "configuredModelId": "uuid",
      "modelId": "literal-provider-model-id",
      "modelDisplayName": "…",
      "responseCount": 420,
      "successRate": 0.98,
      "avgLatencyMs": 1600,
      "p95LatencyMs": 4200,
      "inputTokens": 300000,
      "outputTokens": 410000,
      "estimatedCostsByCurrency": { "USD": 4.10 }
    }
  ],
  "page": 0, "size": 25, "totalElements": 4, "totalPages": 1
}
```
Paginated.

## GET /api/v2/analytics/by-session  (NEW)

Per-conversation breakdown (FR-021).

200:
```json
{
  "items": [
    {
      "sessionId": "uuid",
      "title": "…",
      "responseCount": 18,
      "models": ["…", "…"],
      "avgLatencyMs": 1700,
      "inputTokens": 21000,
      "outputTokens": 30000,
      "estimatedCostsByCurrency": { "USD": 0.31 },
      "successRate": 1.0
    }
  ],
  "page": 0, "size": 25, "totalElements": 42, "totalPages": 2
}
```
Paginated (Constitution API standards).

## GET /api/v2/analytics/responses  (NEW)

Per-response detail rows — the drill-down (FR-022). Owner-only fields (IP) included here.

200:
```json
{
  "items": [
    {
      "responseId": "uuid",
      "userId": "caller-uuid",
      "sessionId": "uuid",
      "createdAt": "…",
      "configuredModelId": "uuid",
      "modelId": "literal-provider-model-id",
      "modelDisplayName": "…",
      "protocol": "openai-compatible",
      "connectionId": "uuid",
      "connectionLabel": "…",
      "status": "complete",
      "latencyMs": 1820,
      "inputTokens": 512,
      "outputTokens": 740,
      "estimatedCost": { "amount": 0.01, "currency": "USD" },
      "clientIp": "203.0.113.7",
      "namedLikeCount": 2,
      "anonymousLikeCount": 5
    }
  ],
  "page": 0, "size": 50, "totalElements": 1234, "totalPages": 25
}
```
Paginated. `clientIp`, `userId`, connection fields, and configured-model UUID appear ONLY in this
owner-scoped detail, never in aggregate payloads (FR-025).

## GET /api/v2/analytics/timeseries  (NEW)

Owner-scoped daily trend buckets powering the dashboard line charts (latency, success rate, token
usage). Accepts `from`, `to`, `configuredModelId`. Not paginated (one row per active day in range).

200:
```json
{
  "items": [
    {
      "bucket": "2026-06-13",
      "responseCount": 42,
      "avgLatencyMs": 1820.5,
      "successRate": 0.97,
      "inputTokens": 30000,
      "outputTokens": 41000
    }
  ]
}
```
- Buckets are emitted only for days with at least one response, ordered ascending.
- `successRate` is 0..1; the dashboard renders it as a percentage.

## GET /api/v2/analytics/public/by-model  (NEW, PUBLIC)

Public anonymized aggregate required by Constitution V (FR-028/FR-029). Optional filters: `from`,
`to`, `protocol`, and literal `modelId`; plus bounded `page`/`size`.

200:
```json
{
  "items": [
    {
      "protocol": "openai-compatible",
      "modelId": "provider-model-id",
      "responseCount": 4200,
      "successRate": 0.98,
      "avgLatencyMs": 1600,
      "p95LatencyMs": 4200,
      "inputTokens": 3000000,
      "outputTokens": 4100000,
      "namedLikeCount": 230,
      "anonymousLikeCount": 91
    }
  ],
  "page": 0, "size": 25, "totalElements": 12, "totalPages": 1
}
```

This DTO MUST contain no user/session/IP/connection/configured-model/user-label/prompt/response
fields. Cost is omitted because configured prices are user-specific and currencies may differ.

**Behavioral contracts**:
- Every `complete` and `error` response is represented exactly once (FR-019; SC-005).
- Empty history → `items: []` / zeroed summary, never an error (SC-008).
- Personal results filtered by `from`/`to`/`configuredModelId` update accordingly (FR-023; SC-006).
- Every collection response uses `{items, page, size, totalElements, totalPages}` with `size ≤ 100`.
- Contract and integration tests assert the public DTO contains zero prohibited fields (SC-009).
