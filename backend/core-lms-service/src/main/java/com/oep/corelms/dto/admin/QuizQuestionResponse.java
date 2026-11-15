package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.quiz.Question;
import com.oep.coremodel.entity.quiz.QuizQuestion;
import com.oep.coremodel.enums.QuestionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuizQuestionResponse(
        UUID questionId,
        QuestionType questionType,
        String questionText,
        String keywordAnswer,
        int position,
        BigDecimal points,
        List<OptionResponse> options) {

    public static QuizQuestionResponse from(QuizQuestion link, List<OptionResponse> options) {
        Question question = link.getQuestion();
        return new QuizQuestionResponse(question.getId(), question.getQuestionType(), question.getQuestionText(),
                question.getKeywordAnswer(), link.getPosition(), link.getPoints(), options);
    }
}
