CREATE TABLE response_likes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    response_id UUID NOT NULL REFERENCES provider_responses(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_response_like UNIQUE (response_id, user_id)
);

CREATE INDEX idx_response_likes_response
    ON response_likes(response_id);
