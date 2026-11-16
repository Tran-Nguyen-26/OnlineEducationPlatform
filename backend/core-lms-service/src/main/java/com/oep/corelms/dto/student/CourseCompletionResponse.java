package com.oep.corelms.dto.student;

/**
 * Checklist of what a student still needs before they can finish the course; eligible = every line satisfied.
 * Items waived by an admin are excluded from the totals. passingPercent/passed are null when the course has no pass threshold.
 */
public record CourseCompletionResponse(String enrollmentStatus, int requiredLessons, int completedRequiredLessons,
                                       int quizzes, int attemptedQuizzes, int assignments, int submittedAssignments,
                                       Double quizPercent, Double assignmentPercent, Double finalPercent,
                                       Integer passingPercent, Boolean passed, int pendingGrading, int waived,
                                       String phase, boolean eligible, boolean completed) {
}
