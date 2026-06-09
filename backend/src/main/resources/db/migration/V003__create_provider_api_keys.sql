CREATE TABLE provider_api_keys (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_id     VARCHAR(100) NOT NULL,
    encrypted_key   BYTEA       NOT NULL,
    key_iv          BYTEA       NOT NULL,
    label           VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_provider_api_keys_user_provider ON provider_api_keys(user_id, provider_id);
