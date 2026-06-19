-- Feature 008: migration operation ledger (idempotency + non-secret result audit) plus a crash-safe
-- staged-media cleanup ledger. No passphrase, provider key, endpoint secret, or sensitive custom
-- parameter is ever written here (Constitution VI) — only counts and created ids.
CREATE TABLE migration_operations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation_type       VARCHAR(32) NOT NULL
        CHECK (operation_type IN ('admin_export', 'admin_import', 'share_import')),
    idempotency_key_hash BYTEA,
    source_digest        BYTEA,
    status               VARCHAR(16) NOT NULL DEFAULT 'in_progress'
        CHECK (status IN ('in_progress', 'succeeded', 'failed')),
    result               JSONB NOT NULL DEFAULT '{}',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Claims an import retry atomically: one operation per (actor, type, idempotency key). Exports carry
-- no idempotency key and are excluded from the uniqueness via the partial index predicate.
CREATE UNIQUE INDEX uq_migration_operations_idempotency
    ON migration_operations(actor_user_id, operation_type, idempotency_key_hash)
    WHERE idempotency_key_hash IS NOT NULL;
CREATE INDEX idx_migration_operations_status ON migration_operations(status, created_at);

-- Records each external media object write before it happens, so an import interrupted between the
-- object write and the final DB commit can be swept (the row tracks exactly what to delete).
CREATE TABLE migration_staged_media (
    operation_id    UUID NOT NULL REFERENCES migration_operations(id) ON DELETE CASCADE,
    media_id        UUID NOT NULL,
    storage_backend VARCHAR(16) NOT NULL,
    storage_key     TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (operation_id, media_id)
);
CREATE INDEX idx_migration_staged_media_created ON migration_staged_media(created_at);
