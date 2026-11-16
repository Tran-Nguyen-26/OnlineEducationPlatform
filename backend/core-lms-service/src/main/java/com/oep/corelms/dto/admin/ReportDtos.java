package com.oep.corelms.dto.admin;

import java.util.List;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() {}

    /** Rates are percentages 0-100 (one decimal) or null when the denominator is zero. */
    public record CourseRow(UUID courseId, String title, String lifecycleStatus, int active, int completed, int withdrawn,
                            int removed, int waitlisted, Double completionRate, Double dropoutRate) {}

    public record InstructorRow(UUID instructorId, String name, String email, int courses, int activeStudents,
                                int completedStudents, Double completionRate, int pendingGrading) {}

    public record MonthRow(String month, int enrollments, int completions) {}

    public record Overview(int totalEnrollments, int activeEnrollments, int completedEnrollments, int dropouts,
                           Double overallCompletionRate, Double overallDropoutRate,
                           List<CourseRow> courses, List<InstructorRow> instructors, List<MonthRow> months) {}
}
