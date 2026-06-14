-- Cache-token capture (feature 006, clarification Q3). Two normalized columns added to the immutable,
-- INSERT-once provider_responses table. Nullable + forward-only: pre-existing rows stay NULL and render
-- as "—"; no backfill/UPDATE (Constitution IV).
ALTER TABLE provider_responses
    ADD COLUMN cache_read_tokens  INTEGER,
    ADD COLUMN cache_write_tokens INTEGER,
    ADD CONSTRAINT ck_provider_responses_cache_read
        CHECK (cache_read_tokens IS NULL OR cache_read_tokens >= 0),
    ADD CONSTRAINT ck_provider_responses_cache_write
        CHECK (cache_write_tokens IS NULL OR cache_write_tokens >= 0);
