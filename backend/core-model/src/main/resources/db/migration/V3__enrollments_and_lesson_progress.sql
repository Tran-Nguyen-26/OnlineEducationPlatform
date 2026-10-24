-- M3 incremental PostgreSQL script, manually applied (Flyway is not enabled).
-- Prerequisites: users, courses, lessons and lesson_contents from the existing schema.
-- Run with psql -v ON_ERROR_STOP=1 -f <this file> on the PRIMARY database.
BEGIN;

CREATE TABLE IF NOT EXISTS enrollments (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses(id),
    student_id uuid NOT NULL REFERENCES users(id),
    status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    created_via varchar(50) DEFAULT 'DIRECT',
    enrolled_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    withdrawn_at timestamptz,
    removed_at timestamptz,
    CONSTRAINT uq_enrollments_course_id_student_id UNIQUE (course_id, student_id)
);

CREATE TABLE IF NOT EXISTS lesson_progress (
    id uuid PRIMARY KEY,
    enrollment_id uuid NOT NULL REFERENCES enrollments(id),
    lesson_id uuid NOT NULL REFERENCES lessons(id),
    status varchar(40) NOT NULL DEFAULT 'NOT_STARTED',
    time_spent_seconds integer NOT NULL DEFAULT 0,
    last_content_id uuid REFERENCES lesson_contents(id),
    started_at timestamptz,
    completed_at timestamptz,
    last_accessed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_lesson_progress_enrollment_id_lesson_id UNIQUE (enrollment_id, lesson_id)
);

-- Also enforce uniqueness when Hibernate created the tables first.
CREATE UNIQUE INDEX IF NOT EXISTS uq_enrollments_course_id_student_id ON enrollments(course_id, student_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lesson_progress_enrollment_id_lesson_id ON lesson_progress(enrollment_id, lesson_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_course_id_status ON enrollments(course_id, status);
CREATE INDEX IF NOT EXISTS idx_enrollments_student_id_status ON enrollments(student_id, status);
CREATE INDEX IF NOT EXISTS idx_lesson_progress_enrollment_id ON lesson_progress(enrollment_id);

COMMIT;
