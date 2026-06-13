# Contract: Usage Analytics Dashboard

All endpoints require authentication and are **strictly scoped to the caller's own data** — no
other user's records, IPs, or identities are ever returned (FR-024). All queries are read-only
(Constitution V). Error schema: `{ "code", "message", "details" }`.

Common query params (all optional):
- `from`, `to`: ISO-8601 timestamps — time-range filter (FR-023).
- `modelId`: filter to one model/configured model (FR-023).

`cost` fields are estimates derived from `model_definitions` pricing; `null` when price/tokens are
unknown (display `—`), and excluded from cost sums.

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
  "estimatedCost": { "amount": 12.43, "currency": "USD" }
}
```

## GET /api/v2/analytics/by-model  (NEW)

Per-model breakdown (FR-021).

200:
```json
{
  "items": [
    {
      "modelId": "…",
      "modelDisplayName": "…",
      "responseCount": 420,
      "successRate": 0.98,
      "avgLatencyMs": 1600,
      "p95LatencyMs": 4200,
      "inputTokens": 300000,
      "outputTokens": 410000,
      "estimatedCost": { "amount": 4.10, "currency": "USD" }
    }
  ]
}
```

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
      "estimatedCost": { "amount": 0.31, "currency": "USD" },
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
      "sessionId": "uuid",
      "createdAt": "…",
      "modelDisplayName": "…",
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
Paginated. `clientIp` and identity-bearing fields appear ONLY in this owner-scoped detail, never in
any aggregate above (FR-025).

**Behavioral contracts**:
- Every `complete` and `error` response is represented exactly once (FR-019; SC-005).
- Empty history → `items: []` / zeroed summary, never an error (SC-008).
- Results filtered by `from`/`to`/`modelId` update accordingly (FR-023; SC-006).
