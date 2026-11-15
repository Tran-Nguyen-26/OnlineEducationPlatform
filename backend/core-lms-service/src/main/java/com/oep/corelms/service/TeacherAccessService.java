package com.oep.corelms.service;

import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** A teacher may only view a course (read-only) while admin has assigned them to it. */
@Service
@RequiredArgsConstructor
public class TeacherAccessService {

    private final CourseInstructorRepository courseInstructors;

    public void requireAssignedInstructor(UUID courseId, UUID instructorId) {
        if (!courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, instructorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not assigned to this course");
        }
    }
}
