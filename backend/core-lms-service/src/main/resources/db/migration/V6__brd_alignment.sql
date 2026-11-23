-- BRD v1.3 alignment (non-AI): blind grading, optimistic lock when grading, consent, limited-use invite codes.
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS blind_grading boolean NOT NULL DEFAULT true;
ALTER TABLE assignment_submissions ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS consent_at timestamptz;
ALTER TABLE course_invite_codes ADD COLUMN IF NOT EXISTS max_uses integer;
ALTER TABLE course_invite_codes ADD COLUMN IF NOT EXISTS used_count integer NOT NULL DEFAULT 0;
