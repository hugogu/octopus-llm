-- Anonymous model access, request leases, conversation import identities, and bulk-operation history.

ALTER TABLE configured_models
    ADD COLUMN is_anonymous_allowed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_configured_models_anonymous_access
    ON configured_models (connection_id, is_enabled, is_anonymous_allowed, sort_order, created_at, id);

CREATE TABLE anonymous_request_leases (
    client_key_hash VARCHAR(64)  NOT NULL,
    slot_no         SMALLINT     NOT NULL,
    lease_id        UUID         NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_anonymous_request_leases PRIMARY KEY (client_key_hash, slot_no),
    CONSTRAINT ck_anonymous_request_leases_slot CHECK (slot_no >= 0)
);

CREATE INDEX idx_anonymous_request_leases_expiry
    ON anonymous_request_leases (expires_at);

CREATE TABLE anonymous_conversation_imports (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_conversation_id UUID         NOT NULL,
    session_id             UUID         REFERENCES chat_sessions(id) ON DELETE CASCADE,
    source_digest          VARCHAR(64)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    last_error             VARCHAR(1000),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    synced_at              TIMESTAMPTZ,
    CONSTRAINT uq_anonymous_conversation_import_identity
        UNIQUE (user_id, source_conversation_id),
    CONSTRAINT uq_anonymous_conversation_import_session
        UNIQUE (session_id),
    CONSTRAINT ck_anonymous_conversation_import_status
        CHECK (status IN ('IMPORTED', 'SKIPPED', 'FAILED'))
);

CREATE INDEX idx_anonymous_conversation_imports_user
    ON anonymous_conversation_imports (user_id, created_at DESC);

CREATE TABLE admin_model_bulk_operations (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id     UUID         NOT NULL REFERENCES users(id),
    action            VARCHAR(30)  NOT NULL,
    selection_mode    VARCHAR(20)  NOT NULL,
    selection_filter  JSONB        NOT NULL DEFAULT '{}',
    status            VARCHAR(20)  NOT NULL,
    target_count      INTEGER      NOT NULL DEFAULT 0,
    processed_count   INTEGER      NOT NULL DEFAULT 0,
    success_count     INTEGER      NOT NULL DEFAULT 0,
    failure_count     INTEGER      NOT NULL DEFAULT 0,
    changed_count     INTEGER      NOT NULL DEFAULT 0,
    already_satisfied_count INTEGER NOT NULL DEFAULT 0,
    idempotency_key_hash VARCHAR(64),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ  NOT NULL DEFAULT (NOW() + INTERVAL '15 minutes'),
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    CONSTRAINT ck_admin_model_bulk_action
        CHECK (action IN ('ALLOW_ANONYMOUS', 'REVOKE_ANONYMOUS', 'SHOW', 'HIDE', 'DELETE')),
    CONSTRAINT ck_admin_model_bulk_selection_mode
        CHECK (selection_mode IN ('IDS', 'FILTER')),
    CONSTRAINT ck_admin_model_bulk_status
        CHECK (status IN ('PREVIEWED', 'RUNNING', 'COMPLETED', 'PARTIAL_FAILURE', 'FAILED')),
    CONSTRAINT ck_admin_model_bulk_counts
        CHECK (
            target_count >= 0 AND processed_count >= 0 AND success_count >= 0 AND failure_count >= 0
            AND changed_count >= 0 AND already_satisfied_count >= 0
            AND processed_count <= target_count
            AND success_count + failure_count <= processed_count
            AND changed_count + already_satisfied_count <= success_count
        )
);

CREATE INDEX idx_admin_model_bulk_operations_admin_created
    ON admin_model_bulk_operations (admin_user_id, created_at DESC);

CREATE TABLE admin_model_bulk_operation_items (
    operation_id              UUID         NOT NULL REFERENCES admin_model_bulk_operations(id) ON DELETE CASCADE,
    configured_model_id       UUID         NOT NULL,
    model_id_snapshot         VARCHAR(255) NOT NULL,
    display_name_snapshot     VARCHAR(255) NOT NULL,
    connection_label_snapshot VARCHAR(255),
    previous_is_enabled       BOOLEAN,
    previous_is_anonymous_allowed BOOLEAN,
    outcome                   VARCHAR(24)  NOT NULL,
    error_code                VARCHAR(100),
    error_message             VARCHAR(1000),
    processed_at              TIMESTAMPTZ,
    CONSTRAINT pk_admin_model_bulk_operation_items PRIMARY KEY (operation_id, configured_model_id),
    CONSTRAINT ck_admin_model_bulk_item_status
        CHECK (outcome IN ('PENDING', 'CHANGED', 'ALREADY_SATISFIED', 'ALREADY_DELETED', 'FAILED'))
);

CREATE INDEX idx_admin_model_bulk_operation_items_status
    ON admin_model_bulk_operation_items (operation_id, outcome);

ALTER TABLE admin_audit_log DROP CONSTRAINT IF EXISTS ck_admin_audit_action;
ALTER TABLE admin_audit_log
    ADD CONSTRAINT ck_admin_audit_action CHECK (action IN (
        'ACTIVATE', 'DEACTIVATE', 'DISABLE', 'ENABLE', 'RESET_PASSWORD', 'DELETE_USER',
        'BUILTIN_CONNECTION_CREATE', 'BUILTIN_CONNECTION_UPDATE', 'BUILTIN_CONNECTION_DELETE',
        'ALLOCATE', 'REVOKE',
        'MODEL_ANONYMOUS_ALLOW', 'MODEL_ANONYMOUS_REVOKE', 'MODEL_SHOW', 'MODEL_HIDE',
        'MODEL_DELETE', 'MODEL_BULK_OPERATION'
    ));

ALTER TABLE admin_audit_log DROP CONSTRAINT IF EXISTS ck_admin_audit_target;
ALTER TABLE admin_audit_log
    ADD CONSTRAINT ck_admin_audit_target CHECK (target_type IN (
        'USER', 'CONNECTION', 'MODEL', 'MODEL_BULK_OPERATION'
    ));
