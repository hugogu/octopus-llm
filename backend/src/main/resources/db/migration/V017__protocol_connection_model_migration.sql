CREATE TABLE connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    protocol VARCHAR(50) NOT NULL,
    label VARCHAR(255),
    base_url VARCHAR(500) NOT NULL,
    encrypted_key BYTEA NOT NULL,
    key_iv BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_connections_user_id_id UNIQUE (user_id, id),
    CONSTRAINT ck_connections_protocol CHECK (protocol IN ('openai-compatible', 'anthropic', 'minimax'))
);

CREATE INDEX idx_connections_user_created
    ON connections(user_id, created_at, id);

CREATE TABLE configured_models (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    connection_id UUID NOT NULL,
    model_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    capability_overrides JSONB NOT NULL DEFAULT '{}',
    custom_params JSONB NOT NULL DEFAULT '{}',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_configured_models_owner_connection
        FOREIGN KEY (user_id, connection_id)
        REFERENCES connections(user_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_configured_models_user_enabled_order
    ON configured_models(user_id, is_enabled, sort_order, created_at, id);
CREATE INDEX idx_configured_models_connection
    ON configured_models(connection_id);

CREATE TABLE configuration_migration_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    migrated_connections INTEGER NOT NULL,
    migrated_models INTEGER NOT NULL,
    skipped_models_without_key INTEGER NOT NULL,
    unmapped_providers INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO connections (
    id, user_id, protocol, label, base_url, encrypted_key, key_iv, created_at, updated_at
)
SELECT
    pak.id,
    pak.user_id,
    CASE
        WHEN pak.provider_id IN ('openai', 'moonshot', 'deepseek', 'zhipu', 'kimi') THEN 'openai-compatible'
        WHEN pak.provider_id = 'anthropic' THEN 'anthropic'
        WHEN pak.provider_id = 'minimax' THEN 'minimax'
    END,
    pak.label,
    COALESCE(
        NULLIF(TRIM(TRAILING '/' FROM pak.base_url), ''),
        CASE pak.provider_id
            WHEN 'openai' THEN 'https://api.openai.com/v1'
            WHEN 'moonshot' THEN 'https://api.moonshot.cn/v1'
            WHEN 'kimi' THEN 'https://api.moonshot.cn/v1'
            WHEN 'deepseek' THEN 'https://api.deepseek.com/v1'
            WHEN 'zhipu' THEN 'https://open.bigmodel.cn/api/paas/v4'
            WHEN 'anthropic' THEN 'https://api.anthropic.com'
            WHEN 'minimax' THEN 'https://api.minimax.chat/v1'
        END
    ),
    pak.encrypted_key,
    pak.key_iv,
    pak.created_at,
    pak.updated_at
FROM provider_api_keys pak
WHERE pak.provider_id IN ('openai', 'moonshot', 'deepseek', 'zhipu', 'kimi', 'anthropic', 'minimax');

INSERT INTO configured_models (
    id, user_id, connection_id, model_id, display_name,
    capability_overrides, custom_params, is_enabled, sort_order, created_at, updated_at
)
SELECT
    umc.id,
    umc.user_id,
    umc.provider_api_key_id,
    umc.model_id,
    md.display_name,
    COALESCE(md.capability_matrix, '{}'),
    COALESCE(umc.custom_params, '{}'),
    umc.is_enabled,
    ROW_NUMBER() OVER (
        PARTITION BY umc.provider_api_key_id
        ORDER BY umc.created_at, umc.id
    ) - 1,
    umc.created_at,
    umc.updated_at
FROM user_model_configs umc
JOIN model_definitions md ON md.id = umc.model_id
JOIN connections c
  ON c.id = umc.provider_api_key_id
 AND c.user_id = umc.user_id
WHERE umc.provider_api_key_id IS NOT NULL;

ALTER TABLE provider_responses
    DROP CONSTRAINT IF EXISTS provider_responses_model_id_fkey,
    DROP CONSTRAINT IF EXISTS uq_response_turn_model,
    ALTER COLUMN model_id TYPE VARCHAR(255),
    ADD COLUMN configured_model_id UUID,
    ADD COLUMN model_display_name VARCHAR(255),
    ADD COLUMN protocol VARCHAR(50),
    ADD COLUMN connection_label VARCHAR(255);

UPDATE provider_responses pr
SET
    configured_model_id = COALESCE((
        SELECT cm.id
        FROM chat_turns ct
        JOIN chat_sessions cs ON cs.id = ct.session_id
        JOIN configured_models cm
          ON cm.user_id = cs.user_id
         AND cm.model_id = pr.model_id
        WHERE ct.id = pr.turn_id
        ORDER BY cm.created_at, cm.id
        LIMIT 1
    ), gen_random_uuid()),
    model_display_name = COALESCE(md.display_name, pr.model_id),
    protocol = COALESCE((
        SELECT c.protocol
        FROM chat_turns ct
        JOIN chat_sessions cs ON cs.id = ct.session_id
        JOIN configured_models cm
          ON cm.user_id = cs.user_id
         AND cm.model_id = pr.model_id
        JOIN connections c ON c.id = cm.connection_id
        WHERE ct.id = pr.turn_id
        ORDER BY cm.created_at, cm.id
        LIMIT 1
    ), CASE
        WHEN md.provider_id IN ('openai', 'moonshot', 'deepseek', 'zhipu', 'kimi') THEN 'openai-compatible'
        WHEN md.provider_id = 'anthropic' THEN 'anthropic'
        WHEN md.provider_id = 'minimax' THEN 'minimax'
        ELSE 'openai-compatible'
    END),
    connection_label = (
        SELECT c.label
        FROM chat_turns ct
        JOIN chat_sessions cs ON cs.id = ct.session_id
        JOIN configured_models cm
          ON cm.user_id = cs.user_id
         AND cm.model_id = pr.model_id
        JOIN connections c ON c.id = cm.connection_id
        WHERE ct.id = pr.turn_id
        ORDER BY cm.created_at, cm.id
        LIMIT 1
    )
FROM model_definitions md
WHERE md.id = pr.model_id;

ALTER TABLE provider_responses
    ALTER COLUMN configured_model_id SET NOT NULL,
    ALTER COLUMN model_display_name SET NOT NULL,
    ALTER COLUMN protocol SET NOT NULL,
    ADD CONSTRAINT uq_response_turn_configured_model UNIQUE (turn_id, configured_model_id);

ALTER TABLE chat_turns
    ADD COLUMN selected_configured_model_ids UUID[] NOT NULL DEFAULT '{}';

UPDATE chat_turns ct
SET selected_configured_model_ids = COALESCE((
    SELECT ARRAY_AGG(cm.id ORDER BY selected.ordinality)
    FROM UNNEST(ct.selected_model_ids) WITH ORDINALITY AS selected(model_id, ordinality)
    JOIN chat_sessions cs ON cs.id = ct.session_id
    JOIN LATERAL (
        SELECT candidate.id
        FROM configured_models candidate
        WHERE candidate.user_id = cs.user_id
          AND candidate.model_id = selected.model_id
        ORDER BY candidate.created_at, candidate.id
        LIMIT 1
    ) cm ON TRUE
), '{}');

ALTER TABLE user_preferences
    ADD COLUMN last_selected_configured_model_id UUID;

UPDATE user_preferences up
SET last_selected_configured_model_id = (
    SELECT cm.id
    FROM configured_models cm
    WHERE cm.user_id = up.user_id
      AND cm.model_id = up.last_selected_model_id
    ORDER BY cm.created_at, cm.id
    LIMIT 1
);

INSERT INTO configuration_migration_audit (
    migrated_connections,
    migrated_models,
    skipped_models_without_key,
    unmapped_providers
)
SELECT
    (SELECT COUNT(*) FROM connections),
    (SELECT COUNT(*) FROM configured_models),
    (SELECT COUNT(*) FROM user_model_configs WHERE provider_api_key_id IS NULL),
    (SELECT COUNT(*) FROM provider_api_keys
     WHERE provider_id NOT IN ('openai', 'moonshot', 'deepseek', 'zhipu', 'kimi', 'anthropic', 'minimax'));

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM provider_responses WHERE configured_model_id IS NULL OR protocol IS NULL) > 0 THEN
        RAISE EXCEPTION 'provider response snapshot validation failed';
    END IF;
    IF (SELECT COUNT(*) FROM configured_models cm
        JOIN connections c ON c.id = cm.connection_id
        WHERE cm.user_id <> c.user_id) > 0 THEN
        RAISE EXCEPTION 'configured model ownership validation failed';
    END IF;
END $$;

DROP TABLE user_model_configs;
DROP TABLE provider_api_keys;
DROP TABLE model_definitions;
