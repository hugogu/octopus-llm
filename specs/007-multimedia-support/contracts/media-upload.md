# Contract: Media Upload

Uniform upload surface — one endpoint for image/video/audio regardless of target protocol (FR-001/005).
Authenticated (uploader = current user). Stores bytes in the active backend and returns an opaque
public reference.

## POST /api/v2/media

**Request**: `multipart/form-data`
- `file`: the media bytes (required)
- `media_type` (optional hint): `image` | `video` | `audio` — server re-detects and is authoritative

**Server behavior**:
1. Detect actual content type (magic bytes); derive `media_type`. Reject mismatched/unsupported types.
2. Enforce per-type size limit from `storage_settings` (`max_image_bytes` / `max_video_bytes` /
   `max_audio_bytes`). Oversize → `413`.
3. Store via active `MediaStorage` under an opaque id; insert `media` row with `turn_id = NULL`.

**Response** `201 Created`:
```json
{
  "media_id": "f1c2…",
  "media_type": "image",
  "mime_type": "image/png",
  "size_bytes": 12345,
  "url": "https://cdn.example.com/m/f1c2….png",
  "original_filename": "diagram.png"
}
```

**Errors** (standard `{ "code", "message", "details" }`):
- `400 invalid_media` — undetectable/unsupported type, or declared≠detected.
- `413 media_too_large` — exceeds the per-type limit; `details` includes `limit_bytes`, `size_bytes`.
- `401 unauthorized` — not authenticated.
- `503 storage_unavailable` — active backend unreachable (e.g. S3 down) → client may retry (edge case).

> Per-prompt ceiling (≤5 files / ≤15 MB, FR-025) is enforced client-side before upload and
> re-validated at submit (see chat-media.md); a single upload only enforces per-file limits.

## DELETE /api/v2/media/{media_id}

Removes an **orphaned** (unbound, `turn_id IS NULL`) upload the user discarded from the tray before
sending (FR-008). Idempotent.

- `204 No Content` — deleted (or already absent).
- `403 forbidden` — not the owner.
- `409 media_bound` — already attached to a saved turn (immutable; cannot delete via this path).
