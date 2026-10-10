package com.oep.coremodel.entity.quiz;

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
@Table(name = "quiz_answers", uniqueConstraints = {@UniqueConstraint(name = "uq_quiz_answers_attempt_id_question_id", columnNames = {"attempt_id", "question_id"})})
public class QuizAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_option_data", nullable = true, columnDefinition = "jsonb")
    private String selectedOptionData;

    @Column(name = "text_answer", nullable = true, columnDefinition = "text")
    private String textAnswer;

    @Column(name = "is_correct", nullable = true)
    private Boolean correct;

    @Column(name = "awarded_score", nullable = true, precision = 5, scale = 2)
    private BigDecimal awardedScore;
}
