-- Feature 009: admin-managed tool configuration. Single mutable row (id = 1) holding the provider
-- config for the built-in web_search tool. Operator config, not session data. The provider API key is
-- stored encrypted at rest and never returned by the API.

CREATE TABLE tool_settings (
    id                    SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    web_search_enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
    web_search_provider   VARCHAR(32) NOT NULL DEFAULT 'mimo',
    web_search_base_url   TEXT,
    web_search_model      TEXT,
    web_search_api_key    TEXT,        -- encrypted at rest; never returned by the API
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by            UUID REFERENCES users(id) ON DELETE SET NULL
);
