ALTER TABLE chat_turns
    ADD COLUMN client_ip INET;

ALTER TABLE provider_responses
    ADD COLUMN connection_id UUID,
    ADD COLUMN input_price_per_mtok NUMERIC(12,4),
    ADD COLUMN output_price_per_mtok NUMERIC(12,4),
    ADD COLUMN price_currency VARCHAR(3),
    ADD CONSTRAINT ck_provider_responses_input_price
        CHECK (input_price_per_mtok IS NULL OR input_price_per_mtok >= 0),
    ADD CONSTRAINT ck_provider_responses_output_price
        CHECK (output_price_per_mtok IS NULL OR output_price_per_mtok >= 0),
    ADD CONSTRAINT ck_provider_responses_price_currency
        CHECK (
            price_currency IS NULL
            OR price_currency ~ '^[A-Z]{3}$'
        );

CREATE INDEX idx_provider_responses_created
    ON provider_responses(created_at, id);
CREATE INDEX idx_provider_responses_configured_model_created
    ON provider_responses(configured_model_id, created_at, id);
CREATE INDEX idx_provider_responses_protocol_model_created
    ON provider_responses(protocol, model_id, created_at, id);
