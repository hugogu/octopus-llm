# Phase 0 Research: Rich Response Rendering, Previews & Share Export

All Technical Context unknowns are resolved below. Each entry records the decision, rationale, and
alternatives considered. Decisions are grounded in the existing codebase (the shared markdown pipeline,
the immutable `provider_responses` write path, and the public share view).

## R1. Shared rendering pipeline as the parity leverage point

**Decision**: Implement all block-rendering changes inside `markdownComponents.tsx` (the `Components`
override map) and the per-block components it delegates to. Do not fork separate renderers for chat vs
share.

**Rationale**: `markdownComponents` is consumed by `MarkdownBlock`, which is used by both
`StreamingMarkdown` (live chat) and `MarkdownRenderer` (settled content + share view). Extending the
shared map makes FR-016 (share parity) structural rather than a duplicated effort. The existing
block-splitting (`splitIntoBlocks`) already keeps fenced blocks intact during streaming, so per-block
components receive whole fence content.

**Alternatives considered**: A dedicated share renderer (rejected — duplicates logic, guarantees drift);
a post-render DOM transform (rejected — fights React, breaks streaming memoization).

## R2. Per-block bounded height + copy

**Decision**: New `CodeBlock.tsx` wraps fenced code with a max-height scroll region, a language label,
and a per-block `CopyButton` fed the block's exact raw source. Reuse the existing `ExpandableContent`
(collapsedHeight ≈ 420) for the **response bubble** cap — it already provides cap + fade + Show
more/less and is already wired in `ModelResponsePanel`; add it to `SharedConversation` for parity.

**Rationale**: FR-001 (block cap) and FR-002 (bubble cap) are distinct scopes; the bubble cap already
exists and only needs reuse on the share side. Per-block copy (FR-003) needs the raw fence string, which
the `code()` component receives as `children`.

**Copy-failure surfacing (FR-003)**: extend `CopyButton` so a failed `navigator.clipboard.writeText`
(or missing clipboard in an insecure context) flips to a visible error state instead of the current
silent `console.error`.

**Alternatives considered**: CSS-only `max-height` without scroll affordance (rejected — hides content
with no escape); copying via text selection (rejected — not one-click, not per-block).

## R3. Mermaid rendering (client-side)

**Decision**: Render Mermaid in the browser via the `mermaid` npm package, loaded with a dynamic
`import()` inside `MermaidPreview.tsx` so it is code-split and never loaded unless a Mermaid block is
present. Render to SVG in a `useEffect`, guard with try/catch → scoped error (FR-006).

**Rationale**: Mermaid renders fully client-side, so model content never leaves the browser — strongest
privacy posture, no infra. Dynamic import keeps it off the critical bundle for the common no-diagram
case (performance goal).

**Alternatives considered**: Server-side Mermaid (rejected — needs headless browser/infra for zero
privacy gain since it can run locally); eager import (rejected — bundle bloat).

## R4. PlantUML rendering (self-hosted, behind same-origin proxy) — resolves Q1

**Decision**: Run the official `plantuml/plantuml-server` image as an **internal** docker-compose
service (no published host port). Add a backend `POST /api/v2/render/plantuml` endpoint that accepts the
raw PlantUML source, forwards it to the internal server via `WebClient`, and returns `image/svg+xml`.
The browser (chat and share) calls the endpoint same-origin through the existing Next proxy.
`PlantUmlPreview.tsx` posts source and inlines the returned SVG (sanitized, see R6). On any failure the
block falls back to source view with a notice (FR-006a).

**Rationale**: PlantUML cannot render purely in-browser. The clarification (Q1) mandates the renderer
stay inside the platform trust boundary, so the public plantuml.com service is disallowed. A backend
proxy (a) keeps the plantuml server unexposed, (b) avoids browser CORS, and (c) gives a single place for
a size limit. The endpoint is **public-safe** (must serve anonymous share visitors), so it requires no
auth but enforces a request-size cap and only ever talks to the configured internal URL (no SSRF). The
server is stateless → horizontally scalable, lock-free (Constitution VII).

**Configuration**: `PLANTUML_SERVER_URL` env on the backend (e.g. `http://plantuml:8080`); add the
service to `docker-compose.yml` on the internal network only.

**Alternatives considered**: Public plantuml.com encode+fetch (rejected — sends model content to a third
party, violates Q1/Constitution VI); bundling a JVM PlantUML lib directly into the backend (rejected —
PlantUML drags Graphviz/native deps into the app image and the upstream server image already isolates
that); client-side WASM PlantUML (rejected — immature, heavy).

## R5. Runnable HTML/CSS/JS/SVG sandbox — resolves Q2/Q4

**Decision**: `RunnableArtifact.tsx` renders the combined artifact into an `<iframe>` using `srcdoc`
with `sandbox="allow-scripts allow-popups allow-forms allow-modals"` — **deliberately excluding
`allow-same-origin`**. HTML blocks default to **source view** and only mount the iframe after an
explicit "Run" click (Q2). The frame may make outbound network requests (default iframe behavior, Q4);
omitting `allow-same-origin` forces it into an opaque origin so it cannot read host cookies,
`localStorage`, the auth token, or navigate the top frame.

**Rationale**: Omitting `allow-same-origin` is the browser-enforced isolation that satisfies FR-009 for
both in-app and anonymous share visitors (FR-017). Explicit-run satisfies FR-010 ("no auto-execution").
`srcdoc` avoids hosting artifact content at a real origin.

**SVG (FR-011)**: rendered as a sanitized inline preview by default (R6), not via the JS sandbox, since
SVG previews are visual, not interactive; any embedded scripting is stripped by sanitization.

**Alternatives considered**: `allow-same-origin` + CSP (rejected — same-origin re-grants storage/cookie
access; CSP is weaker than origin isolation); rendering HTML inline via `rehype-raw` (rejected — would
execute in the host origin; unsafe); a separate sandbox domain (rejected — infra/CORS overhead vs
`srcdoc` opaque origin which is sufficient).

## R6. SVG sanitization

**Decision**: Sanitize SVG source with `DOMPurify` (`USE_PROFILES: { svg: true, svgFilters: true }`,
scripts stripped) before inlining in `SvgPreview.tsx`.

**Rationale**: Inlined SVG executes in the host origin, so `<script>`/event handlers must be removed.
DOMPurify is the standard, well-audited sanitizer. `rehype-sanitize` (already installed) operates on
markdown HAST, not on a raw SVG string extracted from a fenced block, so a direct string sanitizer is
the right tool here.

**Alternatives considered**: Render SVG inside the iframe sandbox (rejected — heavier for a static image
and loses crisp inline scaling); trust SVG unsanitized (rejected — XSS).

## R7. Cache-token capture & normalization — resolves Q3

**Decision**: Add a normalized pair `cacheReadTokens` / `cacheWriteTokens` to
`LlmStreamEvent.ModelComplete`, populated per adapter:

- **Anthropic**: `usage.cache_read_input_tokens` → cache-read; `usage.cache_creation_input_tokens` →
  cache-write.
- **OpenAI-compatible**: `usage.prompt_tokens_details.cached_tokens` → cache-read; no write dimension →
  null.
- **MiniMax / others**: map if the provider reports cache usage; otherwise both null.

Persist the pair into two new nullable columns on `provider_responses` (V029). Surface through
`ProviderResponseV2`, the `model_complete` SSE event, and the shared-response DTO. Display "—" when null.

**Rationale**: Parsing stays inside each adapter (Constitution I); the normalized pair is provider-
neutral. The columns ride the existing INSERT-once write — no new write path (Constitution IV). Because
records are immutable, only responses generated after V029 ships carry values; older rows are NULL → "—"
(forward-only, matches the spec's edge case). This mirrors how 005 added price-snapshot columns to the
same table.

**Alternatives considered**: Single "cached tokens" field (rejected by Q3 — loses read/write
distinction); raw provider usage JSON blob (rejected by Q3 — no normalization, leaks provider quirks to
the UI); a separate cache-stats table (rejected — second source of truth, Constitution IV/VII).

## R8. Response usage details affordance — FR-012/FR-013

**Decision**: New `ResponseDetails.tsx` — a popover/disclosure opened from an `Info` control in the
response header — presents latency, input tokens, output tokens, cache-read, and cache-write. Remove the
always-on inline usage span from `ModelResponsePanel` header; the share view gets the same control.

**Rationale**: FR-013 wants usage out of the main flow. A popover keeps the header compact and matches
the existing `Info`/capabilities affordance already present in `ModelResponsePanel`.

**Alternatives considered**: Keep the inline span (rejected — FR-013 says move it); a separate details
route (rejected — overkill, breaks "Connected" inline reading).

## R9. Long-image share export — FR-019..022

**Decision**: `ShareExportButton.tsx` renders an off-screen poster node styled with the platform design
system (warm canvas gradient, stone borders, rounded cards), with a QR code (generated by `qrcode` to a
data URL) pinned top-right and the full conversation below. Force-expand all `ExpandableContent`/block
caps in the poster node, then rasterize with `html-to-image` (`toPng`) and trigger a download. Empty
conversations render header + QR + empty-state (FR-022).

**Rationale**: Fully client-side (matches the spec assumption — no server image hosting). `html-to-image`
captures live DOM with CSS, preserving the design system. `qrcode` encodes the current `window.location`
share URL (FR-020); it is a point-in-time artifact unaffected by later revocation (spec US6 AS5).

**Alternatives considered**: Server-side render (Puppeteer) (rejected — infra + out of scope per spec);
`html2canvas` (viable alternative; `html-to-image` chosen for better CSS-gradient/SVG fidelity); canvas
hand-drawing (rejected — would not match the design system).

## R10. Streaming & malformed-content resilience — FR-024

**Decision**: Renderable previews only attempt to render **settled** blocks. While streaming, a fenced
block that is still open renders as source/code (it is incomplete); previews mount once the block is
closed/`complete`. All preview renderers wrap their parse in try/catch and emit a scoped inline error,
never throwing into the surrounding `MarkdownBlock`.

**Rationale**: The existing `splitIntoBlocks` already isolates the open trailing fence during streaming;
deferring preview to settled content avoids parsing half a diagram each tick (matches the O(n) streaming
design) and satisfies FR-024 / the streaming edge case.

**Alternatives considered**: Live-render every tick (rejected — perf + constant parse errors mid-stream).

## Resolved unknowns summary

| Unknown | Resolution |
|---------|-----------|
| Mermaid render location | Client-side, dynamic import (R3) |
| PlantUML render location (Q1) | Self-hosted server + same-origin backend proxy (R4) |
| HTML default behavior (Q2) | Source by default, explicit Run into sandbox (R5) |
| Sandbox network (Q4) | Allowed; isolation via no `allow-same-origin` (R5) |
| Cache field model (Q3) | Normalized read/write pair, per-adapter mapping, forward-only (R7) |
| SVG safety | DOMPurify sanitize, inline (R6) |
| Long-image generation | Client-side html-to-image + qrcode (R9) |
| Streaming safety | Defer preview to settled blocks, scoped errors (R10) |
| New frontend deps | `mermaid`, `qrcode`, `html-to-image`, `dompurify` |
| New infra | internal `plantuml` docker-compose service |
