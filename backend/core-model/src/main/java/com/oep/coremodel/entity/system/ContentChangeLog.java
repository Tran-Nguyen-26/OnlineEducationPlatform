package com.oep.coremodel.entity.system;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.course.LessonContent;
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
@Table(name = "content_change_logs")
public class ContentChangeLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private LessonContent content;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "changed_by", nullable = true)
    private User changedBy;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();
}
