package com.oep.corelms.dto.ai;

import java.util.UUID;

public record MistakeExplanationResponse(UUID questionId, UUID optionId, boolean selectedOptionCorrect,
                                         String explanation, boolean cached) {}
