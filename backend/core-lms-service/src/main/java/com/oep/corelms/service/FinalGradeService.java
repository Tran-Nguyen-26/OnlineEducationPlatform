package com.oep.corelms.service;

import com.oep.corelms.repository.AssignmentGradeRepository;
import com.oep.corelms.repository.AssignmentRepository;
import com.oep.corelms.repository.AssignmentSubmissionRepository;
import com.oep.corelms.repository.CompletionWaiverRepository;
import com.oep.corelms.repository.QuizAnswerRepository;
import com.oep.corelms.repository.QuizAttemptRepository;
import com.oep.corelms.repository.QuizQuestionRepository;
import com.oep.corelms.repository.QuizRepository;
import com.oep.corelms.repository.QuizResultRepository;
import com.oep.coremodel.entity.assignment.Assignment;
import com.oep.coremodel.entity.assignment.AssignmentSubmission;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.CompletionWaiver;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.entity.quiz.QuizAttempt;
import com.oep.coremodel.entity.quiz.QuizQuestion;
import com.oep.coremodel.entity.quiz.QuizResult;
import com.oep.coremodel.grading.FinalGrade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Course-level grade of one enrollment: quizzes (by each quiz's grading policy) and assignments, weighted per the course settings. */
@Service
@RequiredArgsConstructor
public class FinalGradeService {

    private final QuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final QuizResultRepository quizResults;
    private final QuizQuestionRepository quizQuestions;
    private final QuizAnswerRepository quizAnswers;
    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final AssignmentGradeRepository grades;
    private final CompletionWaiverRepository waivers;

    /** pendingCount = items still waiting for a manual grade (assignments / essay answers); passed is null when the course has no threshold. */
    public record Standing(Double quizPercent, Double assignmentPercent, Double finalPercent, int pendingCount, Boolean passed,
                           int quizzesTotal, int quizzesAttempted, int assignmentsTotal, int assignmentsSubmitted, int waivedCount) {}

    @Transactional(readOnly = true)
    public Standing standing(Course course, Enrollment e) {
        Set<java.util.UUID> waived = waivers.findByEnrollment_Id(e.getId()).stream().map(CompletionWaiver::getTargetId).collect(Collectors.toSet());
        List<Double> quizPercents = new ArrayList<>();
        List<Double> assignmentPercents = new ArrayList<>();
        int pending = 0, quizzesTotal = 0, quizzesAttempted = 0, assignmentsTotal = 0, assignmentsSubmitted = 0;

        for (Quiz q : quizzes.findByCourse_IdOrderByCreatedAtDesc(course.getId())) {
            if (q.isLocked() || waived.contains(q.getId())) continue;
            quizzesTotal++;
            List<QuizAttempt> mine = attempts.findByQuiz_IdAndEnrollment_IdOrderByAttemptNumberAsc(q.getId(), e.getId());
            if (!mine.isEmpty()) quizzesAttempted++;
            if (mine.stream().anyMatch(a -> quizAnswers.hasPendingEssay(a.getId()))) pending++;
            BigDecimal max = quizQuestions.findByQuiz_IdOrderByPositionAsc(q.getId()).stream()
                    .map(QuizQuestion::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal best = best(q, mine);
            quizPercents.add(best == null || max.signum() <= 0 ? 0.0 : best.doubleValue() * 100.0 / max.doubleValue());
        }
        for (Assignment a : assignments.findByCourse_IdOrderByCreatedAtDesc(course.getId())) {
            if (waived.contains(a.getId())) continue;
            assignmentsTotal++;
            AssignmentSubmission sub = submissions.findByAssignment_IdAndEnrollment_Id(a.getId(), e.getId()).orElse(null);
            double pct = 0.0;
            if (sub != null) {
                assignmentsSubmitted++;
                BigDecimal score = grades.findBySubmission_Id(sub.getId()).map(g -> g.getScore()).orElse(null);
                if (score == null) pending++;
                else if (a.getMaxScore().signum() > 0) pct = score.doubleValue() * 100.0 / a.getMaxScore().doubleValue();
            }
            assignmentPercents.add(pct);
        }

        FinalGrade.Result r = FinalGrade.compute(quizPercents, assignmentPercents, course.getQuizWeightPercent());
        Boolean passed = course.getPassingPercent() == null ? null
                : (pending == 0 && (r.finalPercent() == null || r.finalPercent() >= course.getPassingPercent()));
        return new Standing(r.quizPercent(), r.assignmentPercent(), r.finalPercent(), pending, passed, quizzesTotal, quizzesAttempted,
                assignmentsTotal, assignmentsSubmitted, waived.size());
    }

    private BigDecimal best(Quiz quiz, List<QuizAttempt> mine) {
        List<BigDecimal> scores = mine.stream()
                .map(a -> quizResults.findByAttempt_Id(a.getId()).map(QuizResult::getFinalScore).orElse(null))
                .filter(Objects::nonNull).toList();
        if (scores.isEmpty()) return null;
        return switch (quiz.getGradingPolicy()) {
            case HIGHEST -> scores.stream().max(Comparator.naturalOrder()).orElse(null);
            case LATEST -> scores.get(scores.size() - 1);
            case AVERAGE -> scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        };
    }
}
