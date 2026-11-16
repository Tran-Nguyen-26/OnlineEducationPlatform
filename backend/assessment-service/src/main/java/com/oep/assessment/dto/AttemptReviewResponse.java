package com.oep.assessment.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A student's own essay answers of one attempt with the instructor's score and comment (once graded). */
public record AttemptReviewResponse(UUID attemptId, int attemptNumber, List<EssayReview> essays) {

    public record EssayReview(String questionText, String textAnswer, BigDecimal maxPoints, BigDecimal awardedScore, String feedback, boolean graded) {}
}
