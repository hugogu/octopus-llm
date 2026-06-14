# Feature Specification: Rich Response Rendering, Previews & Share Export

**Feature Branch**: `006-rich-response-rendering`
**Created**: 2026-06-14
**Status**: Draft
**Input**: User description: "006 进行对话页面的功能优化。包括以下几点 1. 不同响应格式的渲染及预揽（尤其是markdown中的mermaid, plantuml, html, svg等）这种需要有两个视图，一个看原始的文本，一个看预揽的效果。2. markdown内部嵌入的```type```需要有高度限制不然把整个markdown都撑大了。每个对话框本身也应该有高度上限，不能无聊长。这些每个```type```需要支持独立的copy功能。3. 对话结果中的用时，in/out量的信息放进详细info里，同时加上cache相关的信息。4. 对于会话中有html/css/js/svg的情况，需要能做整体的展示，像内嵌了浏览器运行这个结果一样。5. 注意以上功能，在share页面也要支持。6. Share页面添加长图生成模式，其中右上角需要有本share页面的二维码，下面才是内容。长图的背景和边框等视觉元素的风格要与UI一致。"

## Clarifications

### Session 2026-06-14

- Q: PlantUML 无法纯前端渲染，渲染应放在哪里？ → A: 自托管/内置渲染器，模型内容不出平台信任边界（不调用第三方公网服务）；渲染器不可用时回退源码视图
- Q: HTML 块默认就渲染预览，会与「不得自动执行活动内容」冲突，默认行为如何定？ → A: HTML 默认显示源码、不自动渲染；仅当用户显式点击「运行」后才在沙箱内呈现/执行（Mermaid/PlantUML/SVG 仍默认预览）
- Q: cache token 各家 provider 字段不同，数据模型如何定？ → A: 归一化为 cache-read + cache-write(creation) 两个字段，按 provider 映射，缺失维度显示「—」；因记录不可变，仅本能力上线后生成的响应才有 cache 数据，历史响应显示「—」
- Q: 可运行沙箱是否允许对外发起网络请求（加载 CDN 脚本/远程资源）？ → A: 允许对外网络请求；隔离边界仍禁止访问宿主凭据/会话/存储与宿主导航

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bounded, copyable content blocks (Priority: P1)

When a model returns a long answer or a large fenced code block, the conversation today grows
unboundedly tall, pushing other models' answers far down and making side-by-side comparison
impractical. A user wants every embedded code/fenced block to be height-capped with its own internal
scroll, every response bubble to be height-capped with a way to expand, and every fenced block to have
its own one-click "copy" control so they can grab exactly one snippet without selecting text by hand.

**Why this priority**: This is the foundational readability fix that makes every other rendering
improvement usable. It delivers immediate standalone value on existing conversations without any new
rendering engine, and it directly addresses the "无聊长" (boringly tall) complaint that breaks the
platform's core side-by-side comparison.

**Independent Test**: Open a conversation containing a very long answer with a large code block.
Confirm the code block stops growing at its height cap and scrolls internally, the response bubble
stops growing at its cap and offers an expand affordance, and clicking the code block's copy control
places exactly that block's raw text on the clipboard with visible confirmation.

**Acceptance Scenarios**:

1. **Given** a response containing a fenced code block taller than the cap, **When** it renders,
   **Then** the block is constrained to a maximum height and scrolls internally instead of expanding
   the whole message.
2. **Given** a response taller than the response-bubble cap, **When** it renders, **Then** the bubble
   is constrained to a maximum height with a clear "expand / show more" affordance, and expanding
   reveals the full content.
3. **Given** any fenced code block, **When** the user activates its copy control, **Then** the exact
   raw text of that block (only that block) is copied and a brief success confirmation is shown.
4. **Given** multiple fenced blocks in one response, **When** the user copies one, **Then** only that
   block is copied and the others are unaffected.
5. **Given** a copy action, **When** the clipboard is unavailable or denied, **Then** the user sees a
   clear failure indication rather than a silent no-op.

---

### User Story 2 - Source / preview dual view for renderable formats (Priority: P1)

Model answers frequently contain diagram and markup formats — Mermaid, PlantUML, SVG, and HTML —
embedded as fenced blocks. Today these render only as raw text, so the user cannot see the actual
diagram or markup result. The user wants each such block to offer two views: a rendered **preview**
(the visual result) and the **source** (raw text), with an obvious toggle between them. Mermaid,
PlantUML, and SVG default to the **preview**; HTML defaults to **source** and only renders/executes
after an explicit user "run" action (handled by US3's sandbox), because HTML can carry active content.

**Why this priority**: Turning diagram/markup source into a visible result is the headline value of
this feature and the most-requested item. It is independently demonstrable on any answer that contains
one of these formats and depends only on US1's block scaffolding.

**Independent Test**: Open a response whose answer contains a Mermaid block, a PlantUML block, an SVG
block, and an HTML block. Confirm each renders a visual preview by default, each exposes a toggle to
switch to raw source and back, and the source view shows the exact original text. Confirm a malformed
block shows a readable error in the preview without breaking the rest of the message.

**Acceptance Scenarios**:

1. **Given** a fenced block tagged as Mermaid, PlantUML, or SVG, **When** it renders, **Then** a
   visual preview of that content is shown by default with a control to switch to source.
1a. **Given** a fenced block tagged as HTML, **When** it renders, **Then** the source is shown by
   default and no markup is rendered/executed until the user explicitly runs it (US3).
2. **Given** a rendered preview, **When** the user toggles to source, **Then** the exact raw text of
   the block is shown, and toggling back restores the preview.
3. **Given** a block whose content fails to render (invalid diagram/markup), **When** preview is
   attempted, **Then** a clear inline error is shown for that block only and the rest of the message
   renders normally.
4. **Given** any renderable block, **When** it is displayed in either view, **Then** the per-block copy
   control (US1) copies the raw source.
5. **Given** a renderable preview taller than the cap, **When** it renders, **Then** it respects the
   block height cap (US1) with internal scroll or a fit-to-width behavior, never overflowing the bubble.

---

### User Story 3 - Runnable web sandbox for HTML/CSS/JS/SVG (Priority: P2)

When an answer contains a self-contained web artifact — HTML with CSS and/or JavaScript, or an SVG —
the user wants to see it run as an integrated whole, as if a small browser were embedded in the
response, rather than reading the markup. This is distinct from US2's static preview: it executes the
combined artifact so interactive results (animations, scripts, styled layouts) actually work.

**Why this priority**: Running a combined web artifact is high value for front-end / visualization use
cases but is a contained slice on top of US2's preview, and it carries stricter isolation requirements,
so it follows the static preview rather than blocking it.

**Independent Test**: Open a response containing an HTML document that uses CSS and JavaScript (e.g. a
small interactive widget). Confirm the user can run it in an embedded, isolated frame and see the live
result, that scripts run only within that isolation boundary, and that the source remains viewable.

**Acceptance Scenarios**:

1. **Given** a response containing a self-contained HTML/CSS/JS artifact, **When** the user opts to run
   it, **Then** the combined artifact executes in an isolated embedded frame and the live result is
   shown.
2. **Given** a runnable artifact, **When** it executes, **Then** its scripts and styles are sandboxed
   and cannot read the user's session, navigate the host app, or affect the rest of the page.
3. **Given** a runnable artifact, **When** it is shown, **Then** the user can still switch to view its
   raw source and copy it (US1/US2).
4. **Given** an SVG artifact, **When** previewed, **Then** it renders as an image-like result with any
   embedded scripting neutralized or sandboxed per the isolation rule.
5. **Given** a runnable artifact, **When** the user has not chosen to run it, **Then** it does not
   auto-execute potentially expensive or active content without a clear user action.

---

### User Story 4 - Response usage & cache detail (Priority: P2)

Each AI response carries operational facts — time taken (latency), input/output token counts, and
cache-related usage (e.g. cached/cache-read and cache-write tokens where the provider reports them).
The user wants this moved out of the main answer flow into a per-response **details / info** affordance
so the conversation stays clean, while the full breakdown — including the new cache fields — is one
click away.

**Why this priority**: Surfacing per-response usage (and adding cache visibility) is an explicitly
requested, independently testable improvement that reuses existing response data and depends only on
the response card existing.

**Independent Test**: Open a response and open its details/info affordance. Confirm it shows time
taken, input token count, output token count, and cache-related token information when the provider
reported it (and a graceful "—"/absent treatment when it did not), and that this detail is not
cluttering the main answer body.

**Acceptance Scenarios**:

1. **Given** a completed response, **When** the user opens its details/info affordance, **Then** they
   see time taken, input tokens, output tokens, and cache-related token figures.
2. **Given** a response whose provider did not report cache (or any) usage fields, **When** the details
   are shown, **Then** the missing figures are shown gracefully (e.g. "—") without breaking the panel.
3. **Given** the main conversation view, **When** a response renders, **Then** usage figures live in the
   details/info affordance and do not dominate the answer body.
4. **Given** an errored response, **When** the user opens its details, **Then** time taken and any
   available usage are still shown alongside the error outcome.

---

### User Story 5 - Full parity on the public share view (Priority: P2)

A conversation shared via a public link must render with the same richness as the in-app view: bounded
copyable blocks, source/preview toggles for Mermaid/PlantUML/SVG/HTML, the runnable web sandbox, and
the per-response usage/cache details. A visitor (including non-registered users) should get the same
reading experience as the owner.

**Why this priority**: The user explicitly requires parity on the share page. It depends on US1–US4
being defined first, and the share view is read-only, so it is a parity slice rather than new
mechanics — but it must enforce the same isolation guarantees for anonymous visitors.

**Independent Test**: Create a share link for a conversation that contains long code, a Mermaid
diagram, a runnable HTML artifact, and responses with usage data. Open the link logged out and confirm
every US1–US4 capability behaves the same as in-app (bounded blocks, copy, preview/source toggle,
sandboxed run, usage/cache details), with sandbox isolation still enforced.

**Acceptance Scenarios**:

1. **Given** a shared conversation opened by any visitor, **When** it renders, **Then** code/fenced
   blocks are bounded and copyable exactly as in the in-app view.
2. **Given** a shared conversation containing renderable formats, **When** it renders, **Then** the
   source/preview toggle and the runnable sandbox behave identically to the in-app view, with the same
   isolation boundary for anonymous visitors.
3. **Given** a shared response, **When** the visitor opens its details/info, **Then** usage and
   cache-related figures are shown (subject to the same privacy boundary as the rest of the share
   view — no owner identity, IP, or connection details).
4. **Given** a shared artifact that runs in the sandbox, **When** it executes, **Then** it cannot reach
   any visitor credential or the host app, the same as in-app.

---

### User Story 6 - Share page long-image (poster) export (Priority: P3)

From a share page, the user wants a "long image" (poster) generation mode that renders the whole shared
conversation as a single tall image suitable for saving and re-sharing. The poster places a QR code for
this share page in the top-right corner, with the conversation content below it. The poster's
background, borders, and other visual elements match the platform's existing UI style.

**Why this priority**: This is a polished, self-contained sharing enhancement that builds on the share
view existing and rendering correctly (US5). It is the most deferrable slice and delivers standalone
value once the rest is in place.

**Independent Test**: Open a share page, trigger long-image mode, and confirm it produces a single tall
image whose top-right contains a scannable QR code that resolves to this share page's URL, whose body
is the conversation content beneath it, and whose background/borders/visual style match the platform UI.

**Acceptance Scenarios**:

1. **Given** a share page, **When** the user triggers long-image mode, **Then** a single tall image of
   the conversation is produced and can be saved/downloaded.
2. **Given** the generated long image, **When** it is inspected, **Then** a QR code is present in the
   top-right corner and scanning it opens this share page's URL.
3. **Given** the generated long image, **When** it is viewed, **Then** the conversation content is laid
   out below the QR code and the background, borders, and visual elements match the platform's design
   system (warm canvas, stone palette, accent color, rounded cards).
4. **Given** a long conversation, **When** the image is generated, **Then** all turns/responses are
   included in reading order without clipping content.
5. **Given** the QR code, **When** the share link is later revoked, **Then** the QR continues to encode
   the same share URL (the image is a point-in-time artifact; revocation behavior of the link itself is
   unchanged by export).

---

### Edge Cases

- **Unlabeled or mislabeled fences**: a block whose language tag is missing or wrong (e.g. SVG pasted as
  plain text) MUST still render readably as code; only correctly-identifiable renderable formats get a
  preview.
- **Streaming / partial content**: while a response is still streaming, a renderable block may be
  incomplete; preview attempts MUST degrade gracefully (show source or a "rendering…/incomplete" state)
  and not throw.
- **Very large diagrams/artifacts**: a preview that would render extremely large MUST be constrained by
  the height cap with scroll/fit behavior, never overflow the layout.
- **Malicious or active HTML/JS**: runnable artifacts MUST be isolated so they cannot exfiltrate the
  viewer's auth token/session, perform top-level navigation of the host app, or access host storage —
  this applies identically to anonymous share-page visitors.
- **PlantUML rendering availability**: if PlantUML rendering depends on a renderer that is unavailable,
  the block MUST fall back to source view with a clear notice, not a broken preview.
- **Copy in insecure contexts**: where clipboard access is unavailable, the copy control MUST indicate
  failure clearly rather than appear to succeed.
- **Missing usage data**: responses lacking latency/token/cache fields MUST show graceful placeholders
  in the details panel and MUST NOT break aggregate rendering. Responses generated before cache capture
  shipped, or from providers that report no cache usage, MUST show "—" for the cache fields.
- **Empty conversation export**: triggering long-image export on a conversation with no responses MUST
  produce a valid image (header + QR + empty-state) rather than an error.
- **Expand state in export**: long-image export MUST include the full content of height-capped blocks
  and bubbles (expanded), not the truncated on-screen state.

## Requirements *(mandatory)*

### Functional Requirements

#### Bounded & Copyable Blocks (US1)

- **FR-001**: Every embedded fenced/code block MUST be constrained to a maximum height and scroll
  internally when its content exceeds that height, rather than expanding the enclosing message.
- **FR-002**: Every response bubble MUST be constrained to a maximum height with a clear "expand / show
  more" affordance that reveals the full content on demand.
- **FR-003**: Every fenced block MUST provide its own copy control that copies only that block's exact
  raw text and shows a brief success confirmation; failure to copy MUST be indicated clearly.

#### Source / Preview Rendering (US2)

- **FR-004**: Fenced blocks identified as Mermaid, PlantUML, or SVG MUST render a visual preview by
  default and expose a control to toggle between preview and raw source. HTML blocks MUST default to
  source view and MUST NOT render/execute until the user explicitly runs them (per FR-008/FR-010);
  they expose the same preview/source toggle once run.
- **FR-005**: The source view MUST show the exact original text of the block, and toggling MUST be
  reversible without losing content.
- **FR-006**: A block that fails to render MUST show a clear inline error scoped to that block only,
  leaving the rest of the message intact and still allowing the user to view its source.
- **FR-006a**: PlantUML rendering MUST be performed within the platform's own trust boundary (a
  self-hosted/bundled renderer); model content MUST NOT be sent to any third-party public rendering
  service. When the renderer is unavailable, the block MUST fall back to source view with a clear
  notice rather than a broken preview.
- **FR-007**: Renderable previews MUST respect the block height cap (FR-001) and MUST NOT overflow the
  response bubble.

#### Runnable Web Sandbox (US3)

- **FR-008**: Self-contained HTML/CSS/JS artifacts MUST be runnable as a combined whole inside an
  isolated embedded frame that shows the live executed result.
- **FR-009**: Runnable artifacts MUST be sandboxed so their scripts/styles cannot read the viewer's
  authentication/session, navigate or alter the host application, or access host storage. The sandbox
  MAY make outbound network requests (so artifacts can load CDN scripts/remote assets), but this MUST
  NOT grant any access to host credentials, session, storage, or host navigation.
- **FR-010**: Active/executable artifacts MUST NOT auto-execute without a clear user action; the user
  retains access to the raw source and copy controls.
- **FR-011**: SVG artifacts MUST render as an image-like preview with any embedded scripting neutralized
  or sandboxed under the same isolation rule.

#### Response Usage & Cache Detail (US4)

- **FR-012**: Each response MUST expose a details/info affordance presenting time taken (latency), input
  token count, output token count, and cache-related token figures captured as a normalized pair —
  **cache-read tokens** and **cache-write (creation) tokens** — mapped per provider where reported,
  with "—" for any dimension a provider does not report.
- **FR-013**: Usage and cache figures MUST live in the details/info affordance rather than the main
  answer body, keeping the conversation flow uncluttered.
- **FR-014**: Missing usage/cache fields MUST be shown gracefully (e.g. "—") without breaking the panel,
  for both successful and errored responses.
- **FR-015**: Cache-related usage MUST be captured (normalized cache-read / cache-write fields) from the
  provider's reported usage on each new response so it is available for display wherever reported.
  Because response records are immutable (Constitution IV), cache figures exist only for responses
  generated after this capability ships; pre-existing responses MUST display "—" rather than being
  backfilled.

#### Share View Parity (US5)

- **FR-016**: The public share view MUST provide the same bounded/copyable blocks, source/preview
  toggles, runnable sandbox, and usage/cache details as the in-app conversation view.
- **FR-017**: Sandbox isolation (FR-009) MUST be enforced identically for anonymous share-page
  visitors.
- **FR-018**: Usage/cache details on the share view MUST remain within the share view's existing privacy
  boundary — no owner identity, IP address, or connection details exposed.

#### Long-Image Export (US6)

- **FR-019**: The share page MUST offer a long-image (poster) export mode that renders the entire shared
  conversation as a single tall, saveable image.
- **FR-020**: The exported image MUST place a QR code in the top-right corner that encodes this share
  page's URL, with the conversation content laid out below it in reading order.
- **FR-021**: The exported image's background, borders, and visual elements MUST match the platform's
  design system (warm canvas, stone palette, accent color, rounded cards).
- **FR-022**: The export MUST include the full content of height-capped blocks and bubbles (expanded
  state), and MUST handle empty conversations with a valid image rather than an error.

#### Cross-cutting

- **FR-023**: All new and modified user-facing surfaces (in-app conversation view and public share view)
  MUST follow the four UX principles (consistent, fluent, responsive, connected) and be visually
  verified before completion.
- **FR-024**: Rendering and preview behavior MUST degrade gracefully during streaming and on
  malformed/partial content, never throwing an error that breaks the surrounding message.

### Key Entities *(include if feature involves data)*

- **Renderable Block**: A fenced segment within a response identified by a format tag (Mermaid,
  PlantUML, SVG, HTML, or generic code). Carries its raw source text, a derived view mode
  (preview/source), a render outcome (ok/error), and per-block copy capability. Not persisted
  separately — derived from the existing immutable response content at render time.
- **Runnable Artifact**: A self-contained HTML/CSS/JS (or SVG) unit executed in an isolated frame.
  Defined by its combined source and an explicit run state; carries no host privileges.
- **Response Usage Detail**: The per-response operational figures surfaced in the details affordance:
  latency, input tokens, output tokens, and normalized cache-read / cache-write token counts. Derived
  from the existing immutable response/statistics record, extended with the two cache fields populated
  at capture time from the provider's usage; not a new source of truth.
- **Share Poster**: A point-in-time, exportable tall image of a shared conversation, composed of a
  QR code encoding the share URL plus the rendered conversation content in the platform visual style.
  An artifact, not stored server-side.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a conversation with a 500-line code block, the response bubble and the code block each
  stay within their height cap (verified: neither exceeds its defined maximum) and scroll/expand
  instead, in 100% of cases.
- **SC-002**: A user can copy any single fenced block with one action and get exactly that block's text
  (0 stray content from other blocks) with visible confirmation.
- **SC-003**: For a response containing Mermaid, PlantUML, SVG, and HTML blocks, the Mermaid/PlantUML/SVG
  blocks show a visual preview by default while the HTML block shows source until explicitly run; each
  can be toggled to exact source and back, with a malformed block degrading to a scoped error in under
  1 attempt (0 whole-message breakage).
- **SC-004**: A runnable HTML/CSS/JS artifact executes in isolation and, in a security check, cannot
  read the viewer's auth token, navigate the host app, or access host storage (0 escapes).
- **SC-005**: Every completed response's details affordance shows latency, input tokens, output tokens,
  and cache figures where reported, and graceful placeholders otherwise (0 panel breakages on missing
  data).
- **SC-006**: Every US1–US4 capability verified in-app is reproduced on the public share view for a
  logged-out visitor (100% parity in acceptance testing) with sandbox isolation intact.
- **SC-007**: Long-image export of a multi-turn shared conversation produces one tall image whose
  top-right QR code scans to the correct share URL and whose full content (expanded) appears below,
  in the platform visual style, with 0 clipped responses.
- **SC-008**: The conversation page remains visually verified (browser/Playwright) on mobile and desktop
  widths with no horizontal overflow from any rendered block, preview, or sandbox.

## Assumptions

- **Reuse over rebuild**: The existing Markdown rendering pipeline (the shared renderer used by both the
  in-app conversation and the share view) is extended in place rather than replaced, so parity (US5)
  comes from a single shared rendering path used by both surfaces.
- **Renderable format set**: "不同响应格式" is scoped to the explicitly named formats — Mermaid,
  PlantUML, SVG, and HTML — plus generic code blocks (bounded + copyable). Other formats remain
  rendered as bounded, copyable code without a visual preview.
- **PlantUML rendering**: PlantUML requires a rendering step beyond pure in-browser parsing. It is
  rendered by a self-hosted/bundled renderer inside the platform's trust boundary (never a third-party
  public service), so model content does not leave the platform (Constitution VI). Where the renderer
  is unavailable, the block falls back to source view with a notice rather than failing.
- **Sandbox model**: "像内嵌了浏览器" is interpreted as an isolated embedded frame (sandboxed, no host
  privileges), not a full browser engine; explicit user action is required before active content runs.
  The frame may reach the network (to load CDN/remote assets) while remaining unable to access host
  credentials, session, storage, or perform host navigation.
- **Cache fields source**: Cache-related token figures are sourced from the provider's reported usage on
  the existing immutable response record; the record/statistics structure is extended with two
  normalized fields (cache-read, cache-write/creation) mapped per provider (e.g. Anthropic's
  `cache_read_input_tokens` / `cache_creation_input_tokens`; OpenAI's `cached_tokens` → cache-read).
  No new parallel source of truth is introduced (Constitution IV), and immutability means only
  responses generated after this ships carry cache data — historical responses display "—".
- **Long-image generation**: The poster is generated as a point-in-time client-side artifact of the
  current share page; it is downloaded by the user and not persisted server-side. The QR encodes the
  current share URL and is unaffected by later revocation of the link.
- **Height caps**: Specific pixel/line thresholds for block and bubble caps are reasonable defaults
  chosen during implementation to balance readability and density; the requirement is that caps exist
  with internal scroll/expand, not a specific numeric value.

## Out of Scope

- Editing, re-running, or persisting model artifacts (the sandbox runs read-only model output; it is
  not an authoring/playground tool).
- Server-side image hosting or shareable image URLs for the long-image export (it is a local download).
- Rendering format types beyond the named set (Mermaid, PlantUML, SVG, HTML) with bespoke previews.
- Changes to how likes, sharing tokens, analytics aggregation, or session immutability work (006 builds
  on 005's surfaces; it does not redefine them).
- Real-time collaborative or interactive editing of conversations.
