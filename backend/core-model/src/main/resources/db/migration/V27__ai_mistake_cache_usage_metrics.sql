BEGIN;

CREATE TABLE IF NOT EXISTS ai_mistake_cache_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    selected_option_id uuid NOT NULL REFERENCES question_options(id) ON DELETE CASCADE,
    cache_hit boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_mistake_cache_events_created_at
    ON ai_mistake_cache_events (created_at);

COMMIT;
