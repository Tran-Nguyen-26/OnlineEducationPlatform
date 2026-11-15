package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.quiz.QuestionOption;

import java.util.UUID;

public record OptionResponse(UUID id, String optionText, boolean correct, int position) {
    public static OptionResponse from(QuestionOption option) {
        return new OptionResponse(option.getId(), option.getOptionText(), option.isCorrect(), option.getPosition());
    }
}
