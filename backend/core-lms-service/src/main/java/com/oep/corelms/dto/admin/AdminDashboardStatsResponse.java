package com.oep.corelms.dto.admin;

public record AdminDashboardStatsResponse(
        long totalSubjects,
        long totalCourses,
        long draftCourses,
        long publishedCourses,
        long archivedCourses,
        long disabledCourses,
        long totalTeachers,
        long totalStudents,
        long totalEnrollments,
        long activeEnrollments,
        long completedEnrollments,
        long withdrawnEnrollments,
        long removedEnrollments,
        double completionRatePercent) {
}
