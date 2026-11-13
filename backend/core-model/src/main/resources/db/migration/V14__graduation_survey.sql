ALTER TABLE courses ADD COLUMN IF NOT EXISTS graduation_threshold integer CHECK (graduation_threshold BETWEEN 0 AND 100);
CREATE TABLE IF NOT EXISTS course_surveys (
    enrollment_id uuid PRIMARY KEY REFERENCES enrollments(id),
    csat integer NOT NULL CHECK (csat BETWEEN 1 AND 5),
    nps integer NOT NULL CHECK (nps BETWEEN 0 AND 10),
    submitted_at timestamptz NOT NULL
);
