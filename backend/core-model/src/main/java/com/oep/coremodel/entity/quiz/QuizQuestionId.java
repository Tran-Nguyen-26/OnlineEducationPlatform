package com.oep.coremodel.entity.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class QuizQuestionId implements Serializable {

    @Column(name = "quiz_id")
    private UUID quizId;

    @Column(name = "question_id")
    private UUID questionId;
}
