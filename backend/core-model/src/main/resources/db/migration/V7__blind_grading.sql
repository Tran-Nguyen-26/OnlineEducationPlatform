-- Apply after the existing assignment tables are provisioned by Hibernate.
ALTER TABLE assignment_submissions
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS claimed_by uuid,
    ADD COLUMN IF NOT EXISTS lock_token uuid,
    ADD COLUMN IF NOT EXISTS lock_expires_at timestamptz,
    ADD COLUMN IF NOT EXISTS appeal_criterion varchar(255),
    ADD COLUMN IF NOT EXISTS appeal_reason text;

ALTER TABLE assignments ADD COLUMN IF NOT EXISTS rubric jsonb;
ALTER TABLE assignment_grades
    ADD COLUMN IF NOT EXISTS rubric_scores jsonb,
    ADD COLUMN IF NOT EXISTS regrade_reviewer_id uuid;
