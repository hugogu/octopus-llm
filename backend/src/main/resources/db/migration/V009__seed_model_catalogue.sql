INSERT INTO model_definitions (id, provider_id, display_name, capability_matrix, is_active) VALUES

-- OpenAI
('gpt-4o-2024-11-20', 'openai', 'GPT-4o (Nov 2024)', '{
  "input_modalities": ["text", "image"],
  "output_modalities": ["text"],
  "context_length_tokens": 128000,
  "supports_streaming": true,
  "supports_function_calling": true,
  "supports_system_prompt": true,
  "supports_video_input": false
}', true),

('gpt-4o-mini-2024-07-18', 'openai', 'GPT-4o Mini', '{
  "input_modalities": ["text", "image"],
  "output_modalities": ["text"],
  "context_length_tokens": 128000,
  "supports_streaming": true,
  "supports_function_calling": true,
  "supports_system_prompt": true,
  "supports_video_input": false
}', true),

-- Anthropic
('claude-3-5-sonnet-20241022', 'anthropic', 'Claude 3.5 Sonnet', '{
  "input_modalities": ["text", "image"],
  "output_modalities": ["text"],
  "context_length_tokens": 200000,
  "supports_streaming": true,
  "supports_function_calling": true,
  "supports_system_prompt": true,
  "supports_video_input": false
}', true),

('claude-3-haiku-20240307', 'anthropic', 'Claude 3 Haiku', '{
  "input_modalities": ["text", "image"],
  "output_modalities": ["text"],
  "context_length_tokens": 200000,
  "supports_streaming": true,
  "supports_function_calling": true,
  "supports_system_prompt": true,
  "supports_video_input": false
}', true),

-- Moonshot (Kimi) — OpenAI-compatible API
('moonshot-v1-8k', 'moonshot', 'Moonshot v1 (8k)', '{
  "input_modalities": ["text"],
  "output_modalities": ["text"],
  "context_length_tokens": 8192,
  "supports_streaming": true,
  "supports_function_calling": false,
  "supports_system_prompt": true,
  "supports_video_input": false
}', true),

('moonshot-v1-32k', 'moonshot', 'Moonshot v1 (32k)', '{
  "input_modalities": ["text"],
  "output_modalities": ["text"],
  "context_length_tokens": 32768,
  "supports_streaming": true,
  "supports_function_calling": false,
  "supports_system_prompt": true,
  "supports_video_input": false
}', true),

-- DeepSeek — OpenAI-compatible API
('deepseek-chat', 'deepseek', 'DeepSeek Chat', '{
  "input_modalities": ["text"],
  "output_modalities": ["text"],
  "context_length_tokens": 65536,
  "supports_streaming": true,
  "supports_function_calling": true,
  "supports_system_prompt": true,
  "supports_video_input": false
}', true),

-- Zhipu AI (GLM)
('glm-4-plus', 'zhipu', 'GLM-4 Plus', '{
  "input_modalities": ["text", "image"],
  "output_modalities": ["text"],
  "context_length_tokens": 128000,
  "supports_streaming": true,
  "supports_function_calling": true,
  "supports_system_prompt": true,
  "supports_video_input": false
}', true),

('glm-4-flash', 'zhipu', 'GLM-4 Flash', '{
  "input_modalities": ["text"],
  "output_modalities": ["text"],
  "context_length_tokens": 128000,
  "supports_streaming": true,
  "supports_function_calling": false,
  "supports_system_prompt": true,
  "supports_video_input": false
}', true),

-- MiniMax
('abab6.5s-chat', 'minimax', 'MiniMax ABAB 6.5s', '{
  "input_modalities": ["text"],
  "output_modalities": ["text"],
  "context_length_tokens": 245760,
  "supports_streaming": true,
  "supports_function_calling": false,
  "supports_system_prompt": true,
  "supports_video_input": false
}', true);
