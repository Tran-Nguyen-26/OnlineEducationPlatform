BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS consent_at timestamptz;
ALTER TABLE users ADD COLUMN IF NOT EXISTS consent_version varchar(64);

COMMIT;
