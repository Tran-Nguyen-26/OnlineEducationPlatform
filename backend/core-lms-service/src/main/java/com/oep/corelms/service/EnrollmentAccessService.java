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
}
