package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.course.Subject;

import java.util.UUID;

public record SubjectResponse(UUID id, String name, String description, boolean active) {
    public static SubjectResponse from(Subject subject) {
        return new SubjectResponse(subject.getId(), subject.getName(), subject.getDescription(), subject.isActive());
    }
}
