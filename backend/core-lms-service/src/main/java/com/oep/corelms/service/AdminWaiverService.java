package com.oep.corelms.service;

import com.oep.corelms.repository.AssignmentRepository;
import com.oep.corelms.repository.AssignmentSubmissionRepository;
import com.oep.corelms.repository.CompletionWaiverRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.QuizAttemptRepository;
import com.oep.corelms.repository.QuizRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.enrollment.CompletionWaiver;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin exemptions: waive a quiz/assignment for one enrollment so a missed item no longer blocks course completion. */
@Service
@RequiredArgsConstructor
public class AdminWaiverService {

    public record Item(String type, UUID id, String title, Instant deadline, boolean done, boolean waived, UUID waiverId, String reason) {}

    private final EnrollmentRepository enrollments;
    private final QuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final CompletionWaiverRepository waivers;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<Item> items(UUID enrollmentId) {
        Enrollment e = find(enrollmentId);
        UUID courseId = e.getCourse().getId();
        Map<UUID, CompletionWaiver> byTarget = waivers.findByEnrollment_Id(enrollmentId).stream()
                .collect(Collectors.toMap(CompletionWaiver::getTargetId, Function.identity()));
        List<Item> out = new ArrayList<>();
        for (var q : quizzes.findByCourse_IdOrderByCreatedAtDesc(courseId)) {
            CompletionWaiver w = byTarget.get(q.getId());
            out.add(new Item("QUIZ", q.getId(), q.getTitle(), q.getDeadline(), attempts.existsByQuiz_IdAndEnrollment_Id(q.getId(), enrollmentId),
                    w != null, w == null ? null : w.getId(), w == null ? null : w.getReason()));
        }
        for (var a : assignments.findByCourse_IdOrderByCreatedAtDesc(courseId)) {
            CompletionWaiver w = byTarget.get(a.getId());
            out.add(new Item("ASSIGNMENT", a.getId(), a.getTitle(), a.getDeadline(), submissions.existsByAssignment_IdAndEnrollment_Id(a.getId(), enrollmentId),
                    w != null, w == null ? null : w.getId(), w == null ? null : w.getReason()));
        }
        return out;
    }

    @Transactional
    public void waive(UUID enrollmentId, String targetType, UUID targetId, String reason, UUID adminId) {
        Enrollment e = find(enrollmentId);
        UUID courseId = e.getCourse().getId();
        boolean belongs = switch (targetType) {
            case "QUIZ" -> quizzes.findById(targetId).map(q -> q.getCourse().getId().equals(courseId)).orElse(false);
            case "ASSIGNMENT" -> assignments.findById(targetId).map(a -> a.getCourse().getId().equals(courseId)).orElse(false);
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "targetType must be QUIZ or ASSIGNMENT");
        };
        if (!belongs) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Item does not belong to this course");
        }
        if (waivers.findByEnrollment_IdAndTargetId(enrollmentId, targetId).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_ASSIGNED, "Already waived");
        }
        CompletionWaiver w = new CompletionWaiver();
        w.setEnrollment(e);
        w.setTargetType(targetType);
        w.setTargetId(targetId);
        w.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        w.setCreatedBy(users.getReferenceById(adminId));
        waivers.save(w);
    }

    @Transactional
    public void remove(UUID enrollmentId, UUID waiverId) {
        CompletionWaiver w = waivers.findById(waiverId).filter(x -> x.getEnrollment().getId().equals(enrollmentId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Waiver not found"));
        waivers.delete(w);
    }

    private Enrollment find(UUID enrollmentId) {
        return enrollments.findById(enrollmentId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Enrollment not found"));
    }
}
