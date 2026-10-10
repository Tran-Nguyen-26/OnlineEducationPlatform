package com.oep.coremodel.entity.ai;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.course.Lesson;
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
@Table(name = "ai_conversations")
public class AiConversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "lesson_id", nullable = true)
    private Lesson lesson;

    @Column(name = "title", nullable = true, length = 255)
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
