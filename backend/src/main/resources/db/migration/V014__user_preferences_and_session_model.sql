-- User preferences table
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_selected_model_id VARCHAR(255),
    theme_preference VARCHAR(50) DEFAULT 'system',
    sidebar_collapsed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_preferences_user UNIQUE (user_id)
);

CREATE INDEX idx_user_preferences_user ON user_preferences(user_id);

-- Add selected_model_id to chat_sessions
ALTER TABLE chat_sessions
    ADD COLUMN selected_model_id VARCHAR(255);

CREATE INDEX idx_chat_sessions_user_updated ON chat_sessions(user_id, updated_at DESC);
