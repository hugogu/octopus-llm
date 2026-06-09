CREATE TABLE model_definitions (
    id                 VARCHAR(100) PRIMARY KEY,
    provider_id        VARCHAR(100) NOT NULL,
    display_name       VARCHAR(255) NOT NULL,
    capability_matrix  JSONB        NOT NULL,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_model_definitions_provider ON model_definitions(provider_id);
