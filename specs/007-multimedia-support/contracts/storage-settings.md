# Contract: Admin Storage Settings

Platform-wide storage backend + size limits (FR-018/020/021). Admin-only. Reachable via admin nav
(Constitution VIII).

## GET /api/v2/admin/storage-settings

**Response** `200`:
```json
{
  "backend": "s3",
  "local_public_base_url": null,
  "s3_endpoint": "https://oss-cn-hangzhou.aliyuncs.com",
  "s3_region": "cn-hangzhou",
  "s3_bucket": "octopus-media",
  "s3_access_key": "LTAI…",
  "s3_secret_key_set": true,
  "s3_public_base_url": "https://octopus-media.oss-cn-hangzhou.aliyuncs.com",
  "max_image_bytes": 1048576,
  "max_video_bytes": 10485760,
  "max_audio_bytes": 10485760,
  "max_files_per_prompt": 5,
  "max_total_bytes_per_prompt": 15728640,
  "updated_at": "2026-06-14T…",
  "updated_by": "uuid"
}
```

**Privacy**: the secret key is **never** returned — only `s3_secret_key_set: boolean` (Constitution VI).

## PUT /api/v2/admin/storage-settings

**Request** (only mutable fields; omit `s3_secret_key` to keep the existing one):
```json
{
  "backend": "s3",
  "s3_endpoint": "…",
  "s3_region": "…",
  "s3_bucket": "…",
  "s3_access_key": "…",
  "s3_secret_key": "…",
  "s3_public_base_url": "…",
  "max_image_bytes": 1048576,
  "max_video_bytes": 10485760,
  "max_audio_bytes": 10485760,
  "max_files_per_prompt": 5,
  "max_total_bytes_per_prompt": 15728640
}
```

**Server behavior**:
1. Validate field coherence (s3 backend requires endpoint/bucket/credentials; local requires
   `local_public_base_url`).
2. **Verify connectivity before persist** (FR-021): HEAD bucket / write+read+delete a probe object for
   S3; verify writable directory for local. On failure → reject, keep previous config in effect.
3. Persist (encrypt secret at rest); record `updated_by`/`updated_at`; emit admin audit log.

**Responses**:
- `200` — saved; returns the GET shape.
- `400 invalid_storage_config` — incoherent fields; `details` lists offending fields.
- `422 storage_unreachable` — credentials/endpoint failed verification; previous config retained.
- `403 forbidden` — non-admin.

New limits take effect for the next upload/attach (FR-020); they are surfaced to the client (via the
chat capabilities/config payload) so the tray can message limits accurately.
