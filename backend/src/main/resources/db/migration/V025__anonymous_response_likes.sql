CREATE TABLE anonymous_response_likes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    response_id      UUID NOT NULL REFERENCES provider_responses(id) ON DELETE CASCADE,
    visitor_key_hash VARCHAR(64) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_anon_response_like UNIQUE (response_id, visitor_key_hash)
);

CREATE INDEX idx_anon_response_likes_response
    ON anonymous_response_likes(response_id);
