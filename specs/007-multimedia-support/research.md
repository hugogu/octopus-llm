# Phase 0 Research: Multimedia Support

All spec clarifications were resolved in `/speckit-clarify` (Session 2026-06-14). This document
records the technical decisions that follow from them and from the existing codebase.

## R1. Media storage abstraction (local vs S3/OSS)

- **Decision**: One `MediaStorage` interface with `LocalMediaStorage` and `S3MediaStorage` impls,
  selected at runtime by a single admin-managed `storage_settings` row. S3 impl uses **AWS SDK for
  Java v2** (`software.amazon.awssdk:s3`) with path-style access and a configurable endpoint, which
  covers AWS S3, Aliyun OSS, and MinIO (all S3-compatible).
- **Rationale**: The spec explicitly requires "local or S3/OSS, admin-configurable." AWS SDK v2 is the
  de-facto S3-compatible client and works against OSS/MinIO via endpoint + path-style. A strategy
  interface is the minimum abstraction (Constitution VII) and keeps adapters/orchestration unaware of
  the backend.
- **Alternatives considered**: (a) Spring Resource abstraction — too generic, no S3 lifecycle/ACL
  control; (b) MinIO Java client — narrower ecosystem than AWS SDK; (c) storing bytes in Postgres —
  rejected: violates "served directly by frontend without backend" (FR-019) and bloats the immutable
  session store.

## R2. Public URLs that are opaque and non-enumerable (Q2 / FR-022)

- **Decision**: Each media object gets a random 128-bit identifier (UUIDv4 / URL-safe base62) used as
  its storage key and as the path segment of its public URL. Local: served at a stable public base
  path (e.g. `/<MEDIA_PUBLIC_BASE>/<opaque-id>.<ext>`) by the frontend/static layer from a shared
  bind-mounted directory. S3/OSS: object key = opaque id; public URL = bucket public base + key (or a
  configured CDN base). No directory listing; no sequential ids.
- **Rationale**: Honors "public but unguessable" (Constitution VI opaque-token principle) while
  allowing local media to be served with zero backend round-trip (SC-006).
- **Alternatives considered**: Signed time-limited URLs (Q2 option B) — rejected by clarification:
  conflicts with direct static serving and durable public share links. Authenticated access (option
  C) — rejected: contradicts the public-access requirement.

## R3. Per-model media capability source (Q1 / FR-002)

- **Decision**: Reuse the existing `CapabilityMatrix` (`input_modalities` list already supports
  `"image"`/`"video"`; add `"audio"`) stored per `ConfiguredModel` in `capability_overrides` jsonb and
  merged over protocol `baseline` via the existing `ProtocolDefinitions.mergeCapabilities`. Capability
  is therefore **model-level with protocol-provided defaults, admin-overridable** — exactly Q1.
  Gating reads `input_modalities`; a model lacking the attached media type is excluded.
- **Rationale**: The mechanism already exists end-to-end (frontend `supportsAttachments` already reads
  `input_modalities`). We extend data, not architecture. `supports_video_input` already exists as a
  flag; we standardize on `input_modalities` containing `image`/`video`/`audio` as the single source
  of truth and keep `supports_video_input` consistent with it.
- **Alternatives considered**: Protocol-only (too coarse — OpenAI-compat endpoints vary per model);
  runtime probing (unreliable, not all protocols expose it).

## R4. Auto-exclusion with notice (FR-003/004)

- **Decision**: Reuse the existing per-model `notice`/`capabilityNotice` channel (SSE `notice` →
  `useParallelStream` → `ModelResponsePanel`). When a turn carries media a selected model can't accept,
  the backend excludes that model from dispatch and emits a terminal `notice` ("does not support
  {type}") for it. The frontend additionally pre-computes, from `input_modalities`, which selected
  models will be excluded and shows it before send; if **all** selected models are incapable, the send
  button is blocked with an explanation.
- **Rationale**: Zero-silent-failure (SC-002) using machinery already present; no new event type.
- **Alternatives considered**: Hard client-side removal of model chips (too aggressive — user may want
  to switch attachment instead); failing the whole turn (rejected — other capable models must still
  answer).

## R5. Media scope across turns (Q3 / FR-006)

- **Decision**: Attachments belong to the turn they were sent on. `ChatService.requestForTurn` builds
  `HistoryTurn` for prior turns with **text only** (it already does — `HistoryTurn(role, text)` with no
  attachments). New media goes only into the current `LlmRequest.attachments`. No change needed to the
  history-building path beyond not adding prior media.
- **Rationale**: Matches Q3 (cost-bounded), and the existing history builder already omits prior
  attachments, so this is the natural, lower-cost behavior.
- **Alternatives considered**: Re-sending prior media each turn (Q3 option A) — rejected by
  clarification for token cost.

## R6. Adapter media adaptation

- **Decision**: Extend `Attachment` to carry `mediaType` (`image|video|audio`), `url` (public) and
  keep optional inline `data` for backward-compat. Adapters fetch bytes from `url` when present (or use
  `data`) and map per protocol: **Anthropic** image/document/video blocks (base64 or URL source per
  API support); **OpenAI-compatible** `image_url` / input-audio / video content parts; **MiniMax** its
  multimodal content schema. Each adapter only forwards types its model declares; the orchestration
  layer has already excluded incapable models, so adapters never receive an unsupported type for a
  capable model.
- **Rationale**: Image path already exists in Anthropic/OpenAI adapters; this is incremental.
  Provider-specific encoding stays inside adapters (Constitution I).
- **Open detail (deferred to implementation)**: exact accepted container/codec per provider and
  whether a provider needs a base64 inline vs URL reference — handled per-adapter with capability
  defaults; unsupported encodings surface as a per-model provider error without failing the turn.

## R7. Voice input capture (FR-016/017)

- **Decision**: Browser-native `navigator.mediaDevices.getUserMedia` + `MediaRecorder` capture an
  audio blob (default `audio/webm` or `audio/mp4` per browser), added to the tray as an `audio`
  attachment and uploaded like any file. No new frontend dependency. Sent to audio-capable models as
  an audio file; gated/excluded like other media.
- **Rationale**: `MediaRecorder` is broadly supported and dependency-free, consistent with the spec's
  "send as an audio file."
- **Alternatives considered**: Third-party recorder libs (unnecessary); client-side transcription —
  rejected by the audio-as-file assumption.

## R8. Size limits, per-prompt ceiling, content-type validation

- **Decision**: Limits live in `storage_settings` (defaults 1 MB image / 10 MB video; audio default
  set alongside, e.g. 10 MB). Per-prompt ceiling **≤5 files / ≤15 MB** (FR-025) enforced client-side
  before upload AND re-validated server-side on upload/submit. Server validates **actual detected
  content type** (magic-byte sniff) against the claimed type to prevent spoofing (edge case).
- **Rationale**: Client checks give instant feedback (SC-003); server checks are the trust boundary.
- **Alternatives considered**: Server-only validation (worse UX); trusting client MIME (insecure).

## R9. Orphaned-upload cleanup (FR-023)

- **Decision**: `media.turn_id` is nullable; an upload starts orphaned (no turn). On submit, the turn's
  media rows are bound (`turn_id` set). An idempotent scheduled sweep deletes media rows + stored
  objects that remain `turn_id IS NULL` older than a TTL (e.g. 24 h). Deleting a session/turn or
  revoking a share cascades media deletion (FR-024/015).
- **Rationale**: Lock-free, horizontally safe (Constitution VII); bounded storage growth (SC-007).
- **Alternatives considered**: Reference counting (overkill); never cleaning (unbounded growth).

## R10. Admin storage settings UI & nav (Constitution VIII)

- **Decision**: New `/(app)/admin/storage` page using `AdminShell` + design system, linked in admin
  nav. Form: backend selector (local / S3-OSS), conditional S3 fields (endpoint, region, bucket,
  access key, secret — secret write-only, never echoed), per-type size limits. "Test & Save" validates
  S3 connectivity (HEAD bucket) before persisting; invalid config rejected, previous config retained
  (FR-021). Loading/disabled/error states throughout.
- **Rationale**: Operator-facing config must be discoverable and consistent (Constitution VIII).
- **Alternatives considered**: Env-only config (not admin-configurable as required); per-connection
  storage (rejected — platform-wide per clarification).
