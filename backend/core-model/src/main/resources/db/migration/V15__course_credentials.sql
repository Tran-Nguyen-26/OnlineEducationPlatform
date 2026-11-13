CREATE TABLE IF NOT EXISTS course_credentials (
    id uuid PRIMARY KEY,
    enrollment_id uuid NOT NULL UNIQUE REFERENCES enrollments(id),
    signed_credential text NOT NULL
);
