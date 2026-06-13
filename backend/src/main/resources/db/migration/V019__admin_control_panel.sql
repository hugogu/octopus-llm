-- Admin control panel: account state, built-in connections, allocations, password resets, audit log.

-- Account state on users.
ALTER TABLE users ADD COLUMN is_admin      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN is_active     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN is_disabled   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN session_epoch INTEGER NOT NULL DEFAULT 0;

-- Supports the usable-admin count used by the last-admin guard.
CREATE INDEX idx_users_enabled_admins ON users (id) WHERE is_admin AND NOT is_disabled;

-- Supports deterministic, paginated listing (SC-006).
CREATE INDEX idx_users_created ON users (created_at, id);

-- Built-in connections are platform-owned connections exposed read-only to allocated users.
ALTER TABLE connections ADD COLUMN is_builtin BOOLEAN NOT NULL DEFAULT FALSE;

-- Allocation of a built-in connection to an activated user (many users per connection).
CREATE TABLE connection_allocations (
    connection_id UUID        NOT NULL REFERENCES connections(id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users(id)       ON DELETE CASCADE,
    allocated_by  UUID        NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_connection_allocations PRIMARY KEY (connection_id, user_id)
);

CREATE INDEX idx_allocations_user ON connection_allocations (user_id);

-- Single-use password-reset tokens issued by an admin reset.
CREATE TABLE password_resets (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_resets_user ON password_resets (user_id);

-- Append-only audit trail of administrative actions (never contains secrets).
CREATE TABLE admin_audit_log (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id UUID         NOT NULL REFERENCES users(id),
    action        VARCHAR(50)  NOT NULL,
    target_type   VARCHAR(20)  NOT NULL,
    target_id     UUID         NOT NULL,
    metadata      JSONB        NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_admin_audit_action CHECK (action IN (
        'ACTIVATE', 'DISABLE', 'ENABLE', 'RESET_PASSWORD',
        'BUILTIN_CONNECTION_CREATE', 'BUILTIN_CONNECTION_UPDATE', 'BUILTIN_CONNECTION_DELETE',
        'ALLOCATE', 'REVOKE'
    )),
    CONSTRAINT ck_admin_audit_target CHECK (target_type IN ('USER', 'CONNECTION'))
);

CREATE INDEX idx_audit_created ON admin_audit_log (created_at DESC);
