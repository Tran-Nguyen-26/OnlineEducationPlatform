package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.CourseLifecycleStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateCourseStatusRequest(@NotNull CourseLifecycleStatus status) {
}
