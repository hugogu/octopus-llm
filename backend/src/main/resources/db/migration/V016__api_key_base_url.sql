-- Optional per-key base URL override, for providers reachable at multiple
-- endpoints (e.g. Kimi keys work against api.kimi.com, not api.moonshot.cn)
ALTER TABLE provider_api_keys
    ADD COLUMN base_url VARCHAR(500);
