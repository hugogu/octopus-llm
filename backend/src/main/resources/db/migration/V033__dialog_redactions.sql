-- Feature 008: per-Dialog deletion as append-only redaction markers. A 'turn' marker hides a whole
-- turn (the user-prompt Dialog and its responses); a 'response' marker hides a single provider
-- response (one model-answer Dialog). This never mutates the immutable chat_turns / provider_responses
-- (Constitution IV); aggregate analytics ignore this table entirely (Constitution V).
CREATE TABLE dialog_redactions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope       VARCHAR(16) NOT NULL CHECK (scope IN ('turn', 'response')),
    turn_id     UUID NOT NULL REFERENCES chat_turns(id) ON DELETE CASCADE,
    response_id UUID REFERENCES provider_responses(id) ON DELETE CASCADE,
    -- Nullable + ON DELETE SET NULL: the marker survives if the deleting user is later removed.
    redacted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    redacted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((scope = 'turn' AND response_id IS NULL)
        OR (scope = 'response' AND response_id IS NOT NULL))
);

-- Idempotent re-delete: at most one marker per turn / per response.
CREATE UNIQUE INDEX uq_dialog_redactions_turn
    ON dialog_redactions(turn_id) WHERE scope = 'turn';
CREATE UNIQUE INDEX uq_dialog_redactions_response
    ON dialog_redactions(response_id) WHERE scope = 'response';
-- Read-time filtering loads all redactions for a session's turns.
CREATE INDEX idx_dialog_redactions_turn ON dialog_redactions(turn_id);
