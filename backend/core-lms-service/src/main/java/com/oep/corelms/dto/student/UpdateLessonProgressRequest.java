package com.oep.corelms.dto.student;

import com.oep.coremodel.enums.LessonProgressStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateLessonProgressRequest(@NotNull LessonProgressStatus status) {
}
