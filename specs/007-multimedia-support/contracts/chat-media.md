# Contract: Chat Media (submit, history, share)

How media references flow through the existing chat/share DTOs. Extends current shapes; does not break
text-only turns.

## Submit a turn with media

`POST /api/v2/chat/sessions/{sessionId}/turns` (existing SSE endpoint). `SubmitTurnRequestV2.attachments`
changes from inline base64 to **media references** (already-uploaded via `POST /api/v2/media`):

```json
{
  "promptText": "What's in these?",
  "selectedConfiguredModelIds": ["…"],
  "attachments": [
    { "media_id": "uuid", "media_type": "image", "mime_type": "image/png", "size_bytes": 1234, "url": "https://…", "order": 0 },
    { "media_id": "uuid", "media_type": "video", "mime_type": "video/mp4", "size_bytes": 9000, "url": "https://…", "order": 1 }
  ]
}
```

**Server behavior**:
1. Re-validate per-prompt ceiling (≤5 files / ≤15 MB, FR-025) and that each `media_id` is an
   owned, orphaned upload; bind them (`turn_id = this turn`).
2. **Capability gating (FR-002/003/004)**: for each selected model, if its merged `input_modalities`
   lacks any attached `media_type`, exclude it from dispatch and emit a terminal per-model SSE
   `notice` event (`"<model> does not support <type>"`). If **no** selected model is capable, reject
   the submit with `409 no_capable_model` (the client also blocks this pre-send).
3. Build `LlmRequest.attachments` for capable models from the media refs; **media is included only on
   this turn** — prior turns contribute text-only history (FR-006).
4. Persist the turn's `attachments` jsonb in media-reference shape (immutable).

**SSE events**: unchanged set; capability exclusions use the existing `notice` event (no new type).

## History: `TurnDtoV2` gains attachments (FR-012/013)

`GET /api/v2/chat/sessions/{id}` turns now include the media set so history renders/plays them:

```json
{
  "id": "…", "sequenceNum": 1, "promptText": "…",
  "attachments": [
    { "media_id": "…", "media_type": "image", "mime_type": "image/png", "url": "https://…", "order": 0 }
  ],
  "selectedModelIds": ["…"], "responses": [ … ], "createdAt": "…"
}
```

Frontend renders images inline and video/audio with native controls, in `order`.

## Share: shared turn DTO gains attachments (FR-014/015)

The public shared-conversation payload (via `ShareService`) carries the same `attachments` array, with
**no owner identity** (Constitution VI). Anonymous viewers render images and play video/audio with full
parity. When the share is revoked, its media becomes inaccessible together with the rest of the shared
content (revocation cascades to the referenced media per R9/FR-024).
