# Implementation Plan: Multimedia Support (Images, Video, Voice)

**Branch**: `007-multimedia-support` | **Date**: 2026-06-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/007-multimedia-support/spec.md`

## Summary

Turn the chat from "image-only base64" into a real multimedia experience across compose, history,
and share, plus operator-configurable storage. Five connected capabilities:

1. **Storage-backed media** — user uploads (image/video/audio) are stored in a configurable backend
   (local filesystem or S3/OSS-compatible) and referenced by **public, unguessable opaque URLs**
   (Q2), replacing today's inline base64 in `chat_turns.attachments`. A new uniform upload endpoint
   returns the stored media descriptor; the client never carries protocol-specific upload logic
   (FR-001/005/018/019/022).
2. **Capability-gated multimodal send** — per-configured-model media-capability flags (image/video/
   audio), seeded from protocol-provided defaults and admin-overridable (Q1), gate the selection.
   Models that cannot accept an attached type are **auto-excluded with a per-model notice**, reusing
   the existing `capabilityNotice`/SSE `notice` channel; if no selected model can accept it, the send
   is blocked (FR-002/003/004). Adapters fetch stored media and adapt it per protocol; media is sent
   **only on the turn it was attached to** (Q3) — later turns send text only (FR-006).
3. **Attachment tray** — multi-file preview (image thumb, video poster, audio playback), delete, and
   drag-reorder, with per-type size limits (1 MB image / 10 MB video defaults) and a per-prompt
   ceiling of ≤5 files / ≤15 MB enforced at attach time before upload (FR-007–011, FR-025).
4. **History & share playback** — stored media renders inline in conversation history and the public
   share view (images render, video/audio play), in user-arranged order, with full parity; revoking a
   share makes its media inaccessible together with the rest (FR-012–015, FR-024). This requires
   surfacing attachments through `TurnDtoV2` and the shared-conversation DTO (currently omitted).
5. **Voice input + admin config** — in-chat voice recording added to the tray as an audio item and
   sent as an audio file under the same gating (FR-016/017); an admin storage-settings page selects
   backend + credentials and per-type size limits, validated before apply, with orphaned-upload
   cleanup (FR-018/020/021/023).
6. **Easy + auto-detected capability (US6)** — model media capability is set via one-click
   image/video/audio toggles (raw JSON only for advanced keys) and auto-detected from the curated
   `ModelCatalogue` on add / bulk "Load models", with a fill-only "detect capabilities" backfill for
   existing models. Without this the whole feature is hidden behind hand-written capability JSON
   (FR-026/027/028/029).

Technical approach: introduce a `media` backend package with a `MediaStorage` strategy interface
(`LocalMediaStorage`, `S3MediaStorage`) behind one upload controller, plus a platform-level
`StorageSettings` (new singleton-row table, admin-managed) for backend selection and size limits.
Attachments become **stored-media references** (`mediaId` + public `url` + `mediaType` + `mimeType` +
`sizeBytes` + `order`), persisted in the existing immutable `chat_turns.attachments` jsonb (shape
change, forward-only) and adapted per protocol by extending the existing `Attachment` contract and
the three adapters (image already done; add video/audio + fetch-from-URL). Frontend extends the
existing `ChatInput` attachment code into a real tray (reorder + size limits + voice + audio/video
preview) and extends the shared markdown/turn rendering to play media in history and share. One new
S3 client dependency (AWS SDK v2 or MinIO) on the backend; `MediaRecorder` (browser-native) for
voice — no new frontend dependency required for recording.

## Technical Context

**Language/Version**: Kotlin on JVM, Java 21 (backend); TypeScript 5 / Node.js 24 (frontend)
**Primary Dependencies**: Spring Boot WebFlux, Spring Data JPA/Hibernate, Flyway (backend) + NEW
AWS SDK for Java v2 `s3` (S3/OSS-compatible client, path-style endpoint); Next.js App Router,
react-markdown (existing) + browser-native `MediaRecorder`/`getUserMedia` for voice (no new dep)
**Storage**: PostgreSQL — new `storage_settings` singleton table (backend + size limits); shape of
existing immutable `chat_turns.attachments` jsonb changes from inline base64 to media references
(forward-only). Media bytes live in local filesystem or S3/OSS, NOT in Postgres.
**Testing**: JUnit 5 + Testcontainers + MockK (backend, incl. MinIO Testcontainer for S3 path);
Vitest + Testing Library, Playwright (frontend)
**Target Platform**: Linux server (Docker Compose); modern browsers
**Project Type**: Web application (Kotlin backend + Next.js frontend)
**Performance Goals**: Upload validated and rejected client-side before any bytes leave the browser
for oversize/over-ceiling cases; no regression to concurrent streaming dispatch (Constitution III);
local-stored media served directly by the frontend/static layer with no per-file authenticated
backend round-trip (FR-019, SC-006)
**Constraints**: `chat_turns`/`provider_responses` stay INSERT-once/immutable (Constitution IV) —
attachment jsonb shape is forward-only, no UPDATE/backfill; media URLs MUST be opaque/non-enumerable
(Constitution VI, FR-022); uploaded-but-unsent media cleaned up (FR-023); S3 config validated before
persist (FR-021); admin storage page must reuse the design system and be reachable in-app
(Constitution VIII); snake_case schema via Flyway only
**Scale/Scope**: 2 Flyway migrations (storage_settings; optional attachments-shape comment), ~6 new
backend files (`media` package: storage interface + local + S3 impls, upload controller, media
service, storage-settings admin controller/service) + edits to 3 adapters, `Attachment`/`LlmRequest`,
ChatService, ChatControllerV2 DTOs, ShareService DTO; ~6 new/edited frontend components (attachment
tray, voice recorder, media preview, history/share media rendering, admin storage settings page),
1 new backend dep

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance |
|-----------|-----------|
| I. Provider-Agnostic Abstraction | ✅ Per-protocol media adaptation stays **inside each adapter**; only the uniform `Attachment` (mediaType/url/mimeType) crosses into shared orchestration. Capability gating reads the existing `CapabilityMatrix` (per-model flags) — no provider branches in core/UI. New providers add media support by extending only their adapter. |
| II. API-First Design | ✅ Upload exposed as a versioned `POST /api/v2/media`; storage settings as `/api/v2/admin/storage-settings`; attachments surfaced through existing chat/share DTOs. Frontend consumes these REST endpoints — no direct DB/back-channel. Local-served media is a static public asset, not a privileged path. |
| III. Concurrent Execution & Streaming | ✅ Upload happens before submit; the parallel per-model dispatch and SSE streaming are unchanged. Auto-exclusion rides the existing per-model `notice` event — no added serialization, no locks. |
| IV. Data Integrity & Immutable Sessions | ✅ `chat_turns` stays INSERT-once; attachment jsonb shape change is forward-only (historical rows keep their old shape, rendered best-effort). Media bytes are immutable once a turn is saved; storage_settings is the only mutable, admin-owned config (not session data). All DDL via Flyway, snake_case. |
| V. Observability & Analytics | ✅ Uploads and per-model exclusions emit structured logs (mediaType, sizeBytes, backend, excluded model + reason). No media bytes or personal data added to anonymous aggregates. |
| VI. Security & User Key Privacy | ✅ Media URLs are opaque/non-enumerable (Q2, FR-022); no API-key handling touched. S3 credentials encrypted at rest like other secrets and never returned in responses/logs (FR-021). Share view keeps zero identity; revoking a share revokes its media (FR-015/024). Public-by-default is the explicit product decision, bounded by unguessable paths. Upload validates actual detected content type to prevent type spoofing. |
| VII. Simplicity & Horizontal Scalability | ✅ One `MediaStorage` strategy interface with two impls — the minimum to honor "local or S3/OSS." Local backend writes to a shared bind-mounted directory served statically (stateless app, no locks); S3 backend is inherently shared. No distributed locks; orphan cleanup is an idempotent scheduled sweep. Reuses the existing `Attachment`/adapter path rather than a new pipeline. |
| VIII. UX Consistency & Visual Coherence | ✅ Attachment tray, voice control, and the admin storage page reuse the design system (stone palette, `#c96442` accent, `rounded-2xl` cards, eyebrow+title header), match the Models settings page, and are reachable via admin nav. Loading/disabled/error states on upload and save. Both in-app and share surfaces visually verified (Playwright) before done. |

**Result**: PASS — no violations. The `MediaStorage` strategy interface is the one new abstraction; it
is justified by the explicit "local or S3/OSS" requirement and is stateless/lock-free, so Complexity
Tracking is not required.

## Project Structure

### Documentation (this feature)

```text
specs/007-multimedia-support/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── media-upload.md        # POST /api/v2/media, DELETE /api/v2/media/{id}
│   ├── storage-settings.md    # GET/PUT /api/v2/admin/storage-settings
│   └── chat-media.md          # attachment shape on submit + TurnDtoV2 + shared DTO
├── checklists/
│   └── requirements.md  # Spec quality checklist (already present)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
backend/src/main/kotlin/com/octopusllm/
├── media/                           # NEW: storage + upload
│   ├── MediaStorage.kt              # strategy interface: store(bytes, mime) -> StoredMedia(url, key); delete(key); exists
│   ├── LocalMediaStorage.kt         # writes under MEDIA_LOCAL_DIR; returns public base-URL + opaque key path
│   ├── S3MediaStorage.kt            # AWS SDK v2 S3 client (path-style; OSS/MinIO compatible)
│   ├── MediaStorageFactory.kt       # resolves active impl from StorageSettings
│   ├── MediaService.kt              # validate (detected type, size, ceiling), opaque id, persist Media row, orphan sweep
│   ├── MediaController.kt           # POST /api/v2/media (multipart), DELETE /api/v2/media/{id}
│   ├── Media.kt                     # @Entity media (id, owner, type, mime, size, storage_key, public_url, turn_id?, created_at)
│   └── MediaRepository.kt
├── admin/
│   ├── StorageSettings.kt           # @Entity single-row platform config (backend, s3 fields, size limits)
│   ├── StorageSettingsService.kt    # read/update + validate S3 connectivity before apply (FR-021)
│   └── StorageSettingsController.kt # GET/PUT /api/v2/admin/storage-settings (admin-only)
├── llm/
│   ├── LlmRequest.kt                # EXTEND: Attachment gains url + mediaType (image|video|audio); keep base64 fallback
│   └── adapter/
│       ├── AnthropicAdapter.kt      # EXTEND: video/audio blocks; fetch-from-URL when url present
│       ├── OpenAiCompatAdapter.kt   # EXTEND: video/audio per OpenAI multimodal content parts
│       └── MiniMaxAdapter.kt        # EXTEND: image/video/audio per MiniMax content schema (currently none)
├── chat/
│   ├── ChatService.kt               # EXTEND: build Attachment from media refs; gate+exclude incapable models; media only on attached turn
│   └── ChatControllerV2.kt          # EXTEND: SubmitTurnRequestV2 media refs; TurnDtoV2 exposes attachments
└── share/
    └── ShareService.kt              # EXTEND: shared turn DTO carries media refs (no identity)

backend/src/main/resources/db/migration/
├── V030__storage_settings.sql       # platform storage config + size limits (single row, snake_case)
└── V031__media.sql                  # media table (opaque id, storage_key, public_url, owner, turn_id nullable)

frontend/src/
├── components/chat/
│   ├── ChatInput.tsx                # EXTEND: delegate to AttachmentTray; size+ceiling checks; voice button
│   ├── AttachmentTray.tsx           # NEW: previews (img/video/audio), remove, drag-reorder, limit messaging
│   ├── VoiceRecorder.tsx            # NEW: MediaRecorder capture → audio attachment
│   ├── MediaItem.tsx                # NEW: render/play one media item (used by tray, history, share)
│   └── markdownComponents.tsx       # (history) render turn media alongside promptText
├── components/share/
│   └── SharedConversation.tsx       # EXTEND: render + play turn media (parity)
├── app/(app)/chat/page.tsx          # EXTEND: upload before submit; pass media refs; per-type capability gating (image/video/audio)
├── app/(app)/admin/storage/page.tsx # NEW: admin storage settings (backend, creds, size limits)
└── lib/
    ├── api/media.ts                 # NEW: upload/delete client for /api/v2/media
    ├── api/storageSettings.ts       # NEW: admin storage settings client
    └── types/api.ts                 # EXTEND: Attachment → media reference; TurnDtoV2 attachments

docker-compose.yml                   # EXTEND: optional `minio` service (dev S3) + media bind mount for local backend
```

**Structure Decision**: Web application (Option 2). Reuse `backend/` (Kotlin/Spring WebFlux) and
`frontend/` (Next.js App Router). The leverage points are (a) the **existing `Attachment`/adapter
path** — image already flows end-to-end, so video/audio + URL-fetch are incremental adapter work
rather than a new pipeline; and (b) the **existing `chat_turns.attachments` jsonb** — its shape moves
from inline base64 to media references, keeping the immutable write-once model intact. A new `media`
package isolates the `MediaStorage` strategy (local vs S3/OSS), and a single `storage_settings` row
gives admins backend + size-limit control. Attachments are newly surfaced through `TurnDtoV2` and the
shared DTO so history and share gain playback at once.

## Complexity Tracking

> No Constitution Check violations — this section is intentionally empty. The single new abstraction
> (`MediaStorage` strategy with local + S3 impls) is mandated by the explicit "local or S3/OSS"
> requirement and is stateless/lock-free.
