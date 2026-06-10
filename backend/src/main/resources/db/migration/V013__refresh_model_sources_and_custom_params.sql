ALTER TABLE model_definitions
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'CATALOGUE';

ALTER TABLE user_model_configs
    ADD COLUMN IF NOT EXISTS custom_params JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE model_definitions
SET source = 'CATALOGUE'
WHERE source IS NULL;

UPDATE model_definitions
SET is_active = FALSE
WHERE id IN ('deepseek-chat', 'deepseek-reasoner');

INSERT INTO model_definitions (id, provider_id, display_name, capability_matrix, is_active, source)
VALUES
(
  'kimi-k2.5',
  'moonshot',
  'Kimi K2.5',
  '{
    "input_modalities": ["text", "image", "video"],
    "output_modalities": ["text"],
    "context_length_tokens": 256000,
    "supports_streaming": true,
    "supports_function_calling": true,
    "supports_system_prompt": true,
    "supports_video_input": true
  }',
  TRUE,
  'CATALOGUE'
),
(
  'kimi-k2.6',
  'moonshot',
  'Kimi K2.6',
  '{
    "input_modalities": ["text", "image", "video"],
    "output_modalities": ["text"],
    "context_length_tokens": 256000,
    "supports_streaming": true,
    "supports_function_calling": true,
    "supports_system_prompt": true,
    "supports_video_input": true
  }',
  TRUE,
  'CATALOGUE'
)
ON CONFLICT (id) DO UPDATE
SET provider_id = EXCLUDED.provider_id,
    display_name = EXCLUDED.display_name,
    capability_matrix = EXCLUDED.capability_matrix,
    is_active = EXCLUDED.is_active,
    source = EXCLUDED.source;
