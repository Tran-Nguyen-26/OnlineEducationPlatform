ALTER TABLE lesson_progress ADD COLUMN IF NOT EXISTS video_position_seconds integer NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS heartbeat_flush_batches (
    id uuid PRIMARY KEY,
    flushed_at timestamptz NOT NULL,
    progress_rows bigint NOT NULL,
    heartbeat_count bigint NOT NULL
);
