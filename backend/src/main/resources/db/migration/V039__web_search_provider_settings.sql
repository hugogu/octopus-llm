-- Feature 009: let multiple web_search providers be configured side by side. tool_settings keeps only
-- the enabled flag + which provider is active; each provider's url/model/key lives in its own row, so
-- switching the active provider no longer requires re-entering credentials.

CREATE TABLE web_search_provider_settings (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    provider    VARCHAR(32) NOT NULL UNIQUE,
    base_url    TEXT,
    model       TEXT,
    api_key     TEXT,        -- encrypted at rest; never returned by the API
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by  UUID REFERENCES users(id) ON DELETE SET NULL
);

-- Carry any existing single web_search config into its own provider row.
INSERT INTO web_search_provider_settings (provider, base_url, model, api_key, updated_at, updated_by)
SELECT web_search_provider, web_search_base_url, web_search_model, web_search_api_key, updated_at, updated_by
FROM tool_settings
WHERE id = 1;

ALTER TABLE tool_settings RENAME COLUMN web_search_provider TO web_search_active_provider;
ALTER TABLE tool_settings DROP COLUMN web_search_base_url;
ALTER TABLE tool_settings DROP COLUMN web_search_model;
ALTER TABLE tool_settings DROP COLUMN web_search_api_key;
