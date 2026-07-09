-- Platform-wide site-info shown in the public footer (e.g. ICP record, public-security record).
-- Single mutable row (id = 1) editable from the admin panel. Empty fields render nothing in the
-- footer; only the public endpoint at `/api/v2/site-settings` is allowed to read these values.
CREATE TABLE site_settings (
    id                       SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    site_name                TEXT,
    footer_text              TEXT,
    icp_record_no            TEXT,
    police_record_no         TEXT,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by               UUID REFERENCES users(id) ON DELETE SET NULL
);

INSERT INTO site_settings (id) VALUES (1);
