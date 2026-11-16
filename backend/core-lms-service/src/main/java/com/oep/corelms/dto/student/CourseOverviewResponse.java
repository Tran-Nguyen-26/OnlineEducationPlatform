package com.oep.corelms.dto.student;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pre-enrollment course page: everything a learner needs to decide, with titles only (no lesson content). */
public record CourseOverviewResponse(
        UUID id, String title, String description, String subjectName, BigDecimal feeAmount, String learningMode,
        Integer capacity, long enrolledCount, String phase, Instant startAt, Instant endAt,
        List<Instructor> instructors, List<StudentCourseResponse.Prerequisite> prerequisites, List<Module> modules,
        int lessonCount, int quizCount, int assignmentCount, int sessionCount,
        int quizWeightPercent, Integer passingPercent, String myStatus) {

    public record Instructor(String name, String organization, String bio) {}

    public record Module(String title, List<String> lessons) {}
}
