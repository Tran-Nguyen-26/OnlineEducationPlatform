ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS exam_starts_at timestamptz;
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS exam_ends_at timestamptz;
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS late_entry_minutes integer;
ALTER TABLE quiz_attempts ADD COLUMN IF NOT EXISTS exam_cutoff_at timestamptz;
ALTER TABLE quiz_attempts ADD COLUMN IF NOT EXISTS exam_device_id uuid;
ALTER TABLE quiz_attempts ADD COLUMN IF NOT EXISTS exam_draft jsonb;
ALTER TABLE quiz_attempts ADD COLUMN IF NOT EXISTS exam_draft_sequence bigint;
CREATE INDEX IF NOT EXISTS idx_exam_attempt_finalize ON quiz_attempts (exam_cutoff_at) WHERE status = 'IN_PROGRESS';
