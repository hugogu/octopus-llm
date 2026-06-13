CREATE TABLE session_shares (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    token      VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_session_shares_session
    ON session_shares(session_id, created_at DESC, id);
CREATE UNIQUE INDEX uq_session_shares_active
    ON session_shares(session_id)
    WHERE revoked_at IS NULL;
