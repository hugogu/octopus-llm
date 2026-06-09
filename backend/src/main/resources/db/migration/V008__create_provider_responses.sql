-- Rows are INSERT-once on stream completion (status='complete' or 'error').
-- No pending/streaming rows, no UPDATE statements — immutability per Constitution IV.
CREATE TABLE provider_responses (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    turn_id         UUID        NOT NULL REFERENCES chat_turns(id) ON DELETE CASCADE,
    model_id        VARCHAR(100) NOT NULL REFERENCES model_definitions(id),
    status          VARCHAR(50)  NOT NULL CHECK (status IN ('complete', 'error')),
    response_text   TEXT,
    error_message   TEXT,
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    latency_ms      INTEGER     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_response_turn_model UNIQUE (turn_id, model_id)
);

CREATE INDEX idx_provider_responses_turn ON provider_responses(turn_id);
