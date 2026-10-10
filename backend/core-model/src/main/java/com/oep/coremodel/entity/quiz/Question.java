package com.oep.coremodel.entity.quiz;

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
@Table(name = "questions")
public class Question extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "question_bank_id", nullable = true)
    private QuestionBank questionBank;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 40)
    private QuestionType questionType = QuestionType.SINGLE_CHOICE;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "keyword_answer", nullable = true, length = 255)
    private String keywordAnswer;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "created_by", nullable = true)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
