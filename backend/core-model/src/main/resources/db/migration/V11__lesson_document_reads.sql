CREATE TABLE IF NOT EXISTS lesson_document_reads (
    enrollment_id uuid NOT NULL REFERENCES enrollments(id),
    content_id uuid NOT NULL REFERENCES lesson_contents(id) ON DELETE CASCADE,
    read_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (enrollment_id, content_id)
);
