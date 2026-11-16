package com.oep.assessment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GradebookResponse(UUID courseId, String courseTitle, int quizWeightPercent, Integer passingPercent,
                                List<Column> columns, List<Row> rows) {

    /** kind is QUIZ or ASSIGNMENT; maxScore is the quiz's total question points or the assignment's maxScore. */
    public record Column(UUID id, String kind, String title, BigDecimal maxScore, Instant deadline) {}

    /** status: NONE (nothing yet), SUBMITTED (assignment waiting for a grade / quiz not scored), GRADED. */
    public record Cell(BigDecimal score, int attempts, String status) {}

    public record Row(UUID enrollmentId, UUID studentId, String studentName, String studentEmail, String enrollmentStatus,
                      Map<UUID, Cell> cells, BigDecimal averagePercent,
                      Double quizPercent, Double assignmentPercent, Double finalPercent, Boolean passed, boolean pending) {}
}
