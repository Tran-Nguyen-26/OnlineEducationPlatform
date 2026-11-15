package com.oep.corelms.service;

import com.oep.corelms.dto.student.MyEnrollmentResponse;
import com.oep.corelms.dto.student.StudentCourseResponse;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Every published course is browsable with its intro; enrollment (via invite code) unlocks the outline. */
@Service
@RequiredArgsConstructor
public class StudentCourseService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;

    @Transactional(readOnly = true)
    public List<StudentCourseResponse> listCatalog(UUID studentId) {
        return courses.findByLifecycleStatusOrderByCreatedAtDesc(CourseLifecycleStatus.PUBLISHED).stream()
                .map(course -> StudentCourseResponse.from(course, enrollments.existsByCourseIdAndStudentId(course.getId(), studentId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentCourseResponse getCourse(UUID courseId, UUID studentId) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Course not found");
        }
        return StudentCourseResponse.from(course, enrollments.existsByCourseIdAndStudentId(courseId, studentId));
    }

    @Transactional(readOnly = true)
    public List<MyEnrollmentResponse> myEnrollments(UUID studentId) {
        return enrollments.findByStudentIdOrderByEnrolledAtDesc(studentId).stream().map(MyEnrollmentResponse::from).toList();
    }
}
