package com.oep.coremodel.entity.assignment;

import com.oep.coremodel.entity.BaseEntity;
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
@Table(name = "submission_files")
public class SubmissionFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private AssignmentSubmission submission;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", nullable = true, length = 100)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 40)
    private UploadStatus uploadStatus = UploadStatus.PENDING;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();
}
