package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.QuestionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A reusable question in a course's bank; usedInQuizzes > 0 blocks deletion. */
public record BankQuestionResponse(UUID questionId, QuestionType questionType, String questionText, String keywordAnswer,
                                   List<OptionResponse> options, long usedInQuizzes, Instant createdAt) {}
