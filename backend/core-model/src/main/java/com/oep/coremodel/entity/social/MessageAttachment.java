package com.oep.coremodel.entity.social;

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
@Table(name = "message_attachments")
public class MessageAttachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "file_name", nullable = true, length = 255)
    private String fileName;

    @Column(name = "file_type", nullable = true, length = 100)
    private String fileType;

    @Column(name = "file_size", nullable = true)
    private Long fileSize;
}
