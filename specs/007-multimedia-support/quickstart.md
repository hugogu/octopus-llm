# Quickstart & Validation: Multimedia Support

End-to-end scenarios proving the feature. Assumes the local Docker Compose stack (frontend :3001,
backend :8080, db, optional `minio` for the S3 path). See `data-model.md` and `contracts/` for shapes.

## Prerequisites

```bash
docker compose up -d db backend frontend        # core stack
docker compose up -d minio                       # only for the S3/OSS validation
# Backend code-quality gate
cd backend && ./gradlew build
# Frontend type gate
cd frontend && npx tsc --noEmit
```

A user account with at least one image-capable configured model (e.g. an OpenAI-compatible or
Anthropic model whose `input_modalities` includes `image`) and one text-only model selected.

## Scenario 1 — Image to a capable model, incapable model excluded (US1, FR-002/003)

1. In the chat, select one image-capable and one text-only model.
2. Attach an image; observe a pre-send notice that the text-only model will be excluded.
3. Send. **Expect**: the image-capable model answers about the image; the text-only model shows the
   exclusion notice ("does not support image"); no silent failure (SC-002).
4. Select **only** the text-only model, attach an image. **Expect**: send is blocked with an
   explanation (FR-004 / `409 no_capable_model`).

## Scenario 2 — Tray: multi-file, size limit, reorder, delete (US2, FR-007–011, FR-025)

1. Attach two images and one video. **Expect**: each shows a preview (thumb / video poster).
2. Attach a >1 MB image. **Expect**: rejected before upload with a message stating the 1 MB limit and
   the file size (SC-003).
3. Attempt a 6th file or one pushing total >15 MB. **Expect**: blocked with the ceiling message (FR-025).
4. Drag to reorder; remove one. **Expect**: order persists into the sent turn; removed file is gone and
   its orphaned upload is deleted (`DELETE /api/v2/media/{id}`).

## Scenario 3 — Media in history & share, video plays (US3, FR-012–015)

1. Send a turn with an image and a video; reload the conversation. **Expect**: both render inline in
   order; the video plays inline (FR-013).
2. Create a share link; open it logged-out. **Expect**: image renders and video plays with full parity
   and no owner identity (SC-004).
3. Revoke the share; reopen the link. **Expect**: the shared content **and** its media are inaccessible
   (FR-015).

## Scenario 4 — Voice input (US4, FR-016/017)

1. Click the voice control, record a few seconds, stop. **Expect**: an audio item appears in the tray
   with playback; it can be removed.
2. With an audio-capable model selected, send. **Expect**: the model responds to the spoken content.
3. With only an audio-incapable model selected, send. **Expect**: that model is excluded with a notice.

## Scenario 5 — Admin storage config & local direct-serve (US5, FR-018–022, SC-006)

1. As admin, open **Admin → Storage**. Switch backend to S3/OSS (MinIO dev), enter endpoint/bucket/
   credentials, Test & Save. **Expect**: connectivity verified before save; invalid creds rejected with
   previous config retained (FR-021).
2. Upload media in a conversation. **Expect**: object lands in the configured bucket; `public_url`
   resolves and is opaque/non-enumerable (FR-022).
3. Switch backend to **local**, set `local_public_base_url`, upload again, open history. **Expect**:
   media loads directly from its public URL with **no per-file authenticated backend request**
   (verify in network panel — request goes to the static/public base, not `/api/v2/...`) (SC-006).
4. Change the image size limit; attach a file between old and new limits. **Expect**: new limit enforced
   and reflected in messaging (FR-020).

## Scenario 6 — Orphan cleanup (FR-023, SC-007)

1. Upload a file but navigate away without sending. **Expect**: the `media` row stays `turn_id IS NULL`.
2. After the orphan TTL / cleanup sweep runs. **Expect**: the row and stored object are removed; storage
   does not grow unbounded.

## Gates before "done"

- `./gradlew build` (backend) and `npx tsc --noEmit` (frontend) pass.
- New endpoints have happy-path integration tests (upload, storage-settings, submit-with-media); S3
  path covered via a MinIO Testcontainer.
- Both in-app history and the public share view visually verified (Playwright) for image render +
  video/audio playback (Constitution VIII).
