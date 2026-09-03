ALTER TABLE configured_models
    ADD COLUMN is_anonymous_default BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_configured_models_anonymous_defaults
    ON configured_models (is_anonymous_default)
    WHERE is_anonymous_default = TRUE;
