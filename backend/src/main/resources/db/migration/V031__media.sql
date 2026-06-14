-- Uploaded media objects (feature 007). One row per image/video/audio upload. Bytes live in the
-- configured storage backend (local filesystem or S3/OSS), never in Postgres. The primary key is an
-- opaque, non-enumerable UUID that also serves as the public URL path segment (Constitution VI).
--
-- turn_id is NULL while an upload is "orphaned" (uploaded but not yet sent); it is set when the owning
-- chat turn is saved, after which the media is immutable. Orphaned rows older than a TTL are swept.
-- Deleting a chat_turn (and thus its session) cascades media deletion so storage tracks the session.
CREATE TABLE media (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    media_type        VARCHAR(16) NOT NULL CHECK (media_type IN ('image', 'video', 'audio')),
    mime_type         VARCHAR(255) NOT NULL,
    size_bytes        BIGINT NOT NULL CHECK (size_bytes >= 0),
    storage_backend   VARCHAR(16) NOT NULL CHECK (storage_backend IN ('local', 's3')),
    storage_key       TEXT NOT NULL,
    public_url        TEXT NOT NULL,
    original_filename TEXT,
    turn_id           UUID REFERENCES chat_turns(id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_turn ON media(turn_id);
-- Drives the orphaned-upload cleanup sweep (turn_id IS NULL older than TTL).
CREATE INDEX idx_media_orphans ON media(created_at) WHERE turn_id IS NULL;

-- Shape note for chat_turns.attachments (jsonb, added in V007): from feature 007 onward, new turns
-- store media REFERENCES, not inline base64. Each element is:
--   { "media_id": uuid, "media_type": "image|video|audio", "mime_type": "...",
--     "size_bytes": int, "url": "<opaque public url>", "order": int }
-- This is forward-only; historical rows keep their old inline shape and render best-effort.
COMMENT ON COLUMN chat_turns.attachments IS
    'Ordered media references (feature 007): {media_id, media_type, mime_type, size_bytes, url, order}. Forward-only; legacy rows may hold inline base64.';
