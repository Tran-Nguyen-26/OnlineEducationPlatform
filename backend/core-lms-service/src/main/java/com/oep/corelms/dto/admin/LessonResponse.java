package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.course.Lesson;

import java.util.UUID;

public record LessonResponse(UUID id, UUID moduleId, String title, String description, int position, boolean required) {
    public static LessonResponse from(Lesson lesson) {
        return new LessonResponse(lesson.getId(), lesson.getModule().getId(), lesson.getTitle(),
                lesson.getDescription(), lesson.getPosition(), lesson.isRequired());
    }
}
