ALTER TABLE assignment_submissions
    ADD COLUMN IF NOT EXISTS viva_questions jsonb,
    ADD COLUMN IF NOT EXISTS viva_answers jsonb,
    ADD COLUMN IF NOT EXISTS viva_consistency_score numeric(5,2),
    ADD COLUMN IF NOT EXISTS viva_consistency_note text,
    ADD COLUMN IF NOT EXISTS viva_completed_at timestamptz;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_assignment_viva_consistency_score'
    ) THEN
        ALTER TABLE assignment_submissions
            ADD CONSTRAINT chk_assignment_viva_consistency_score
            CHECK (viva_consistency_score IS NULL OR
                   (viva_consistency_score >= 0 AND viva_consistency_score <= 100));
    END IF;
END
$$;
