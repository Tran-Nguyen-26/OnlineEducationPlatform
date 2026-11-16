package com.oep.corelms.dto.admin;

import java.util.UUID;

public record CoursePrerequisiteResponse(UUID prerequisiteCourseId, String prerequisiteCourseTitle) {
}
