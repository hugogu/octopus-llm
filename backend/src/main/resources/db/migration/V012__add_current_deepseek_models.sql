INSERT INTO model_definitions (id, provider_id, display_name, capability_matrix, is_active)
VALUES
(
  'deepseek-v4-flash',
  'deepseek',
  'DeepSeek V4 Flash',
  '{
    "input_modalities": ["text"],
    "output_modalities": ["text"],
    "context_length_tokens": 65536,
    "supports_streaming": true,
    "supports_function_calling": true,
    "supports_system_prompt": true,
    "supports_video_input": false
  }',
  true
),
(
  'deepseek-v4-pro',
  'deepseek',
  'DeepSeek V4 Pro',
  '{
    "input_modalities": ["text"],
    "output_modalities": ["text"],
    "context_length_tokens": 65536,
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
