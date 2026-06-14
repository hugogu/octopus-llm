# Feature Specification: Multimedia Support (Images, Video, Voice)

**Feature Branch**: `007-multimedia-support`
**Created**: 2026-06-14
**Status**: Draft
**Input**: User description: "提供多媒体的支持。允许用户通过聊天窗口上传视频、图片给支持的AI，不支持的AI自动从选择中排除；可配置的文件大小限制（默认图片1MB、视频10MB），上传后在聊天窗口预览（可删除、可拖拽排序）；上传的视频和图片像文本一样出现在聊天历史及分享记录中，视频可播放；图片及视频可存储于本地或S3/OSS（管理员后台配置），本地存储时直接由前端对外服务、不走后端，暂定公开访问；聊天窗口可直接语音输入，以音频文件形式传给AI。"

## Clarifications

### Session 2026-06-14

- Q: Where does each model's media capability (image/video/audio support) come from? → A: Model-level — each configured model carries media-capability flags, pre-filled with protocol-provided defaults and overridable by an administrator.
- Q: What access/security model governs uploaded media? → A: Publicly readable via unguessable, non-enumerable (opaque) URLs; no authentication on individual media files.
- Q: In a multi-turn conversation, how does previously attached media participate in later turns? → A: Media is sent only on the turn it was attached to; subsequent turns send text only and do not re-send prior media.
- Q: What is the per-prompt combined attachment ceiling (beyond per-file limits)? → A: At most 5 files and 15 MB combined per prompt.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Attach images and video to a capable model and get an answer (Priority: P1)

A user composing a prompt attaches one or more images (and/or a short video) from the chat
window and sends it to the set of AI models they have selected for side-by-side comparison.
Models that can accept the attached media receive it alongside the text and respond about it;
models that cannot accept that media type are automatically excluded from this send, and the
user is told which ones were skipped and why.

**Why this priority**: This is the core value of the feature — turning a text-only comparison
tool into a multimodal one. Without it, none of the other stories (preview, history, storage,
voice) have anything to operate on. It is the smallest slice that delivers a working multimodal
comparison.

**Independent Test**: Select two models — one that accepts images and one that does not — attach
an image, send a prompt, and confirm the image-capable model returns a relevant answer while the
incapable model is excluded with a clear notice. Fully testable on its own.

**Acceptance Scenarios**:

1. **Given** a user has selected one or more image-capable models, **When** they attach an image and send a prompt, **Then** each selected capable model receives the image plus text and returns a response that reflects the image content.
2. **Given** a user has selected a mix of capable and incapable models, **When** they attach a video and send, **Then** the incapable models are excluded from this turn and the user sees a notice naming the excluded models and the reason ("does not support video").
3. **Given** a user has selected only models that cannot accept the attached media, **When** they attempt to send, **Then** the system prevents the send and explains that no selected model can accept the attachment, prompting the user to change the selection or remove the attachment.
4. **Given** an image-capable model received an image on an earlier turn, **When** the conversation continues to a later turn, **Then** that later turn sends text only and the earlier media is not re-sent (it remains attached to its original turn in the record).

---

### User Story 2 - Manage attachments before sending (preview, delete, reorder, size limits) (Priority: P2)

Before sending, the user sees a preview tray of everything they have attached. They can attach
multiple files, preview each one, remove any of them, and reorder them by dragging. Files larger
than the configured limit for their type are rejected at attach time with a clear message, so the
user never submits an oversized file.

**Why this priority**: This is the everyday authoring experience around US1. It makes attachment
usable and trustworthy but depends on US1 existing first.

**Independent Test**: Attach several images and a video, confirm each renders a thumbnail/preview,
remove one, drag to reorder the rest, and attempt to attach a file above the limit to confirm it
is rejected with a message — all without sending.

**Acceptance Scenarios**:

1. **Given** the user has attached one or more files, **When** the attachment tray renders, **Then** each file shows a preview (image thumbnail, video poster/first frame, audio indicator) and a remove control.
2. **Given** the user has multiple attachments, **When** they drag one to a new position, **Then** the ordering updates and that ordering is preserved when the prompt is sent.
3. **Given** a configured per-type size limit (default 1 MB images, 10 MB video), **When** the user attaches a file exceeding the limit for its type, **Then** the file is rejected before upload and the user sees a message stating the limit and the file's size.
4. **Given** the user removes an attachment from the tray, **When** they then send the prompt, **Then** the removed file is not included and is not referenced in the saved turn.

---

### User Story 3 - Media appears and plays in chat history and shared records (Priority: P2)

When a user revisits a past conversation, or opens a shared link to one, the images and videos
that were attached appear inline exactly as part of the record, just like the text. Videos are
playable inline in both the history view and the public share view.

**Why this priority**: Persistence and shareability are what make multimodal conversations
durable and useful to others; it builds directly on US1/US2 having captured the media.

**Independent Test**: Send a prompt with an image and a video, reload the conversation history and
confirm both render inline and the video plays; open the conversation's share link in a logged-out
context and confirm the same media renders and the video plays.

**Acceptance Scenarios**:

1. **Given** a past turn that included attached images and video, **When** the user opens that conversation's history, **Then** the images render inline and the video is playable inline within the turn.
2. **Given** a conversation has been shared via its opaque share link, **When** an anonymous viewer opens the link, **Then** the attached images and video render and the video plays, with full parity to the owner's history view.
3. **Given** a turn contained both text and media, **When** it is displayed in history or share, **Then** the media appears in the same order the user arranged it, associated with the correct turn and author.

---

### User Story 4 - Voice input sent as audio to the model (Priority: P3)

From the chat window the user records their voice and sends it as an audio file to the selected
AI models. Audio-capable models receive the recording; incapable models are excluded with the
same notice behavior as other media types.

**Why this priority**: Voice broadens input modality and accessibility, but it is an additive
input path on top of the attachment and capability-gating machinery established by US1/US2.

**Independent Test**: Record a short voice clip in the chat window, send to an audio-capable model
and confirm it responds to the spoken content; with an audio-incapable model selected, confirm it
is excluded with a notice.

**Acceptance Scenarios**:

1. **Given** the user is composing a prompt, **When** they start and stop a voice recording, **Then** the recording appears in the attachment tray as an audio item with a preview/playback control and can be removed before sending.
2. **Given** the user sends a recorded audio attachment, **When** a selected model supports audio input, **Then** that model receives the audio and responds to its content.
3. **Given** a selected model does not support audio input, **When** the user sends an audio attachment, **Then** that model is excluded from the turn with a notice naming it and the reason.

---

### User Story 5 - Admin configures storage backend and size limits (Priority: P3)

An administrator chooses, from the admin control panel, where user-uploaded media is stored —
the platform's local storage or an S3/OSS-compatible object store — and sets the per-type maximum
file sizes. When local storage is used, uploaded media is served directly to viewers without
routing each file through the backend application.

**Why this priority**: Configurability and storage portability matter for operators, but the
feature works with built-in defaults (local storage, 1 MB / 10 MB) without it, so it can follow
the user-facing stories.

**Independent Test**: In the admin panel, switch the storage backend from local to an S3/OSS
target with credentials, upload media in a conversation, and confirm it is stored in and served
from the configured object store; change the image size limit and confirm the new limit is
enforced at attach time.

**Acceptance Scenarios**:

1. **Given** an administrator on the storage settings page, **When** they select local storage or an S3/OSS-compatible target and save valid configuration, **Then** subsequent uploads are stored in the selected backend.
2. **Given** local storage is configured, **When** a viewer loads a conversation or share containing media, **Then** the media is fetched directly from its public URL without an authenticated backend request per file.
3. **Given** an administrator changes the per-type size limits, **When** a user next attaches a file, **Then** the new limits are enforced and reflected in any limit messaging.
4. **Given** an S3/OSS target with invalid credentials, **When** the administrator saves, **Then** the configuration is rejected with a clear validation error and the previous working configuration remains in effect.

---

### Edge Cases

- A model's media capability is unknown or the provider has not declared it — treat as "not
  supported" for that media type and exclude it rather than risk a failed send.
- An upload succeeds but the model call later fails — the attachment remains associated with the
  user's turn; failure is reported per model as with text-only turns.
- A user attaches a file whose declared type does not match its actual content (e.g. a renamed
  file) — validation is based on actual detected type, and mismatches are rejected.
- The configured object store is unreachable at upload time — the user sees an upload failure and
  can retry; the prompt is not silently sent without its media.
- A very long or large video is within the size limit but exceeds a provider's own media limits —
  the provider's rejection is surfaced per model without failing the whole turn.
- Media attached but the user navigates away or never sends — orphaned uploads must not accumulate
  indefinitely (they are cleaned up).
- Total combined size or count of attachments on a single prompt exceeds a sane maximum — the user
  is told before sending.
- A shared link is later revoked — its media must become inaccessible together with the rest of
  the shared content.

## Requirements *(mandatory)*

### Functional Requirements

#### Uploading & capability gating

- **FR-001**: The chat window MUST let a user attach one or more image and video files to a prompt through a single, unified attachment control, regardless of which underlying AI protocols are selected.
- **FR-002**: The system MUST determine, per selected model, whether it can accept each attached media type (image, video, audio), exposing this as capability metadata that the frontend can rely on without protocol-specific logic. Each configured model MUST carry media-capability flags, pre-filled with protocol-provided defaults and overridable per model by an administrator. A model whose capability is unset/unknown MUST be treated as not supporting that media type.
- **FR-003**: When one or more selected models cannot accept an attached media type, the system MUST automatically exclude those models from that send and present a notice that names the excluded models and the reason.
- **FR-004**: When no selected model can accept an attached media type, the system MUST prevent the send and prompt the user to adjust their selection or remove the attachment.
- **FR-005**: The backend MUST adapt each attached media item to the format required by each target AI protocol, while presenting a single uniform upload/attachment contract to the frontend (no protocol-specific upload paths in the client).
- **FR-006**: Attached media MUST be associated with the user's turn and sent to capable models only on that turn; subsequent turns of the same conversation MUST send text only and MUST NOT re-send prior media. The media remains attached to its original turn in the saved record.

#### Attachment management UX

- **FR-007**: The chat window MUST show a preview tray of all pending attachments, rendering an image thumbnail, a video poster/first frame, and an audio indicator with playback as appropriate.
- **FR-008**: Users MUST be able to remove any pending attachment before sending, and removed attachments MUST NOT be included in or referenced by the saved turn.
- **FR-009**: Users MUST be able to reorder pending attachments by drag-and-drop, and the chosen order MUST be preserved in the sent prompt and in the saved record.
- **FR-010**: The system MUST enforce a per-media-type maximum file size at attach time, rejecting oversized files before upload with a message stating the limit and the file's size.
- **FR-011**: Default per-type size limits MUST be 1 MB for images and 10 MB for video, applied when no administrator override is set.
- **FR-025**: In addition to per-file limits, the system MUST enforce a per-prompt ceiling of at most 5 attachments and 15 MB combined, informing the user before sending when an attempt would exceed it.

#### History, sharing & playback

- **FR-012**: Images and video attached to a turn MUST appear inline in the conversation history exactly as part of that turn, alongside the text, in the user-arranged order.
- **FR-013**: Videos in conversation history MUST be playable inline; images MUST render inline.
- **FR-014**: The public share view MUST render attached images and play attached video with full parity to the owner's history view, using the existing opaque, revocable share-link mechanism.
- **FR-015**: When a share link is revoked, the media it exposed MUST become inaccessible together with the rest of the shared content.

#### Voice input

- **FR-016**: The chat window MUST let the user record voice input directly and add the recording to the attachment tray as an audio item that can be previewed, removed, and sent.
- **FR-017**: Recorded audio MUST be sent to selected models as an audio file, subject to the same capability gating and exclusion-with-notice behavior as other media types.

#### Storage & administration

- **FR-018**: An administrator MUST be able to choose, from the admin control panel, whether user-uploaded media is stored in the platform's local storage or in an S3/OSS-compatible object store, providing the necessary connection configuration for the latter.
- **FR-019**: When local storage is configured, stored media MUST be served to viewers directly from a public URL without routing each media file through an authenticated backend request.
- **FR-020**: An administrator MUST be able to configure the per-media-type maximum file sizes, and updated limits MUST take effect for subsequent attachments and be reflected in limit messaging.
- **FR-021**: The system MUST validate object-store configuration before applying it, rejecting invalid configuration with a clear error and leaving the previously working configuration in effect.
- **FR-022**: Uploaded media URLs MUST be publicly readable but MUST use unguessable, non-enumerable identifiers so that media cannot be discovered by guessing URLs.
- **FR-023**: Media uploaded but never sent (orphaned) MUST be cleaned up so storage does not grow unbounded.
- **FR-024**: Stored media MUST be retained for as long as the conversation or share that references it exists, and become inaccessible when that record is deleted.

### Key Entities *(include if feature involves data)*

- **Media Attachment**: A single uploaded item (image, video, or audio) belonging to a user turn. Key attributes: media type, original filename, content type, byte size, storage location/public URL, display order within the turn, and the owning turn reference. Immutable once its turn is saved.
- **Turn Media Set**: The ordered collection of Media Attachments belonging to one chat turn, preserving user-arranged order and surfaced uniformly across compose, history, and share.
- **Model Media Capability**: Per-model metadata declaring which media input types (image, video, audio) the model accepts, used to gate selection and exclude incapable models.
- **Storage Configuration**: Administrator-managed setting selecting the storage backend (local vs S3/OSS-compatible) and its connection parameters, plus the per-media-type size limits. Platform-wide.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can attach an image and receive a relevant response from a capable model in the same number of steps as sending a text-only prompt plus one attach action (no extra configuration).
- **SC-002**: When a selected model cannot accept an attached media type, the user always sees which model was excluded and why before or upon sending — 100% of incapable-model cases produce a visible notice, with zero silent failures.
- **SC-003**: Oversized files are rejected before any upload begins in 100% of cases, with a message that states both the limit and the offending file's size.
- **SC-004**: Images and videos that were sent appear and (for video) play correctly in both conversation history and the public share view in 100% of cases, in the order the user arranged them.
- **SC-005**: An administrator can switch the storage backend between local and S3/OSS-compatible and change size limits without code changes, and the change takes effect for the next upload.
- **SC-006**: When local storage is configured, media in a viewed conversation or share loads directly from its public URL without a per-file authenticated backend round-trip.
- **SC-007**: Media uploaded but never sent does not remain stored indefinitely — orphaned uploads are removed within the platform's defined cleanup window.

## Assumptions

- **Audio = raw file, not transcript**: Voice input is sent to models as an audio file (multimodal audio input), not transcribed to text, matching the explicit request. Audio-incapable models are excluded like any other unsupported media type.
- **Public-by-default with opaque URLs**: Per the request, uploaded media is publicly accessible. To honor the constitution's opaque-token principle, public URLs use unguessable, non-enumerable identifiers rather than authentication. There is no per-user access control on individual media files in this iteration.
- **Platform-wide storage configuration**: The storage backend and size limits are a single platform-wide administrator setting, not per-user or per-connection.
- **S3/OSS = S3-compatible API**: "Object storage" means an S3/OSS-compatible API (e.g. AWS S3, Aliyun OSS, MinIO) configured with endpoint, bucket, and credentials.
- **Supported media types**: Images and video for upload attachments; audio for voice input. Exact accepted container/codec formats are bounded by what target providers accept and are refined during planning.
- **Combined-attachment ceiling**: In addition to per-file limits, a single prompt may carry at most 5 attachments totaling at most 15 MB (FR-025).
- **Capability source**: Media capability is declared per configured model, pre-populated from protocol-provided defaults and overridable by an administrator; unset/unknown capability is treated as "not supported" for safety.
- **Multi-turn media scope**: Attached media is sent only on the turn it was attached to; later turns send text only and do not re-send prior media (FR-006).
- **Existing share mechanism reused**: Media in shares relies on the existing opaque, revocable share-link feature rather than a new sharing model.
- **Builds on existing rendering**: Inline rendering/playback extends the existing rich-response rendering surface (feature 006) rather than introducing a separate viewer.
