package com.oep.corelms.service;

import com.oep.corelms.dto.teacher.CourseContentResponse;
import com.oep.corelms.dto.teacher.CourseContentResponse.ContentItem;
import com.oep.corelms.dto.teacher.CourseContentResponse.ContentLesson;
import com.oep.corelms.dto.teacher.CourseContentResponse.ContentModule;
import com.oep.corelms.dto.teacher.InstructorCourseResponse;
import com.oep.corelms.dto.teacher.RosterEntryResponse;
import com.oep.corelms.dto.teacher.TeacherDashboardResponse;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonProgressRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.LessonProgressStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Teachers can only ever read course content — all authoring stays admin-only (see AdminModuleService etc). */
@Service
@RequiredArgsConstructor
public class TeacherCourseService {

    private final CourseRepository courses;
    private final CourseInstructorRepository courseInstructors;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonContentRepository lessonContents;
    private final TeacherAccessService accessService;
    private final EnrollmentRepository enrollments;
    private final LessonProgressRepository lessonProgress;
    private final UserProfileRepository userProfiles;

    @Transactional(readOnly = true)
    public List<InstructorCourseResponse> listMyCourses(UUID instructorId) {
        return courseInstructors.findByInstructor_IdOrderByAddedAtDesc(instructorId).stream()
                .map(link -> InstructorCourseResponse.from(link.getCourse()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherDashboardResponse dashboard(UUID instructorId) {
        var stats = courseInstructors.findByInstructor_IdOrderByAddedAtDesc(instructorId).stream().map(link -> {
            Course c = link.getCourse();
            return new TeacherDashboardResponse.CourseStat(c.getId(), c.getTitle(), c.getSubject().getName(), c.getLifecycleStatus().name(),
                    enrollments.countByCourseIdAndStatus(c.getId(), EnrollmentStatus.ACTIVE),
                    enrollments.countByCourseIdAndStatus(c.getId(), EnrollmentStatus.COMPLETED),
                    enrollments.countByCourseIdAndStatus(c.getId(), EnrollmentStatus.WAITLISTED), c.getCapacity());
        }).toList();
        int active = (int) stats.stream().mapToLong(TeacherDashboardResponse.CourseStat::activeStudents).sum();
        return new TeacherDashboardResponse(stats.size(), active, stats);
    }

    @Transactional(readOnly = true)
    public CourseContentResponse getContent(UUID courseId, UUID instructorId) {
        accessService.requireAssignedInstructor(courseId, instructorId);
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));

        List<ContentModule> moduleDtos = modules.findByCourse_IdOrderByPositionAsc(courseId).stream()
                .map(module -> new ContentModule(
                        module.getId(), module.getTitle(), module.getDescription(), module.getPosition(),
                        lessons.findByModule_IdOrderByPositionAsc(module.getId()).stream()
                                .map(lesson -> new ContentLesson(
                                        lesson.getId(), lesson.getTitle(), lesson.getDescription(), lesson.getPosition(),
                                        lesson.isRequired(),
                                        lessonContents.findByLesson_IdOrderByPositionAsc(lesson.getId()).stream()
                                                .map(c -> new ContentItem(c.getId(), c.getContentType(), c.getTitle(),
                                                        c.getTextContent(), c.getExternalUrl(), c.getFileName(), c.getUploadStatus()))
                                                .toList()))
                                .toList()))
                .toList();

        return new CourseContentResponse(courseId, course.getTitle(), moduleDtos);
    }

    /** Roster of every student ever enrolled (any status), with their lesson-completion progress in this course. */
    @Transactional(readOnly = true)
    public List<RosterEntryResponse> roster(UUID courseId, UUID instructorId) {
        accessService.requireAssignedInstructor(courseId, instructorId);

        int totalLessons = modules.findByCourse_IdOrderByPositionAsc(courseId).stream()
                .mapToInt(m -> (int) lessons.countByModule_Id(m.getId()))
                .sum();

        return enrollments.findByCourseIdOrderByEnrolledAtDesc(courseId).stream()
                .map(e -> toRosterEntry(e, totalLessons))
                .toList();
    }

    private RosterEntryResponse toRosterEntry(Enrollment e, int totalLessons) {
        User student = e.getStudent();
        String fullName = userProfiles.findById(student.getId()).map(UserProfile::getFullName).orElse(null);
        long completedLessons = lessonProgress.findByEnrollmentId(e.getId()).stream()
                .filter(p -> p.getStatus() == LessonProgressStatus.COMPLETED)
                .count();
        double percent = totalLessons == 0 ? 0.0 : Math.round(completedLessons * 1000.0 / totalLessons) / 10.0;

        return new RosterEntryResponse(e.getId(), student.getId(), student.getEmail(), fullName, e.getStatus(),
                e.getEnrolledAt(), totalLessons, (int) completedLessons, percent);
    }
}
