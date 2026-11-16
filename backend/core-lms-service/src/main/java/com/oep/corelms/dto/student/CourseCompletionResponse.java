package com.oep.corelms.dto.student;

/** Checklist of what a student still needs before they can finish the course; eligible = every line satisfied. */
public record CourseCompletionResponse(String enrollmentStatus, int requiredLessons, int completedRequiredLessons,
                                       int quizzes, int attemptedQuizzes, int assignments, int submittedAssignments,
                                       boolean eligible, boolean completed) {
}
