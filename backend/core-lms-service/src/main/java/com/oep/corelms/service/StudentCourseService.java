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

import java.util.HashMap;
import java.util.Map;
import com.oep.coremodel.entity.enrollment.Enrollment;
import java.util.List;
import java.util.UUID;

/** Every published course is browsable with its intro; enrollment (via invite code) unlocks the outline. */
@Service
@RequiredArgsConstructor
public class StudentCourseService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final com.oep.corelms.repository.CourseInstructorRepository courseInstructors;
    private final com.oep.corelms.repository.UserProfileRepository userProfiles;
    private final com.oep.corelms.repository.CoursePrerequisiteRepository coursePrerequisites;

    @Transactional(readOnly = true)
    public List<StudentCourseResponse> listCatalog(UUID studentId) {
        List<Course> published = courses.findByLifecycleStatusOrderByCreatedAtDesc(CourseLifecycleStatus.PUBLISHED);
        // bulk-load what the per-course lookups used to fetch one by one (was ~7 queries per course)
        Map<UUID, EnrollmentStatus> myStatus = new HashMap<>();
        for (Enrollment e : enrollments.findByStudentIdOrderByEnrolledAtDesc(studentId)) myStatus.putIfAbsent(e.getCourse().getId(), e.getStatus());
        Map<UUID, List<String>> instructors = instructorNamesFor(published.stream().map(Course::getId).toList());
        return published.stream()
                .map(course -> StudentCourseResponse.from(course,
                        myStatus.get(course.getId()) == EnrollmentStatus.ACTIVE,
                        myStatus.get(course.getId()) == EnrollmentStatus.WAITLISTED,
                        myStatus.get(course.getId()) == EnrollmentStatus.COMPLETED,
                        coursePrerequisites.findByCourse_Id(course.getId()).stream()
                                .map(p -> new StudentCourseResponse.Prerequisite(p.getPrerequisiteCourse().getId(), p.getPrerequisiteCourse().getTitle(),
                                        myStatus.get(p.getPrerequisiteCourse().getId()) == EnrollmentStatus.COMPLETED))
                                .toList(),
                        instructors.getOrDefault(course.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentCourseResponse getCourse(UUID courseId, UUID studentId) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Course not found");
        }
        return StudentCourseResponse.from(course, isActivelyEnrolled(courseId, studentId), isWaitlisted(courseId, studentId), isCompleted(courseId, studentId), prerequisitesOf(courseId, studentId), instructorNames(courseId));
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

    private Map<UUID, List<String>> instructorNamesFor(List<UUID> courseIds) {
        Map<UUID, List<String>> out = new HashMap<>();
        if (courseIds.isEmpty()) return out;
        var links = courseInstructors.findByCourse_IdIn(courseIds);
        Map<UUID, String> names = new HashMap<>();
        userProfiles.findAllById(links.stream().map(ci -> ci.getInstructor().getId()).distinct().toList())
                .forEach(p -> names.put(p.getUser().getId(), p.getFullName()));
        for (var ci : links) {
            out.computeIfAbsent(ci.getCourse().getId(), k -> new java.util.ArrayList<>())
                    .add(names.getOrDefault(ci.getInstructor().getId(), ci.getInstructor().getEmail()));
        }
        return out;
    }

    private java.util.List<String> instructorNames(java.util.UUID courseId) {
        return courseInstructors.findByCourse_Id(courseId).stream()
                .map(ci -> userProfiles.findById(ci.getInstructor().getId()).map(p -> p.getFullName()).orElse(ci.getInstructor().getEmail()))
                .toList();
    }
}
