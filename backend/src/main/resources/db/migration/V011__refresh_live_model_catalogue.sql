INSERT INTO model_definitions (id, provider_id, display_name, capability_matrix, is_active)
VALUES
(
  'deepseek-reasoner',
  'deepseek',
  'DeepSeek Reasoner',
  '{
    "input_modalities": ["text"],
    "output_modalities": ["text"],
    "context_length_tokens": 65536,
    "supports_streaming": true,
    "supports_function_calling": false,
    "supports_system_prompt": true,
    "supports_video_input": false
  }',
  true
),
(
  'glm-4.5-air',
  'zhipu',
  'GLM 4.5 Air',
  '{
    "input_modalities": ["text"],
    "output_modalities": ["text"],
    "context_length_tokens": 128000,
    "supports_streaming": true,
    "supports_function_calling": true,
    "supports_system_prompt": true,
    "supports_video_input": false
  }',
  true
)
ON CONFLICT (id) DO UPDATE
SET provider_id = EXCLUDED.provider_id,
    display_name = EXCLUDED.display_name,
    capability_matrix = EXCLUDED.capability_matrix,
    is_active = EXCLUDED.is_active;
