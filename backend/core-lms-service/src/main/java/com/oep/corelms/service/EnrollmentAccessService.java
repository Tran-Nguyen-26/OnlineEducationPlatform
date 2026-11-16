package com.oep.corelms.service;

import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Shared guard: a student may only view/interact with a course's content while actively enrolled in it. */
@Service
@RequiredArgsConstructor
public class EnrollmentAccessService {

    private final EnrollmentRepository enrollments;

    public Enrollment requireActiveEnrollment(UUID courseId, UUID studentId) {
        Enrollment enrollment = enrollments.findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ENROLLED, "Not enrolled in this course"));
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED, "Enrollment is not active");
        }
        return enrollment;
    }

    /** Read access (outline, files) also stays open after the course is COMPLETED so graduates can revisit material. */
    public Enrollment requireReadableEnrollment(UUID courseId, UUID studentId) {
        Enrollment enrollment = enrollments.findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ENROLLED, "Not enrolled in this course"));
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED, "Enrollment is not active");
        }
        return enrollment;
    }

    /** Progress can only change while the course is running (between its start and end dates, when set). */
    public void requireRunning(com.oep.coremodel.entity.course.Course course) {
        switch (course.currentPhase()) {
            case UPCOMING -> throw new BusinessException(ErrorCode.COURSE_NOT_OPEN, "Khóa học chưa bắt đầu");
            case ENDED -> throw new BusinessException(ErrorCode.COURSE_NOT_OPEN, "Khóa học đã kết thúc — chỉ có thể xem lại");
            default -> { }
        }
    }
}
