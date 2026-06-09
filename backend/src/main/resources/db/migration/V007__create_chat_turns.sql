CREATE TABLE chat_turns (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id        UUID        NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    sequence_num      INTEGER     NOT NULL,
    prompt_text       TEXT        NOT NULL,
    attachments       JSONB,
    selected_model_ids TEXT[]     NOT NULL,
    client_request_id VARCHAR(100),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_turn_sequence UNIQUE (session_id, sequence_num)
);

-- Partial unique index for idempotency key (only non-NULL values are indexed)
CREATE UNIQUE INDEX idx_chat_turns_client_request_id
    ON chat_turns(client_request_id)
    WHERE client_request_id IS NOT NULL;

CREATE INDEX idx_chat_turns_session_seq ON chat_turns(session_id, sequence_num);
