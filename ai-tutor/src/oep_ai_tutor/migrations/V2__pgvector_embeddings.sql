-- The database image/role must support pgvector before applying this M7 migration.
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE ai_tutor.ai_source_chunks
    ADD COLUMN embedding vector;

ALTER TABLE ai_tutor.ai_source_chunks
    ADD CONSTRAINT ai_source_chunks_embedding_dimension
    CHECK (embedding IS NULL OR vector_dims(embedding) = embedding_dimension);

-- Initial Jina space: exact scoped search remains the correctness baseline;
-- this index is available when a measured approximate-query strategy is enabled.
CREATE INDEX ai_source_chunks_hnsw_1024
    ON ai_tutor.ai_source_chunks
    USING hnsw ((embedding::vector(1024)) vector_cosine_ops)
    WHERE embedding_dimension = 1024 AND embedding IS NOT NULL;
