BEGIN;

CREATE TABLE IF NOT EXISTS ai_usage_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid REFERENCES courses(id) ON DELETE SET NULL,
    operation varchar(40) NOT NULL,
    prompt_tokens integer NOT NULL DEFAULT 0,
    completion_tokens integer NOT NULL DEFAULT 0,
    latency_ms integer NOT NULL DEFAULT 0,
    is_refused boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_events_created_at
    ON ai_usage_events (created_at);
CREATE INDEX IF NOT EXISTS idx_ai_usage_events_course_created_at
    ON ai_usage_events (course_id, created_at);

COMMIT;
