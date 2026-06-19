-- Feature 008: share audience scope. 'authenticated' = visible only to logged-in users (the default
-- for new shares, the more private option); 'public' = anyone with the opaque token (prior behaviour).
ALTER TABLE session_shares
    ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'authenticated'
        CHECK (scope IN ('authenticated', 'public'));

-- Preserve the behaviour of links already handed out before scopes existed: they were public.
UPDATE session_shares SET scope = 'public';
