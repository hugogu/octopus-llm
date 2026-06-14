# Phase 1 Data Model: Multimedia Support

Snake_case schema, Flyway migrations only (Constitution IV). New tables are additive; the existing
immutable `chat_turns` keeps INSERT-once semantics — only the **shape** of its `attachments` jsonb
changes (forward-only).

## New table: `storage_settings` (V030)

Single-row platform configuration, admin-managed. Mutable (it is operator config, not session data).

| Column | Type | Notes |
|--------|------|-------|
| `id` | `smallint` PK, CHECK (`id = 1`) | enforces single row |
| `backend` | `text` NOT NULL | `local` \| `s3` |
| `local_public_base_url` | `text` NULL | public base for local-served media |
| `s3_endpoint` | `text` NULL | S3/OSS endpoint (path-style) |
| `s3_region` | `text` NULL | region |
| `s3_bucket` | `text` NULL | bucket name |
| `s3_access_key` | `text` NULL | encrypted at rest |
| `s3_secret_key` | `text` NULL | encrypted at rest; never returned in API |
| `s3_public_base_url` | `text` NULL | public/CDN base for S3 objects |
| `max_image_bytes` | `bigint` NOT NULL DEFAULT 1048576 | 1 MB |
| `max_video_bytes` | `bigint` NOT NULL DEFAULT 10485760 | 10 MB |
| `max_audio_bytes` | `bigint` NOT NULL DEFAULT 10485760 | 10 MB |
| `max_files_per_prompt` | `int` NOT NULL DEFAULT 5 | FR-025 |
| `max_total_bytes_per_prompt` | `bigint` NOT NULL DEFAULT 15728640 | 15 MB, FR-025 |
| `updated_at` | `timestamptz` NOT NULL | audit |
| `updated_by` | `uuid` NULL | admin user |

**Validation rules**: when `backend = s3`, `s3_endpoint`/`s3_bucket` (and credentials) required and
connectivity verified before persist (FR-021). Secret key write-only in API responses.

## New table: `media` (V031)

One row per uploaded media object. Bytes live in the storage backend, not here.

| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid` PK | opaque, non-enumerable identifier (also the public path segment) |
| `owner_user_id` | `uuid` NOT NULL FK → users | uploader |
| `media_type` | `text` NOT NULL | `image` \| `video` \| `audio` |
| `mime_type` | `text` NOT NULL | detected content type |
| `size_bytes` | `bigint` NOT NULL | validated against limits |
| `storage_backend` | `text` NOT NULL | `local` \| `s3` (backend at upload time) |
| `storage_key` | `text` NOT NULL | object key / relative path |
| `public_url` | `text` NOT NULL | opaque public URL (FR-022) |
| `original_filename` | `text` NULL | display only |
| `turn_id` | `uuid` NULL FK → chat_turns | NULL while orphaned; set on submit (R9) |
| `created_at` | `timestamptz` NOT NULL | TTL anchor for orphan sweep |

**Indexes**: `(turn_id)` for turn lookup; `(turn_id, created_at)` partial WHERE `turn_id IS NULL` for
the orphan sweep.

**Lifecycle / state transitions**:
1. `orphaned` — created by upload, `turn_id IS NULL`.
2. `bound` — `turn_id` set when the owning turn is saved; immutable thereafter.
3. `deleted` — removed (row + stored object) when: orphan TTL elapses (FR-023); owning session/turn
   deleted; or the only share exposing it is revoked together with the session (FR-015/024).

## Changed shape (forward-only): `chat_turns.attachments` (jsonb)

Existing column (`V007`), today an array of `{type, data(base64), mimeType}`. New writes use **media
references**; historical rows keep their old shape and are rendered best-effort.

New element shape:

```json
{
  "media_id": "uuid",
  "media_type": "image | video | audio",
  "mime_type": "image/png",
  "size_bytes": 12345,
  "url": "https://.../<opaque-id>.png",
  "order": 0
}
```

`order` preserves user drag-reorder (FR-009). No DDL change required (jsonb), but V031 may add a SQL
comment documenting the new shape. `chat_turns` rows remain INSERT-once (Constitution IV).

## Capability metadata (no new table)

Reuses `configured_models.capability_overrides` (jsonb) merged over protocol baseline via
`ProtocolDefinitions.mergeCapabilities`:

- `input_modalities`: list now meaningfully includes `"image"`, `"video"`, `"audio"`.
- `supports_video_input`: kept consistent with `input_modalities` containing `"video"`.
- A model accepts an attached media type iff its merged `input_modalities` contains that type;
  otherwise it is excluded with a notice (FR-002/003).

## Entity mapping to spec

| Spec entity | Realization |
|-------------|-------------|
| Media Attachment | `media` row + its reference element in `chat_turns.attachments` |
| Turn Media Set | ordered `attachments` array of one `chat_turns` row (by `order`) |
| Model Media Capability | merged `CapabilityMatrix.input_modalities` per `configured_models` |
| Storage Configuration | `storage_settings` singleton row |
