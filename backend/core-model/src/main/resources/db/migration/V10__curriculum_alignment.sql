BEGIN;
CREATE TABLE IF NOT EXISTS learning_outcomes (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses(id),
    code varchar(40) NOT NULL,
    description varchar(500) NOT NULL,
    bloom_level varchar(16) NOT NULL CHECK (bloom_level IN ('REMEMBER', 'UNDERSTAND', 'APPLY')),
    UNIQUE (course_id, code)
);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS weight_posttest integer CHECK (weight_posttest BETWEEN 0 AND 100);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS weight_massexam integer CHECK (weight_massexam BETWEEN 0 AND 100);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS weight_assignment integer CHECK (weight_assignment BETWEEN 0 AND 100);
ALTER TABLE lesson_contents ADD COLUMN IF NOT EXISTS lo_id uuid REFERENCES learning_outcomes(id);
ALTER TABLE questions ADD COLUMN IF NOT EXISTS lo_id uuid REFERENCES learning_outcomes(id);
ALTER TABLE questions ADD COLUMN IF NOT EXISTS approval_state varchar(16);
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS assessment_kind varchar(16);
COMMIT;
