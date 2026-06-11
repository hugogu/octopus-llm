-- Reasoning / thinking content captured from providers that emit it
ALTER TABLE provider_responses
    ADD COLUMN reasoning_text TEXT;

-- Backfill session titles from the first prompt of each session
UPDATE chat_sessions s
SET title = left(t.prompt_text, 60)
FROM chat_turns t
WHERE t.session_id = s.id
  AND t.sequence_num = 1
  AND s.title IS NULL;
