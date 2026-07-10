-- Feature 009: unified tool calling. Each unique tool execution within a turn is stored once;
-- a join table records which immutable provider_responses consumed it, so per-turn deduplication
-- across models keeps a single execution while preserving per-response lineage for analytics/replay.

CREATE TABLE tool_invocations (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    quest_id       UUID        NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    turn_id        UUID        NOT NULL REFERENCES chat_turns(id) ON DELETE CASCADE,
    tool_name      VARCHAR(64) NOT NULL,
    arguments_hash VARCHAR(64) NOT NULL,
    arguments      JSONB       NOT NULL,
    result         JSONB,
    error_message  TEXT,
    status         VARCHAR(16) NOT NULL
        CHECK (status IN ('pending', 'running', 'success', 'failed', 'timeout')),
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tool_invocation_dedup UNIQUE (quest_id, turn_id, tool_name, arguments_hash)
);

CREATE INDEX idx_tool_invocations_quest_turn ON tool_invocations(quest_id, turn_id);
CREATE INDEX idx_tool_invocations_status ON tool_invocations(status);

CREATE TABLE provider_response_tool_invocations (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_response_id UUID        NOT NULL REFERENCES provider_responses(id) ON DELETE CASCADE,
    tool_invocation_id   UUID        NOT NULL REFERENCES tool_invocations(id) ON DELETE CASCADE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pr_tool_invocation UNIQUE (provider_response_id, tool_invocation_id)
);

CREATE INDEX idx_pr_tool_invocations_response ON provider_response_tool_invocations(provider_response_id);
CREATE INDEX idx_pr_tool_invocations_invocation ON provider_response_tool_invocations(tool_invocation_id);
