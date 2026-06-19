-- Feature 008: display-only provenance for Quests created via import. Ownership stays user_id =
-- importer (admin for artifact import, user for share import); these columns just let the UI show
-- "Imported from ..." without transferring ownership.
ALTER TABLE chat_sessions
    ADD COLUMN imported_from_label VARCHAR(255),
    ADD COLUMN imported_at         TIMESTAMPTZ;
