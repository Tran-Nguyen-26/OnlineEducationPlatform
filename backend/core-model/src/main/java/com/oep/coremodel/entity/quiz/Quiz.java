package com.oep.coremodel.entity.quiz;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.Lesson;
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
@Table(name = "quizzes", indexes = {@Index(name = "idx_quizzes_course_id", columnList = "course_id"), @Index(name = "idx_quizzes_lesson_id", columnList = "lesson_id")})
public class Quiz extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "lesson_id", nullable = true)
    private Lesson lesson;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Column(name = "question_source", nullable = true, length = 50)
    private String questionSource = "FIXED";

    @Column(name = "time_limit_seconds", nullable = true)
    private Integer timeLimitSeconds;

    @Column(name = "deadline", nullable = true)
    private Instant deadline;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_policy", nullable = false, length = 40)
    private QuizGradingPolicy gradingPolicy = QuizGradingPolicy.HIGHEST;

    @Column(name = "randomize_questions", nullable = true)
    private Boolean randomizeQuestions = false;

    @Column(name = "randomize_answers", nullable = true)
    private Boolean randomizeAnswers = false;

    @Column(name = "show_score", nullable = true)
    private Boolean showScore = true;

    @Column(name = "show_correct_answers", nullable = true)
    private Boolean showCorrectAnswers = false;

    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

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
