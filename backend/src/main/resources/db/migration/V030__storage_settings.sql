-- Platform-wide media storage configuration (feature 007). Single mutable row (id = 1) holding the
-- active storage backend, its connection parameters, and per-type size limits. This is operator
-- config, not session data, so it is intentionally mutable (Constitution IV applies to sessions).
CREATE TABLE storage_settings (
    id                          SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    backend                     VARCHAR(16) NOT NULL DEFAULT 'local' CHECK (backend IN ('local', 's3')),
    local_public_base_url       TEXT,
    s3_endpoint                 TEXT,
    s3_region                   TEXT,
    s3_bucket                   TEXT,
    s3_access_key               TEXT,
    s3_secret_key               TEXT,             -- encrypted at rest; never returned by the API
    s3_public_base_url          TEXT,
    max_image_bytes             BIGINT NOT NULL DEFAULT 1048576  CHECK (max_image_bytes > 0),
    max_video_bytes             BIGINT NOT NULL DEFAULT 10485760 CHECK (max_video_bytes > 0),
    max_audio_bytes             BIGINT NOT NULL DEFAULT 10485760 CHECK (max_audio_bytes > 0),
    max_files_per_prompt        INTEGER NOT NULL DEFAULT 5  CHECK (max_files_per_prompt > 0),
    max_total_bytes_per_prompt  BIGINT NOT NULL DEFAULT 15728640 CHECK (max_total_bytes_per_prompt > 0),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by                  UUID REFERENCES users(id) ON DELETE SET NULL
);

-- Seed the default local-backend row so the read path always finds configuration.
INSERT INTO storage_settings (id, backend) VALUES (1, 'local');
