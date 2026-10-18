package com.oep.coremodel.entity.course;

import com.oep.coremodel.enums.VideoJobStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Theo dõi trạng thái pipeline transcode video độc lập với lesson_contents.
 * Lifecycle: QUEUED → DOWNLOADING → PROBING → TRANSCODING → UPLOADING → DONE/FAILED
 */
@Getter
@Setter
@Entity
@Table(name = "video_processing_jobs",
       indexes = {
           @Index(name = "idx_vpj_content_id", columnList = "content_id"),
           @Index(name = "idx_vpj_status", columnList = "status")
       })
public class VideoProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private LessonContent lessonContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private VideoJobStatus status = VideoJobStatus.QUEUED;

    @Column(name = "progress_pct", nullable = false)
    private int progressPct = 0;

    /**
     * JSON array các rendition đã upload xong, ví dụ: ["360p", "480p"].
     * Dùng để resume nếu worker bị gián đoạn (future).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "renditions_done", columnDefinition = "jsonb")
    private List<String> renditionsDone;

    /** Object key trong bucket oep-raw */
    @Column(name = "raw_object_key", nullable = true, length = 500)
    private String rawObjectKey;

    @Column(name = "error_message", nullable = true, columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at", nullable = true)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = true)
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
