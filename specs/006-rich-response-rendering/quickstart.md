# Quickstart: Rich Response Rendering, Previews & Share Export

End-to-end validation guide. Proves FR-001..024 and SC-001..008. Implementation details live in
`tasks.md`; this file is the run/verify checklist.

## Prerequisites

- Local stack via docker-compose (frontend :3001, backend :8080, db, mailhog) per the repo's existing
  dev setup. Check `docker ps` / `lsof` before starting — do not spin up duplicates.
- New internal `plantuml` service running on the compose network (no host port). Verify the backend can
  reach it: `docker compose exec backend wget -qO- http://plantuml:8080/ >/dev/null && echo ok`.
- Frontend deps installed: `mermaid`, `qrcode`, `html-to-image`, `dompurify` (+ `@types/dompurify`,
  `@types/qrcode` if needed).
- A signed-in account with at least one saved conversation; ability to create a share link (005).

## Gates (must pass before "done")

```bash
# Backend
cd backend && ./gradlew build            # compile + unit/integration (incl. Testcontainers)

# Frontend
cd frontend && npx tsc --noEmit          # zero type errors
cd frontend && npx vitest run            # component/unit tests
cd frontend && npx playwright test       # visual + behavior (chat & share)
```

## Seed renderable content

Create a conversation turn whose answer contains, in one message:

- a long (>400 line) code block,
- a ```mermaid``` block,
- a ```plantuml``` block,
- an ```svg``` block,
- an ```html``` block that uses inline CSS + a small `<script>`.

(You can paste these into a prompt and ask a model to echo them, or use a fixture conversation.)

## Validation scenarios

### US1 — bounded & copyable (FR-001..003, SC-001/002)
1. The 400-line code block renders capped with internal scroll; the response bubble caps with
   "Show more" (reused `ExpandableContent`). Neither exceeds its max height.
2. Each fenced block shows its own copy control; copying one places exactly that block's text on the
   clipboard with a check-mark confirmation.
3. In an insecure/clipboard-denied context, the copy control shows a visible failure state (not silent).

### US2 — source/preview toggle (FR-004..007, SC-003)
4. Mermaid, PlantUML, and SVG blocks show a **visual preview by default** with a preview⇄source toggle;
   source view shows exact original text.
5. The HTML block shows **source by default** (no auto-render) with a Run control.
6. Corrupt one block (e.g. invalid mermaid) → a scoped inline error appears for that block only; the
   rest of the message renders; source is still viewable.
7. PlantUML preview comes from `POST /api/v2/render/plantuml` (self-hosted). Confirm via network panel
   the browser hits the same-origin proxy, not plantuml.com. Stop the `plantuml` service → block falls
   back to source view with a notice (FR-006a), no broken preview.

### US3 — runnable sandbox (FR-008..011, SC-004)
8. Click Run on the HTML block → it executes in an `<iframe>` and the live result shows. Inspect the
   iframe: `sandbox` attribute present and **without** `allow-same-origin`.
9. Security check: the artifact cannot read the auth token / `localStorage` / cookies and cannot
   navigate the top frame (attempt from inside the artifact → blocked). Outbound network (e.g. a CDN
   `<script>`) is allowed and loads.
10. SVG renders as a sanitized image-like preview; an SVG with an embedded `<script>` does not execute.

### US4 — usage & cache details (FR-012..015, SC-005)
11. Open a response's details/info affordance → shows latency, input tokens, output tokens, cache-read,
    cache-write. The usage line no longer clutters the main answer body.
12. Generate a NEW response on a cache-capable provider (Anthropic) with caching active → cache-read/
    cache-write show real numbers. A pre-existing (pre-V029) response shows "—" for cache fields.
13. Verify the SSE `model_complete` event and `GET /api/v2/chat/sessions/{id}` carry `cacheReadTokens`/
    `cacheWriteTokens` (network panel).

### US5 — share parity (FR-016..018, SC-006)
14. Create a share link; open it logged out. Repeat scenarios 1–11 on the share page: bounded/copyable
    blocks, preview/source toggles, sandboxed run (no `allow-same-origin`), and the usage/cache details.
15. Confirm the shared payload exposes cache figures but **no** owner identity, IP, or connection
    (inspect `GET /api/v2/shared/{token}`).

### US6 — long-image export (FR-019..022, SC-007)
16. On the share page, trigger long-image export → a single tall PNG downloads.
17. The PNG has a QR code top-right; scanning it opens this share page's URL.
18. The conversation content sits below the QR, fully expanded (no clipped/capped blocks), in the
    platform visual style (warm canvas, stone borders, rounded cards).
19. Export an empty conversation → a valid image (header + QR + empty state), not an error.

### Cross-cutting (FR-023/024, SC-008)
20. Mobile + desktop widths (Playwright): no horizontal overflow from any block, preview, or sandbox.
21. During streaming, an open fenced block renders as source without throwing; preview appears once the
    block settles.

## Expected outcome

All gates green and scenarios 1–21 pass on **both** the in-app conversation and the public share view,
with sandbox isolation verified and PlantUML traffic confirmed same-origin/self-hosted.
