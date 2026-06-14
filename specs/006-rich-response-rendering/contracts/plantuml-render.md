# Contract: PlantUML Render Proxy

Self-hosted PlantUML rendering behind a same-origin backend proxy (resolves clarification Q1). The
browser never contacts the PlantUML server directly; the backend forwards to the internal service.

## `POST /api/v2/render/plantuml`

Render PlantUML source to SVG.

**Auth**: None required. The endpoint must serve **anonymous share-page visitors** (FR-016/FR-017), so
it is unauthenticated but hardened (size limit; talks only to the configured internal URL).

**Request**

- `Content-Type: text/plain` (raw PlantUML source) — or `application/json` `{ "source": "..." }`.
- Body size limit: reject sources larger than a configured cap (e.g. 100 KB) with `413`.

```
@startuml
Alice -> Bob: hello
@enduml
```

**Response**

- `200 OK`, `Content-Type: image/svg+xml` — the rendered diagram SVG.
- The frontend sanitizes the returned SVG (DOMPurify) before inlining.

**Errors** (standard error schema `{ "code", "message", "details" }`):

| Status | Code | When |
|--------|------|------|
| `400` | `invalid_plantuml` | Source missing/empty or the renderer rejects it as malformed. |
| `413` | `payload_too_large` | Source exceeds the configured size cap. |
| `502`/`503` | `renderer_unavailable` | Internal PlantUML server unreachable. Frontend falls back to source view with a notice (FR-006a). |

**Behavior / constraints**

- Backend `PlantUmlRenderService` calls only `PLANTUML_SERVER_URL` (configured), never a client-supplied
  URL — no SSRF surface.
- Stateless; safe to scale horizontally; no DB, no locks.
- Idempotent: same source → same SVG.
- Contains no personal data; safe for the public share context.

**Infra**: `docker-compose.yml` adds an internal `plantuml` service
(`plantuml/plantuml-server:<pinned-tag>`) with **no published host port**; backend reaches it on the
compose network. Backend env: `PLANTUML_SERVER_URL=http://plantuml:8080`.

## Frontend client (`lib/api/render.ts`)

`renderPlantUml(source: string): Promise<string>` — POSTs to the same-origin `/api/v2/render/plantuml`
proxy route, returns the SVG string (or throws → caller shows source-fallback notice). Used identically
by the in-app conversation and the public share view.
