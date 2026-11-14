CREATE TABLE IF NOT EXISTS video_rewind_heatmap (
    course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    lesson_id uuid NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    video_id uuid NOT NULL REFERENCES lesson_contents(id) ON DELETE CASCADE,
    bucket_start_sec integer NOT NULL CHECK (bucket_start_sec >= 0),
    rewind_count bigint NOT NULL DEFAULT 0 CHECK (rewind_count >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (course_id, lesson_id, video_id, bucket_start_sec)
);
