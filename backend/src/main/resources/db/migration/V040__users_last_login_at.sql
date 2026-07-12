-- Track when each user last logged in so the admin Users panel can show it.
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
