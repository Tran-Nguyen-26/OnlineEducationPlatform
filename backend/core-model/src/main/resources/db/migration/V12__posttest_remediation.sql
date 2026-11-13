ALTER TABLE quiz_attempts ADD COLUMN IF NOT EXISTS is_remediation boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS uq_quiz_attempt_remediation
    ON quiz_attempts (quiz_id, enrollment_id) WHERE is_remediation;
