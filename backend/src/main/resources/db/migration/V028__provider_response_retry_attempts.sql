ALTER TABLE provider_responses
    ADD COLUMN attempt_number INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN retry_request_id VARCHAR(100);

ALTER TABLE provider_responses
    ADD CONSTRAINT ck_provider_responses_attempt_number
        CHECK (attempt_number > 0);

ALTER TABLE provider_responses
    DROP CONSTRAINT uq_response_turn_configured_model;

ALTER TABLE provider_responses
    ADD CONSTRAINT uq_response_turn_configured_model_attempt
        UNIQUE (turn_id, configured_model_id, attempt_number);

CREATE UNIQUE INDEX uq_provider_responses_retry_request_id
    ON provider_responses(retry_request_id)
    WHERE retry_request_id IS NOT NULL;
