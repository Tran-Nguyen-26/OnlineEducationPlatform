BEGIN;

CREATE TABLE IF NOT EXISTS ai_mistake_explanation_cache (
    question_id uuid NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    selected_option_id uuid NOT NULL REFERENCES question_options(id) ON DELETE CASCADE,
    prompt_version varchar(32) NOT NULL,
    explanation text NOT NULL,
    prompt_tokens integer NOT NULL DEFAULT 0,
    completion_tokens integer NOT NULL DEFAULT 0,
    latency_ms integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_ai_mistake_explanation_cache PRIMARY KEY (question_id, selected_option_id, prompt_version)
);

COMMIT;
