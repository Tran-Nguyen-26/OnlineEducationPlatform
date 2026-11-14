BEGIN;

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS ai_document_chunks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    lesson_id uuid NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    content_id uuid NOT NULL REFERENCES lesson_contents(id) ON DELETE CASCADE,
    chunk_index integer NOT NULL,
    chunk_text text NOT NULL,
    token_count integer NOT NULL DEFAULT 0,
    embedding_vector text,
    embedding vector(1536),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_document_chunks_content_index UNIQUE (content_id, chunk_index)
);

ALTER TABLE ai_document_chunks
    ADD COLUMN IF NOT EXISTS embedding vector(1536);

CREATE INDEX IF NOT EXISTS idx_ai_document_chunks_course_lesson
    ON ai_document_chunks (course_id, lesson_id);

CREATE INDEX IF NOT EXISTS idx_ai_document_chunks_embedding_hnsw
    ON ai_document_chunks USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

COMMIT;
