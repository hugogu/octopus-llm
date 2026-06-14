# Phase 1 Data Model: Rich Response Rendering, Previews & Share Export

This feature is render-heavy; its only persisted change is two cache-token columns on the existing
immutable `provider_responses` table. The remaining "entities" are render-time, derived structures with
no storage.

## Persisted change

### `provider_responses` (EXTEND — migration V029)

Existing immutable, INSERT-once table (see V008/V017/V023). Add two nullable columns:

| Column | Type | Null | Notes |
|--------|------|------|-------|
| `cache_read_tokens` | `INTEGER` | yes | Provider-reported cache-read (cache-hit) input tokens. NULL when the provider does not report it or for rows created before V029. |
| `cache_write_tokens` | `INTEGER` | yes | Provider-reported cache-write/creation tokens. NULL when not reported (e.g. OpenAI-compatible) or pre-V029. |

Constraints / rules:

- Both columns nullable with optional non-negative CHECK (`>= 0`), mirroring the existing token columns.
- **No UPDATE / no backfill** — immutability (Constitution IV). Historical rows stay NULL and render as
  "—". Forward-only per clarification Q3.
- Populated at the same INSERT that writes `input_tokens`/`output_tokens` on terminal completion.

JPA mapping (`ProviderResponse.kt`): add `cacheReadTokens: Int? = null`, `cacheWriteTokens: Int? = null`
as `@Column(name = "cache_read_tokens" / "cache_write_tokens")`.

## In-flight / transport model (no storage)

### `LlmStreamEvent.ModelComplete` (EXTEND)

Add `cacheReadTokens: Int?` and `cacheWriteTokens: Int?`. Emitted on each model's terminal completion;
`ChatService` copies them into the `provider_responses` INSERT.

Per-adapter mapping (Constitution I — parsing stays in the adapter):

| Provider | cache-read source | cache-write source |
|----------|-------------------|--------------------|
| Anthropic | `usage.cache_read_input_tokens` | `usage.cache_creation_input_tokens` |
| OpenAI-compatible | `usage.prompt_tokens_details.cached_tokens` | — (null) |
| MiniMax / other | provider field if present | provider field if present, else null |

### Response DTOs (EXTEND)

Add `cacheReadTokens: number \| null` and `cacheWriteTokens: number \| null` to:

- `ProviderResponseV2` (GET session) and the `model_complete` SSE event payload (chat).
- The shared-response DTO in `ShareService` (share parity). These are usage figures, not identity, so
  they stay within the share view's privacy boundary (FR-018) — no owner id / IP / connection added.

## Render-time derived structures (frontend, not persisted)

### Renderable Block (`lib/markdown/blocks.ts`)

Derived when `markdownComponents.code()` receives a fenced block.

| Field | Meaning |
|-------|---------|
| `language` | Lower-cased fence tag (e.g. `mermaid`, `plantuml`, `svg`, `html`, or other). |
| `strategy` | One of `code` (bounded+copyable only), `diagram-preview` (mermaid/plantuml/svg, preview default), `html-runnable` (source default, explicit run). |
| `source` | Exact raw block text (used by preview, source view, and per-block copy). |

Classification rules:
- `mermaid` → `diagram-preview` via Mermaid (client).
- `plantuml`/`puml` → `diagram-preview` via render proxy.
- `svg` → `diagram-preview` via sanitized inline SVG.
- `html` → `html-runnable` (default source; run → sandbox).
- anything else → `code`.

### Block View State (component-local)

`view: 'preview' | 'source'`. Initial value by strategy: `diagram-preview` → `preview`;
`html-runnable` and `code` → `source` (HTML never auto-renders, Q2). `run: boolean` for
`html-runnable`, false until the user clicks Run (FR-010). Render outcome `ok | error` drives the
scoped inline error (FR-006). None of this is persisted.

### Response Usage Detail (component view-model)

Read-only projection shown in `ResponseDetails.tsx`: `{ latencyMs, inputTokens, outputTokens,
cacheReadTokens, cacheWriteTokens }`, each rendered as a value or "—". Sourced directly from the
response DTO; no new fetch.

### Share Poster (transient artifact)

Built in the browser for export: a styled DOM node = QR(data-url of `window.location` share URL) +
fully-expanded conversation content, rasterized to PNG. Not stored; not a server resource.
