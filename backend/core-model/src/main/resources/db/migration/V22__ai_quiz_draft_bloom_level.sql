BEGIN;

ALTER TABLE questions ADD COLUMN IF NOT EXISTS bloom_level varchar(16);
ALTER TABLE questions ADD COLUMN IF NOT EXISTS source_lesson_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_questions_bloom_level') THEN
        ALTER TABLE questions ADD CONSTRAINT ck_questions_bloom_level
            CHECK (bloom_level IS NULL OR bloom_level IN ('REMEMBER', 'UNDERSTAND', 'APPLY'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_questions_source_lesson') THEN
        ALTER TABLE questions ADD CONSTRAINT fk_questions_source_lesson
            FOREIGN KEY (source_lesson_id) REFERENCES lessons(id);
    END IF;
END $$;

COMMIT;
