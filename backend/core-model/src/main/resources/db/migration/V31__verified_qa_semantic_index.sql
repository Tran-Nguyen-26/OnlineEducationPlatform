BEGIN;

CREATE TABLE IF NOT EXISTS ai_verified_qa_sources (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    lesson_id uuid NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    question_id uuid NOT NULL UNIQUE REFERENCES lesson_questions(id) ON DELETE CASCADE,
    reply_id uuid NOT NULL REFERENCES lesson_replies(id) ON DELETE CASCADE,
    question_text text NOT NULL,
    answer_text text NOT NULL,
    embedding vector(1536),
    verified_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_verified_qa_lesson ON ai_verified_qa_sources(course_id, lesson_id, verified_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_verified_qa_embedding_hnsw
    ON ai_verified_qa_sources USING hnsw (embedding vector_cosine_ops) WHERE embedding IS NOT NULL;

COMMIT;
