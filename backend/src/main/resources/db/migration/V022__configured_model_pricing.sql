ALTER TABLE configured_models
    ADD COLUMN input_price_per_mtok NUMERIC(12,4),
    ADD COLUMN output_price_per_mtok NUMERIC(12,4),
    ADD COLUMN price_currency VARCHAR(3),
    ADD CONSTRAINT ck_configured_models_input_price
        CHECK (input_price_per_mtok IS NULL OR input_price_per_mtok >= 0),
    ADD CONSTRAINT ck_configured_models_output_price
        CHECK (output_price_per_mtok IS NULL OR output_price_per_mtok >= 0),
    ADD CONSTRAINT ck_configured_models_price_currency
        CHECK (
            (input_price_per_mtok IS NULL AND output_price_per_mtok IS NULL AND price_currency IS NULL)
            OR
            ((input_price_per_mtok IS NOT NULL OR output_price_per_mtok IS NOT NULL)
                AND price_currency ~ '^[A-Z]{3}$')
        );
