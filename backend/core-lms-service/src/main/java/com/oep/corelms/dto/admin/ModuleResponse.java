package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.course.CourseModule;

import java.util.UUID;

public record ModuleResponse(UUID id, UUID courseId, String title, String description, int position, boolean isDefault) {
    public static ModuleResponse from(CourseModule module) {
        return new ModuleResponse(module.getId(), module.getCourse().getId(), module.getTitle(),
                module.getDescription(), module.getPosition(), module.isDefaultModule());
    }
}
