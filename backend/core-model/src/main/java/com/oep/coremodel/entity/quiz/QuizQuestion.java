package com.oep.coremodel.entity.quiz;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    @EmbeddedId
    private QuizQuestionId id = new QuizQuestionId();

    @MapsId("quizId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @MapsId("questionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "position", nullable = false)
    private int position = 0;

    @Column(name = "points", nullable = false, precision = 5, scale = 2)
    private BigDecimal points = BigDecimal.ONE;
}
