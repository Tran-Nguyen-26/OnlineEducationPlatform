package com.oep.coremodel.grading;

import java.util.List;

/**
 * Shared final-grade formula (used by both services so gradebook and course completion always agree).
 * Inputs are per-item percentages (0-100); unattempted items must already be passed as 0 and waived items omitted.
 */
public final class FinalGrade {

    private FinalGrade() {}

    public record Result(Double quizPercent, Double assignmentPercent, Double finalPercent) {}

    public static Result compute(List<Double> quizPercents, List<Double> assignmentPercents, int quizWeightPercent) {
        Double quiz = average(quizPercents);
        Double assignment = average(assignmentPercents);
        Double finalPercent;
        if (quiz == null && assignment == null) {
            finalPercent = null;
        } else if (quiz == null) {
            finalPercent = assignment;
        } else if (assignment == null) {
            finalPercent = quiz;
        } else {
            int w = Math.max(0, Math.min(100, quizWeightPercent));
            finalPercent = (quiz * w + assignment * (100 - w)) / 100.0;
        }
        return new Result(round(quiz), round(assignment), round(finalPercent));
    }

    private static Double average(List<Double> values) {
        return values == null || values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static Double round(Double v) {
        return v == null ? null : Math.round(v * 10.0) / 10.0;
    }
}
