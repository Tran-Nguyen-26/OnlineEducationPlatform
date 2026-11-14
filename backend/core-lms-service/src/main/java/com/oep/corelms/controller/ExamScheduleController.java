package com.oep.corelms.controller;

import com.oep.corelms.repository.QuizAttemptRepository;
import com.oep.corelms.repository.QuizRepository;
import com.oep.corelms.service.NotificationPublisher;
import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/quizzes/{quizId}/exam-schedule")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ExamScheduleController {
    private final QuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final JdbcTemplate jdbc;
    private final NotificationPublisher notifications;

    public record Schedule(@NotNull Instant startsAt, @NotNull Instant endsAt, @Min(0) @Max(60) int lateEntryMinutes) {}

    @GetMapping
    @Transactional(readOnly = true)
    public Schedule get(@PathVariable UUID courseId, @PathVariable UUID quizId) {
        var quiz = quizzes.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Không tìm thấy đề thi"));
        return new Schedule(quiz.getExamStartsAt(), quiz.getExamEndsAt(), quiz.getLateEntryMinutes() == null ? 15 : quiz.getLateEntryMinutes());
    }

    @PutMapping
    @Transactional
    public Schedule save(@PathVariable UUID courseId, @PathVariable UUID quizId, @Valid @RequestBody Schedule schedule) {
        var quiz = quizzes.findByIdAndCourseIdForUpdate(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Không tìm thấy đề thi"));
        if (!schedule.startsAt().isAfter(Instant.now()) || !schedule.endsAt().isAfter(schedule.startsAt()))
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Giờ thi phải ở tương lai và giờ kết thúc phải sau giờ bắt đầu");
        if (attempts.existsByQuizId(quizId))
            throw new BusinessException(ErrorCode.QUIZ_HAS_ATTEMPTS, "Ca thi đã có lượt làm bài");
        quiz.setAssessmentKind(Quiz.Kind.MASS_EXAM);
        quiz.setMaxAttempts(1);
        quiz.setExamStartsAt(schedule.startsAt());
        quiz.setExamEndsAt(schedule.endsAt());
        quiz.setLateEntryMinutes(schedule.lateEntryMinutes());
        var recipients = jdbc.queryForList("""
                select student_id from enrollments
                where course_id = ? and status in ('ACTIVE', 'COMPLETED')
                """, UUID.class, courseId);
        for (UUID recipient : recipients) {
            notifications.publishNotificationEvent("MASS_EXAM_SCHEDULED", "QUIZ", quizId, recipient,
                    "Đã lên lịch kỳ thi", "Kỳ thi “" + quiz.getTitle() + "” bắt đầu lúc " + schedule.startsAt(),
                    "/learner/courses/" + courseId + "/assessments",
                    Map.of("courseId", courseId, "startsAt", schedule.startsAt().toString(),
                            "endsAt", schedule.endsAt().toString()));
        }
        return schedule;
    }
}
