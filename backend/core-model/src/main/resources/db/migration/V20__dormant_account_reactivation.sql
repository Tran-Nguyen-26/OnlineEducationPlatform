BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at timestamptz;
-- Login history was not stored previously; updated_at is the safest available legacy activity baseline.
UPDATE users SET last_login_at = updated_at WHERE last_login_at IS NULL;

COMMIT;
