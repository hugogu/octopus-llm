ALTER TABLE users
    ADD COLUMN display_name VARCHAR(255);

CREATE TABLE auth_action_throttles (
    action            VARCHAR(50) NOT NULL,
    key_hash          VARCHAR(64) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count     INTEGER NOT NULL DEFAULT 1,
    expires_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_auth_action_throttles PRIMARY KEY (action, key_hash, window_started_at),
    CONSTRAINT ck_auth_action_throttles_count CHECK (request_count >= 0)
);

CREATE INDEX idx_auth_action_throttles_expiry
    ON auth_action_throttles(expires_at);
