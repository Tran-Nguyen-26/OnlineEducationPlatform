package com.oep.coremodel.entity.course;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.identity.User;
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
@Table(name = "lesson_contents", indexes = {@Index(name = "idx_lesson_contents_lesson_id_position", columnList = "lesson_id, position")})
public class LessonContent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 40)
    private ContentType contentType;

    @Column(name = "title", nullable = true, length = 255)
    private String title;

    @Column(name = "text_content", nullable = true, columnDefinition = "text")
    private String textContent;

    @Column(name = "external_url", nullable = true, length = 1000)
    private String externalUrl;

    @Column(name = "object_key", nullable = true, length = 500)
    private String objectKey;

    @Column(name = "file_name", nullable = true, length = 255)
    private String fileName;

    @Column(name = "mime_type", nullable = true, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = true)
    private Long fileSize;

    @Column(name = "duration_seconds", nullable = true)
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 40)
    private UploadStatus uploadStatus = UploadStatus.PENDING;

    @Column(name = "position", nullable = false)
    private int position = 0;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "created_by", nullable = true)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "updated_by", nullable = true)
    private User updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
