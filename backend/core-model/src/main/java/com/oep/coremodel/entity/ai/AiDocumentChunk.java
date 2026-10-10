package com.oep.coremodel.entity.ai;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import com.oep.coremodel.enums.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ai_document_chunks", indexes = {@Index(name = "idx_ai_document_chunks_course_id_lesson_id", columnList = "course_id, lesson_id")})
public class AiDocumentChunk extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private LessonContent content;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "text")
    private String chunkText;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "embedding_vector", nullable = true, columnDefinition = "text")
    private String embeddingVector;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
