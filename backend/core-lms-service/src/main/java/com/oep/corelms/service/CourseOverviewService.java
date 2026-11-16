package com.oep.corelms.service;

import com.oep.corelms.dto.student.CourseOverviewResponse;
import com.oep.corelms.dto.student.StudentCourseResponse;
import com.oep.corelms.repository.AssignmentRepository;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CoursePrerequisiteRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.CourseSessionRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.QuizRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseOverviewService {

    private final CourseRepository courses;
    private final CourseInstructorRepository courseInstructors;
    private final UserProfileRepository userProfiles;
    private final CoursePrerequisiteRepository prerequisites;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final QuizRepository quizzes;
    private final AssignmentRepository assignments;
    private final CourseSessionRepository sessions;
    private final EnrollmentRepository enrollments;
    private final AvatarUrls avatarUrls;

    @Transactional(readOnly = true)
    public CourseOverviewResponse overview(UUID courseId, UUID studentId) {
        Course c = courses.findById(courseId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        Enrollment mine = enrollments.findByCourseIdAndStudentId(courseId, studentId).orElse(null);
        if (c.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED && mine == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Course not found");
        }
        var instructors = courseInstructors.findByCourse_Id(courseId).stream().map(ci -> {
            UserProfile p = userProfiles.findById(ci.getInstructor().getId()).orElse(null);
            return new CourseOverviewResponse.Instructor(p != null ? p.getFullName() : ci.getInstructor().getEmail(),
                    p == null ? null : p.getOrganization(), p == null ? null : p.getBio(), avatarUrls.of(ci.getInstructor().getId()));
        }).toList();
        var prereqs = prerequisites.findByCourse_Id(courseId).stream()
                .map(p -> new StudentCourseResponse.Prerequisite(p.getPrerequisiteCourse().getId(), p.getPrerequisiteCourse().getTitle(),
                        enrollments.existsByCourseIdAndStudentIdAndStatus(p.getPrerequisiteCourse().getId(), studentId, EnrollmentStatus.COMPLETED)))
                .toList();
        var mods = modules.findByCourse_IdOrderByPositionAsc(courseId).stream()
                .map(m -> new CourseOverviewResponse.Module(m.getTitle(),
                        lessons.findByModule_IdOrderByPositionAsc(m.getId()).stream().map(l -> l.getTitle()).toList()))
                .toList();
        int lessonCount = mods.stream().mapToInt(m -> m.lessons().size()).sum();
        return new CourseOverviewResponse(c.getId(), c.getTitle(), c.getDescription(), c.getSubject().getName(), c.getFeeAmount(),
                c.getLearningMode().name(), c.getCapacity(), enrollments.countByCourseIdAndStatus(courseId, EnrollmentStatus.ACTIVE),
                c.currentPhase().name(), c.getStartAt(), c.getEndAt(), instructors, prereqs, mods, lessonCount,
                quizzes.findByCourse_IdOrderByCreatedAtDesc(courseId).size(), assignments.findByCourse_IdOrderByCreatedAtDesc(courseId).size(),
                sessions.findByCourse_IdOrderByStartsAtAsc(courseId).size(), c.getQuizWeightPercent(), c.getPassingPercent(),
                mine == null ? null : mine.getStatus().name());
    }
}
