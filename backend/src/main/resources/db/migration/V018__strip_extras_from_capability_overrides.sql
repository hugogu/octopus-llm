UPDATE configured_models
SET capability_overrides = capability_overrides - 'extras'
WHERE capability_overrides ? 'extras';
