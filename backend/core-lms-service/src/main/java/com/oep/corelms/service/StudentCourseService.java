package com.oep.corelms.service;

import com.oep.corelms.dto.student.MyEnrollmentResponse;
import com.oep.corelms.dto.student.StudentCourseResponse;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.EnrollmentStatus;
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
    private final com.oep.corelms.repository.CoursePrerequisiteRepository coursePrerequisites;

    @Transactional(readOnly = true)
    public List<StudentCourseResponse> listCatalog(UUID studentId) {
        return courses.findByLifecycleStatusOrderByCreatedAtDesc(CourseLifecycleStatus.PUBLISHED).stream()
                .map(course -> StudentCourseResponse.from(course, isActivelyEnrolled(course.getId(), studentId), isWaitlisted(course.getId(), studentId), isCompleted(course.getId(), studentId), prerequisitesOf(course.getId(), studentId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentCourseResponse getCourse(UUID courseId, UUID studentId) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Course not found");
        }
        return StudentCourseResponse.from(course, isActivelyEnrolled(courseId, studentId), isWaitlisted(courseId, studentId), isCompleted(courseId, studentId), prerequisitesOf(courseId, studentId));
    }

    /** A withdrawn/removed row still exists (unique per course+student) but must not read back as "enrolled". */
    private boolean isActivelyEnrolled(UUID courseId, UUID studentId) {
        return enrollments.existsByCourseIdAndStudentIdAndStatus(courseId, studentId, EnrollmentStatus.ACTIVE);
    }

    private boolean isCompleted(UUID courseId, UUID studentId) {
        return enrollments.existsByCourseIdAndStudentIdAndStatus(courseId, studentId, EnrollmentStatus.COMPLETED);
    }

    private boolean isWaitlisted(UUID courseId, UUID studentId) {
        return enrollments.existsByCourseIdAndStudentIdAndStatus(courseId, studentId, EnrollmentStatus.WAITLISTED);
    }

    @Transactional(readOnly = true)
    public List<MyEnrollmentResponse> myEnrollments(UUID studentId) {
        return enrollments.findByStudentIdOrderByEnrolledAtDesc(studentId).stream().map(MyEnrollmentResponse::from).toList();
    }

    private java.util.List<StudentCourseResponse.Prerequisite> prerequisitesOf(java.util.UUID courseId, java.util.UUID studentId) {
        return coursePrerequisites.findByCourse_Id(courseId).stream()
                .map(p -> new StudentCourseResponse.Prerequisite(p.getPrerequisiteCourse().getId(), p.getPrerequisiteCourse().getTitle(),
                        enrollments.existsByCourseIdAndStudentIdAndStatus(p.getPrerequisiteCourse().getId(), studentId,
                                com.oep.coremodel.enums.EnrollmentStatus.COMPLETED)))
                .toList();
    }
}
