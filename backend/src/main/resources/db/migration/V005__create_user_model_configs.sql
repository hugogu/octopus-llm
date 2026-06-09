CREATE TABLE user_model_configs (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    model_id             VARCHAR(100) NOT NULL REFERENCES model_definitions(id),
    provider_api_key_id  UUID        REFERENCES provider_api_keys(id) ON DELETE SET NULL,
    is_enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_model UNIQUE (user_id, model_id)
);
