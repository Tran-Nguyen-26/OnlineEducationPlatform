package com.oep.corelms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiEmbeddingIndexJob {
    private final JdbcTemplate jdbc;
    private final AiEmbeddingService embeddings;
    @Value("${oep.ai.embedding-index-enabled:true}")
    private boolean enabled;

    private record Chunk(UUID id, String text) {}

    @Scheduled(fixedDelayString = "${oep.ai.embedding-index-poll-ms:30000}")
    @Transactional
    public void indexPendingChunks() {
        if (!enabled || !embeddings.isConfigured()) return;
        List<Chunk> chunks = jdbc.query("""
                select d.id, d.chunk_text
                from ai_document_chunks d join lesson_contents c on c.id = d.content_id
                where d.embedding is null and c.upload_status = 'AVAILABLE'
                order by d.created_at, d.id
                limit 32
                for update of d skip locked
                """, (rs, row) -> new Chunk(rs.getObject(1, UUID.class), rs.getString(2)));
        if (chunks.isEmpty()) return;
        try {
            List<float[]> vectors = embeddings.embed(chunks.stream().map(Chunk::text).toList());
            for (int index = 0; index < chunks.size(); index++) {
                jdbc.update("update ai_document_chunks set embedding = cast(? as vector) where id = ? and embedding is null",
                        AiEmbeddingService.toPgVector(vectors.get(index)), chunks.get(index).id());
            }
        } catch (RuntimeException ex) {
            log.warn("AI document embedding batch failed; chunks will be retried: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${oep.ai.embedding-index-poll-ms:30000}")
    @Transactional
    public void indexPendingVerifiedAnswers() {
        if (!enabled || !embeddings.isConfigured()) return;
        List<Chunk> rows = jdbc.query("""
                select id, question_text || E'\\n' || answer_text
                from ai_verified_qa_sources
                where embedding is null
                order by verified_at, id
                limit 32
                for update skip locked
                """, (rs, row) -> new Chunk(rs.getObject(1, UUID.class), rs.getString(2)));
        if (rows.isEmpty()) return;
        try {
            List<float[]> vectors = embeddings.embed(rows.stream().map(Chunk::text).toList());
            for (int index = 0; index < rows.size(); index++) {
                jdbc.update("update ai_verified_qa_sources set embedding = cast(? as vector) where id = ? and embedding is null",
                        AiEmbeddingService.toPgVector(vectors.get(index)), rows.get(index).id());
            }
        } catch (RuntimeException ex) {
            log.warn("Verified Q&A embedding batch failed; rows will be retried: {}", ex.getMessage());
        }
    }
}
