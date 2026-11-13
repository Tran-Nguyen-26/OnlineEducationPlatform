BEGIN;

ALTER TABLE enrollments ADD COLUMN IF NOT EXISTS access_expires_at timestamptz;

CREATE TABLE IF NOT EXISTS access_codes (
    id uuid PRIMARY KEY,
    code_hash varchar(64) NOT NULL UNIQUE,
    batch_id uuid NOT NULL,
    course_id uuid NOT NULL REFERENCES courses(id),
    kind varchar(16) NOT NULL CHECK (kind IN ('ACTIVE', 'EXTENSION')),
    access_days integer NOT NULL CHECK (access_days IN (90, 180)),
    expires_at timestamptz NOT NULL,
    used_by uuid REFERENCES users(id),
    used_at timestamptz,
    revoked_at timestamptz,
    CHECK ((used_at IS NULL) = (used_by IS NULL)),
    CHECK (used_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_access_codes_batch ON access_codes(batch_id);

COMMIT;
