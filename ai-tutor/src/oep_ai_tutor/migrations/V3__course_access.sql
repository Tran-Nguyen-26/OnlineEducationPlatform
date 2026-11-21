-- M2 is authoritative for publication. A versioned private signal suppresses
-- all M7 student access immediately after M2 commits a non-published status.
CREATE TABLE ai_tutor.ai_course_access (
    course_id uuid PRIMARY KEY,
    course_version bigint NOT NULL,
    active boolean NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ai_course_access_version CHECK (course_version >= 0)
);
