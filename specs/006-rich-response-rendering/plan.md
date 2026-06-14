# Implementation Plan: Rich Response Rendering, Previews & Share Export

**Branch**: `006-rich-response-rendering` | **Date**: 2026-06-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/006-rich-response-rendering/spec.md`

## Summary

Upgrade the conversation reading experience (in-app and on the public share view, from one shared
rendering pipeline) with six connected capabilities:

1. **Bounded, copyable blocks** — every fenced/code block is height-capped with internal scroll and its
   own copy control; the response bubble cap already exists (`ExpandableContent`) and is reused on both
   surfaces.
2. **Source / preview dual view** — Mermaid, PlantUML, and SVG fenced blocks render a visual preview by
   default with a preview⇄source toggle; HTML defaults to source and only renders/executes via the
   explicit run action (Q2). PlantUML is rendered by a **self-hosted** renderer behind a same-origin
   backend proxy so model content never leaves the platform (Q1).
3. **Runnable web sandbox** — self-contained HTML/CSS/JS (and SVG) runs in a sandboxed `<iframe>` with
   no `allow-same-origin`, so it cannot read the viewer's token/storage or navigate the host; the frame
   may reach the network for CDN assets (Q4).
4. **Response usage & cache detail** — latency, input/output tokens, and a normalized **cache-read /
   cache-write** token pair (Q3) move into a per-response details affordance. Cache capture is new:
   adapters parse provider cache usage → `LlmStreamEvent.ModelComplete` → an immutable `provider_responses`
   column pair (forward-only; historical rows show "—").
5. **Share-view parity** — all of the above work for anonymous visitors, with sandbox isolation and the
   share view's existing no-identity privacy boundary intact.
6. **Long-image export** — a client-side poster of the shared conversation with a QR code (encoding the
   share URL) top-right and the conversation below, in the platform visual style.

Technical approach: extend the existing shared Markdown pipeline (`markdownComponents.tsx` →
`MarkdownBlock` → both `StreamingMarkdown` and `MarkdownRenderer`) so parity is structural, not
duplicated. Add focused frontend components (per-block code card, renderable previews, runnable sandbox,
details popover, share-export poster) and three frontend libs (`mermaid`, a QR generator, an
HTML→image exporter) plus `dompurify` for SVG. Add one backend render-proxy endpoint and one
docker-compose `plantuml` service. Backend cache capture flows through the existing immutable
write-once path — one new Flyway migration, adapter parsing, and DTO field additions; no second write
path (Constitution IV).

## Technical Context

**Language/Version**: Kotlin on JVM, Java 21 (backend); TypeScript 5 / Node.js 24 (frontend)
**Primary Dependencies**: Spring Boot WebFlux, Spring Data JPA/Hibernate, Flyway (backend); Next.js App
Router, react-markdown (existing), react-syntax-highlighter (existing) + NEW `mermaid`, `qrcode`,
`html-to-image`, `dompurify` (frontend); self-hosted `plantuml/plantuml-server` (infra)
**Storage**: PostgreSQL — extend existing immutable `provider_responses` with two cache-token columns
**Testing**: JUnit 5 + Testcontainers + MockK (backend); Vitest + Testing Library, Playwright (frontend)
**Target Platform**: Linux server (Docker Compose); modern browsers
**Project Type**: Web application (Kotlin backend + Next.js frontend)
**Performance Goals**: No regression to the existing O(n) block-by-block streaming render; Mermaid and
the runnable sandbox are lazy/dynamic so they never load until a renderable/runnable block exists;
analytics dashboard targets from 005 unchanged
**Constraints**: `provider_responses` stays INSERT-once/immutable (cache columns nullable, forward-only);
sandbox iframe MUST omit `allow-same-origin`; PlantUML rendering MUST stay within the platform trust
boundary (no third-party public service); SVG previews sanitized before inlining; share view exposes no
owner identity/IP/connection; new render endpoint is size-limited and proxies only the configured
internal PlantUML URL (no SSRF surface); snake_case schema via Flyway only
**Scale/Scope**: 1 Flyway migration, ~4 backend files touched (adapters, stream event, ChatService,
DTOs) + 1 new render-proxy controller + 1 compose service; ~8 new/edited frontend components, 4 new
deps, parity on the existing share page, 1 export poster

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance |
|-----------|-----------|
| I. Provider-Agnostic Abstraction | ✅ Cache-usage parsing stays **inside each adapter**; only a normalized `(cacheReadTokens, cacheWriteTokens)` pair crosses into shared `LlmStreamEvent`/orchestration. No provider-specific logic in core/UI. |
| II. API-First Design | ✅ Cache figures exposed through existing REST/SSE response DTOs; PlantUML rendering exposed as a versioned `/api/v2/render/plantuml` endpoint consumed via the same-origin Next proxy — no direct DB/back-channel. |
| III. Concurrent Execution & Streaming | ✅ Cache tokens piggyback on the terminal `model_complete` event already emitted per model; no added serialization, no change to concurrent dispatch or stream start. |
| IV. Data Integrity & Immutable Sessions | ✅ Two nullable cache columns added to `provider_responses` via Flyway (snake_case); rows stay INSERT-once. Historical rows remain NULL → "—" (forward-only, no backfill/UPDATE). |
| V. Observability & Analytics | ✅ Every call now also records cache usage where the provider reports it; figures are owner/viewer-visible detail, not added to anonymous aggregates. |
| VI. Security & User Key Privacy | ✅ Runnable artifacts sandboxed without `allow-same-origin` (no token/storage/host access) for in-app **and** anonymous share visitors; PlantUML self-hosted (model content stays in trust boundary); SVG sanitized; render proxy talks only to the configured internal URL; share view keeps zero identity. No API-key handling touched. |
| VII. Simplicity & Horizontal Scalability | ✅ Reuses the existing shared markdown pipeline, `ExpandableContent`, and `CopyButton` rather than a second renderer; cache capture rides the existing write path. One new stateless infra service (PlantUML) is the minimum needed to honor the self-hosted clarification (Q1) — stateless, horizontally scalable, no locks. |
| VIII. UX Consistency & Visual Coherence | ✅ Block cards, toggles, details popover, and the export poster reuse the design system (stone palette, `#c96442` accent, `rounded-2xl`/`rounded-xl` cards); both surfaces visually verified (Playwright) before done. |

**Result**: PASS — no violations. The new PlantUML infra service is justified by clarification Q1
(self-hosted rendering); it is stateless and lock-free, so Complexity Tracking is not required.

## Project Structure

### Documentation (this feature)

```text
specs/006-rich-response-rendering/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── plantuml-render.md     # POST /api/v2/render/plantuml
│   └── response-usage.md      # cache fields on response/SSE/shared DTOs
├── checklists/
│   └── requirements.md  # Spec quality checklist (already present)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
backend/src/main/kotlin/com/octopusllm/
├── llm/
│   ├── LlmStreamEvent.kt          # EXTEND: ModelComplete += cacheReadTokens, cacheWriteTokens
│   └── adapter/
│       ├── AnthropicAdapter.kt    # EXTEND: parse cache_creation_input_tokens / cache_read_input_tokens
│       ├── OpenAiCompatAdapter.kt # EXTEND: parse usage.prompt_tokens_details.cached_tokens → cache-read
│       └── MiniMaxAdapter.kt      # EXTEND: map cache usage if present, else nulls
├── chat/
│   ├── ProviderResponse.kt        # EXTEND: cacheReadTokens, cacheWriteTokens columns
│   ├── ChatService.kt             # EXTEND: persist cache tokens from ModelComplete
│   └── ChatControllerV2.kt        # EXTEND: ProviderResponseV2 + model_complete SSE carry cache fields
├── share/
│   └── ShareService.kt            # EXTEND: shared response DTO carries cache fields (no identity)
└── render/                        # NEW: self-hosted diagram render proxy
    ├── PlantUmlRenderController.kt # POST /api/v2/render/plantuml (size-limited; public-safe)
    └── PlantUmlRenderService.kt    # WebClient → internal PLANTUML_SERVER_URL; returns SVG

backend/src/main/resources/db/migration/
└── V029__provider_response_cache_tokens.sql   # cache_read_tokens, cache_write_tokens (nullable)

frontend/src/
├── components/chat/
│   ├── markdownComponents.tsx     # EXTEND: route fenced blocks to CodeBlock / renderable previews
│   ├── CodeBlock.tsx              # NEW: per-block height cap + language label + per-block copy
│   ├── BlockViewToggle.tsx        # NEW: preview⇄source segmented control wrapper
│   ├── MermaidPreview.tsx         # NEW: lazy dynamic mermaid render
│   ├── PlantUmlPreview.tsx        # NEW: posts source to /api/v2/render/plantuml, shows SVG
│   ├── SvgPreview.tsx             # NEW: DOMPurify-sanitized inline SVG
│   ├── RunnableArtifact.tsx       # NEW: sandboxed iframe (no allow-same-origin), explicit Run
│   └── ResponseDetails.tsx        # NEW: details popover (latency, in/out, cache read/write)
├── components/ui/
│   └── CopyButton.tsx             # EXTEND: surface copy failure (FR-003) instead of silent catch
├── components/share/
│   ├── SharedConversation.tsx     # EXTEND: reuse ExpandableContent + ResponseDetails (parity)
│   └── ShareExportButton.tsx      # NEW: long-image poster (html-to-image + QR top-right)
├── components/chat/ModelResponsePanel.tsx  # EXTEND: move usage line into ResponseDetails
└── lib/
    ├── markdown/blocks.ts         # NEW: classify fence language → render strategy
    └── api/render.ts              # NEW: client for the plantuml render proxy (same-origin)

docker-compose.yml                 # EXTEND: add internal `plantuml` service (no public port)
frontend/src/app/api/.../route.ts  # EXTEND/VERIFY: proxy passes /api/v2/render/* upstream intact
```

**Structure Decision**: Web application (Option 2). Reuse `backend/` (Kotlin/Spring WebFlux) and
`frontend/` (Next.js App Router). The single shared Markdown pipeline is the leverage point: extending
`markdownComponents.tsx` makes the in-app conversation and the public share view gain the same
rendering at once (FR-016 parity is structural). A new `render` backend package + one stateless
docker-compose service provide self-hosted PlantUML; cache capture extends the existing immutable
write-once response path.

## Complexity Tracking

> No Constitution Check violations — this section is intentionally empty. The new PlantUML service is
> justified by clarification Q1 and is stateless/lock-free.
