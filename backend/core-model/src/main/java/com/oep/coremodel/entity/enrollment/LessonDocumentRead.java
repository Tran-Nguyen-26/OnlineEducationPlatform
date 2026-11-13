package com.oep.coremodel.entity.enrollment;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_document_reads")
@IdClass(LessonDocumentRead.Key.class)
public class LessonDocumentRead {
    public record Key(UUID enrollmentId, UUID contentId) implements Serializable {}

    @Id
    @Column(name = "enrollment_id")
    private UUID enrollmentId;
    @Id
    @Column(name = "content_id")
    private UUID contentId;
    @Column(name = "read_at", nullable = false)
    private Instant readAt;
}
