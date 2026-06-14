---
description: "Task list for Rich Response Rendering, Previews & Share Export"
---

# Tasks: Rich Response Rendering, Previews & Share Export

**Input**: Design documents from `specs/006-rich-response-rendering/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included only where the constitution requires them — the new `/api/v2/render/plantuml`
endpoint (integration test, happy path), backend cache capture, and security-critical frontend
behavior (sandbox isolation, per-block copy). Not full TDD for every UI tweak.

**Organization**: Grouped by user story (US1–US6) for independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- Paths follow the plan: `backend/src/main/kotlin/com/octopusllm/...`, `frontend/src/...`

## Path Conventions

Web app: Kotlin backend under `backend/src/`, Next.js frontend under `frontend/src/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencies and the self-hosted PlantUML service the feature relies on.

- [X] T001 [P] Add frontend deps `mermaid`, `qrcode`, `html-to-image`, `dompurify` (+ `@types/qrcode`, `@types/dompurify` if needed) to `frontend/package.json`; install
- [X] T002 [P] Add an internal `plantuml` service (`plantuml/plantuml-server:<pinned-tag>`, **no published host port**, on the compose network) to `docker-compose.yml`, and add `PLANTUML_SERVER_URL=http://plantuml:8080` to the backend service env
- [X] T003 [P] Add backend config for `PLANTUML_SERVER_URL` and a render request size cap (e.g. 100 KB) in `backend/src/main/resources/application*.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared block-routing scaffold every rendering story builds on. The single shared
markdown pipeline means this work lands once and benefits both the in-app and share views.

**⚠️ CRITICAL**: Blocks US1, US2, US3.

- [X] T004 Create fence classifier in `frontend/src/lib/markdown/blocks.ts` mapping a fence language → strategy (`code` | `diagram-preview` | `html-runnable`) per data-model.md
- [X] T005 Refactor `frontend/src/components/chat/markdownComponents.tsx` `code()` to delegate fenced blocks to a strategy dispatcher with a **default passthrough that preserves current rendering** (keep `MarkdownRenderer.test.tsx` green)
- [X] T006 [P] Improve `frontend/src/components/ui/CopyButton.tsx` to surface a visible failure state when `navigator.clipboard` is unavailable/denied (FR-003), replacing the silent `console.error`

**Checkpoint**: Block dispatcher in place; existing rendering unchanged.

---

## Phase 3: User Story 1 - Bounded, copyable content blocks (Priority: P1) 🎯 MVP

**Goal**: Every fenced block is height-capped + independently copyable; response bubble cap reused.

**Independent Test**: A 400-line code block caps with internal scroll, the bubble caps with "Show
more", and each block's copy control copies exactly that block's text with confirmation.

- [X] T007 [P] [US1] Create `frontend/src/components/chat/CodeBlock.tsx`: max-height scroll region + language label + per-block `CopyButton` fed the block's exact raw source (FR-001/FR-003)
- [X] T008 [US1] Wire the `code` strategy in the `markdownComponents.tsx` dispatcher to `CodeBlock` (replaces the inline `SyntaxHighlighter` path)
- [X] T009 [P] [US1] Vitest in `frontend/src/components/chat/CodeBlock.test.tsx`: height cap applied; per-block copy copies only that block's raw text; copy-failure surfaces

**Note**: In-app bubble cap (FR-002) already exists via `ExpandableContent` in `ModelResponsePanel`; share-side reuse is covered in US5 (parity).

**Checkpoint**: US1 fully functional in-app and independently testable.

---

## Phase 4: User Story 2 - Source / preview dual view (Priority: P1)

**Goal**: Mermaid/PlantUML/SVG preview by default with preview⇄source toggle; HTML defaults to source
(Q2). PlantUML rendered self-hosted via same-origin proxy (Q1).

**Independent Test**: A response with Mermaid, PlantUML, SVG, and HTML blocks shows previews (HTML =
source) by default, each toggles to exact source, and a malformed block degrades to a scoped error.

### Backend — self-hosted PlantUML proxy

- [X] T010 [P] [US2] Create `backend/src/main/kotlin/com/octopusllm/render/PlantUmlRenderService.kt`: `WebClient` → `PLANTUML_SERVER_URL`, enforce size cap, return SVG; calls only the configured URL (no SSRF)
- [X] T011 [US2] Create `backend/src/main/kotlin/com/octopusllm/render/PlantUmlRenderController.kt`: `POST /api/v2/render/plantuml` (accepts `text/plain` and `{source}`), unauthenticated/public-safe, errors per `contracts/plantuml-render.md` (400/413/502)
- [X] T012 [US2] Integration test (MockWebServer/Testcontainers) for `/api/v2/render/plantuml`: happy-path SVG + `413` over cap + `502` renderer-unavailable (constitution: new endpoint happy-path test)
- [X] T013 [P] [US2] Add `frontend/src/lib/api/render.ts` (`renderPlantUml(source)`), and verify the Next proxy route forwards `/api/v2/render/*` upstream intact

### Frontend — previews + toggle

- [X] T014 [P] [US2] Create `frontend/src/components/chat/BlockViewToggle.tsx` (preview⇄source segmented control wrapper, design-system styled)
- [X] T015 [P] [US2] Create `frontend/src/components/chat/MermaidPreview.tsx`: dynamic `import('mermaid')`, render settled blocks only, try/catch → scoped error (FR-006/R10)
- [X] T016 [P] [US2] Create `frontend/src/components/chat/PlantUmlPreview.tsx`: call `render.ts`; on failure fall back to source view with a notice (FR-006a)
- [X] T017 [P] [US2] Create `frontend/src/components/chat/SvgPreview.tsx`: DOMPurify-sanitized inline SVG (svg profile, scripts stripped)
- [X] T018 [US2] Wire `diagram-preview` (preview default) and `html-runnable` (**source default**, Q2) strategies into the `markdownComponents.tsx` dispatcher via `BlockViewToggle`
- [X] T019 [P] [US2] Vitest in `frontend/src/components/chat/BlockPreview.test.tsx`: toggle preview↔source; HTML defaults to source; malformed Mermaid → scoped error, rest of message intact

**Checkpoint**: US1 + US2 work; PlantUML traffic is same-origin/self-hosted.

---

## Phase 5: User Story 3 - Runnable web sandbox (Priority: P2)

**Goal**: Self-contained HTML/CSS/JS (and SVG) runs in an isolated frame; explicit run; network allowed
but no host-credential access (Q4).

**Independent Test**: Clicking Run executes the artifact in an `<iframe>` whose `sandbox` lacks
`allow-same-origin`; it cannot read the token/storage or navigate the host; SVG script is neutralized.

- [X] T020 [P] [US3] Create `frontend/src/components/chat/RunnableArtifact.tsx`: `<iframe srcdoc>` with `sandbox="allow-scripts allow-popups allow-forms allow-modals"` (**no `allow-same-origin`**); mounts only after explicit Run (FR-008/009/010)
- [X] T021 [US3] Wire the `html-runnable` Run action in the dispatcher to `RunnableArtifact`; route SVG run/preview through `SvgPreview` (FR-011)
- [X] T022 [P] [US3] Playwright in `frontend/tests/` : iframe `sandbox` omits `allow-same-origin`; no auto-run before click; artifact cannot reach `localStorage`/token; SVG `<script>` does not execute (SC-004)

**Checkpoint**: US1–US3 work in-app.

---

## Phase 6: User Story 4 - Response usage & cache detail (Priority: P2)

**Goal**: Per-response details affordance with latency, in/out tokens, and normalized cache-read/
cache-write (Q3). Cache capture is new and forward-only.

**Independent Test**: A response's details affordance shows latency, in/out, cache-read, cache-write; a
new cache-capable response shows real cache numbers; older responses show "—".

### Backend — cache capture (immutable write path)

- [X] T023 [US4] Create migration `backend/src/main/resources/db/migration/V029__provider_response_cache_tokens.sql`: add nullable `cache_read_tokens`, `cache_write_tokens` (+ `>= 0` CHECKs), no backfill
- [X] T024 [US4] Add `cacheReadTokens`/`cacheWriteTokens` columns to `backend/src/main/kotlin/com/octopusllm/chat/ProviderResponse.kt`
- [X] T025 [US4] Add `cacheReadTokens`/`cacheWriteTokens` to `LlmStreamEvent.ModelComplete` in `backend/src/main/kotlin/com/octopusllm/llm/LlmStreamEvent.kt`
- [X] T026 [P] [US4] Parse cache usage in `backend/src/main/kotlin/com/octopusllm/llm/adapter/AnthropicAdapter.kt` (`cache_read_input_tokens` → read, `cache_creation_input_tokens` → write)
- [X] T027 [P] [US4] Parse cache usage in `backend/src/main/kotlin/com/octopusllm/llm/adapter/OpenAiCompatAdapter.kt` (`usage.prompt_tokens_details.cached_tokens` → read; write = null)
- [X] T028 [P] [US4] Map cache usage in `backend/src/main/kotlin/com/octopusllm/llm/adapter/MiniMaxAdapter.kt` if reported, else nulls
- [X] T029 [US4] Persist cache tokens into the `provider_responses` INSERT in `backend/src/main/kotlin/com/octopusllm/chat/ChatService.kt` (depends on T023–T028)
- [X] T030 [US4] Surface cache fields in `ProviderResponseV2` and the `model_complete` SSE event in `backend/src/main/kotlin/com/octopusllm/chat/ChatControllerV2.kt`
- [X] T031 [P] [US4] Backend test (MockK + slice): Anthropic cache usage parsed → persisted → surfaced in `ProviderResponseV2`/SSE; nulls handled gracefully

### Frontend — details affordance

- [X] T032 [P] [US4] Add `cacheReadTokens`/`cacheWriteTokens` (`number | null`) to `ProviderResponseV2` and the `model_complete` SSE type in `frontend/src/lib/types/api.ts`
- [X] T033 [P] [US4] Create `frontend/src/components/chat/ResponseDetails.tsx`: popover with latency, in/out, cache-read, cache-write; render "—" for null (FR-012/014)
- [X] T034 [US4] In `frontend/src/components/chat/ModelResponsePanel.tsx`, replace the inline usage span with the `ResponseDetails` Info affordance and thread cache fields from SSE/session (FR-013)
- [X] T035 [P] [US4] Vitest in `frontend/src/components/chat/ResponseDetails.test.tsx`: shows all figures; "—" placeholders for missing/cache-less responses

**Checkpoint**: US4 works in-app; cache figures end-to-end.

---

## Phase 7: User Story 5 - Full parity on the public share view (Priority: P2)

**Goal**: All US1–US4 behavior on the public share page for anonymous visitors, isolation + privacy
boundary intact.

**Independent Test**: A logged-out visitor on a share link gets bounded/copyable blocks, preview/source
toggles, sandboxed run, and usage/cache details; the payload exposes no owner identity/IP/connection.

- [X] T036 [US5] Add `cacheReadTokens`/`cacheWriteTokens` to the shared response DTO in `backend/src/main/kotlin/com/octopusllm/share/ShareService.kt` (usage only — no identity, FR-018)
- [X] T037 [P] [US5] Add cache fields to the `SharedResponse` type in `frontend/src/lib/types/api.ts`
- [X] T038 [US5] In `frontend/src/components/share/SharedConversation.tsx`, wrap each response body in `ExpandableContent` (bubble cap parity) and add `ResponseDetails`; rely on the shared `MarkdownRenderer` so `CodeBlock`/previews/runnable apply automatically (FR-016)
- [X] T039 [P] [US5] Backend test: `GET /api/v2/shared/{token}` payload includes cache fields and excludes owner id/IP/connection (FR-018)
- [X] T040 [P] [US5] Playwright: share page (logged out) reproduces bounded/copyable blocks, preview/source toggle, sandboxed run (no `allow-same-origin`), and details (SC-006)

**Checkpoint**: Parity verified; US1–US5 work on both surfaces.

---

## Phase 8: User Story 6 - Share long-image export (Priority: P3)

**Goal**: Client-side poster of the shared conversation with a QR (share URL) top-right, content below,
in the platform visual style.

**Independent Test**: Export yields one tall PNG with a top-right QR that scans to the share URL and the
full (expanded) conversation below, in the design-system style.

- [X] T041 [P] [US6] Create `frontend/src/components/share/ShareExportButton.tsx`: build an off-screen poster node (QR top-right via `qrcode` of `window.location`; full conversation below, force-expanded; warm canvas/stone/rounded-card styling) (FR-020/021/022)
- [X] T042 [US6] Rasterize with `html-to-image` `toPng` and trigger download; handle empty conversation → valid image (header + QR + empty state) (FR-019/022)
- [X] T043 [US6] Add the export action to the `SharedConversation.tsx` header (connected, design-system button)
- [X] T044 [P] [US6] Playwright: export downloads a PNG; QR present top-right and encodes the share URL; content below, no clipped responses (SC-007)

**Checkpoint**: All user stories functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T045 [P] Gates green: `cd backend && ./gradlew build`; `cd frontend && npx tsc --noEmit`; `npx vitest run`
- [ ] T046 Playwright responsive pass (mobile + desktop): no horizontal overflow from any block/preview/sandbox (SC-008); streaming open-fence renders as source without throwing (FR-024)
- [X] T047 [P] Run `quickstart.md` scenarios 1–21 on both the in-app conversation and the share view
- [X] T048 Security/self-host verification: confirm PlantUML traffic is same-origin (never plantuml.com) and the runnable iframe cannot reach host credentials/storage

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (P1)**: no dependencies.
- **Foundational (P2)**: depends on Setup; **blocks US1, US2, US3**.
- **US1 (P3)**: after Foundational. MVP.
- **US2 (P4)**: after Foundational; PlantUML backend (T010–T013) is independent of US1 and can run in parallel with US1.
- **US3 (P5)**: after US2 (reuses the diagram dispatcher + `SvgPreview`).
- **US4 (P6)**: independent of US1–US3 (backend cache pipeline + details popover); can run in parallel once Setup is done.
- **US5 (P7)**: after US1–US4 (reuses the shared pipeline + `ResponseDetails`).
- **US6 (P8)**: after US5 (needs the share view rendering correctly).
- **Polish (P9)**: after all targeted stories.

### Within Each User Story

- Backend: migration → entity → stream event → adapters → service → controller/DTO.
- Frontend: leaf components ([P]) → dispatcher wiring (sequential, same file) → tests.
- Dispatcher edits in `markdownComponents.tsx` (T005, T008, T018, T021) are **sequential** (same file).

### Parallel Opportunities

- Setup T001–T003 all [P].
- US2 backend (T010–T013) ∥ US1 (T007–T009).
- US4 adapters T026/T027/T028 all [P]; backend (US4) ∥ frontend rendering stories.
- Leaf preview components T014–T017 all [P] before the single dispatcher wiring T018.

---

## Parallel Example: User Story 2

```bash
# Backend PlantUML proxy and frontend leaf previews in parallel:
Task: T010 PlantUmlRenderService.kt
Task: T014 BlockViewToggle.tsx
Task: T015 MermaidPreview.tsx
Task: T016 PlantUmlPreview.tsx
Task: T017 SvgPreview.tsx
# Then (sequential, same file): T018 wire strategies into markdownComponents.tsx
```

---

## Implementation Strategy

### MVP First (US1)

1. Setup → Foundational → US1. **STOP and validate**: bounded + copyable blocks in-app. Demo.

### Incremental Delivery

1. Foundation ready (Setup + Foundational).
2. US1 (bounded/copyable) → demo (MVP).
3. US2 (previews + self-hosted PlantUML) → demo.
4. US3 (runnable sandbox) → demo.
5. US4 (usage + cache) — can land in parallel with US2/US3.
6. US5 (share parity) → demo on share page.
7. US6 (long-image export) → demo.

### Parallel Team Strategy

After Foundational: Dev A → US1+US2 (rendering), Dev B → US4 (backend cache + details), converge on
US5 parity, then US6.

---

## Notes

- [P] = different files, no incomplete dependency.
- Cache capture is forward-only (Constitution IV): no UPDATE/backfill; historical rows render "—".
- Sandbox isolation (no `allow-same-origin`) and self-hosted PlantUML are security-critical — verify in
  T022/T048 before marking done.
- Commit after each task or logical group; visually verify every user-facing surface (Constitution VIII)
  before a story is "done".
