package com.oep.corelms.service;

import com.oep.corelms.service.EventNotifier;
import com.oep.corelms.dto.social.AnnouncementResponse;
import com.oep.corelms.repository.CourseAnnouncementRepository;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.entity.social.CourseAnnouncement;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.event.NotificationRequestedEvent;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Course announcements: posted by Admin or an assigned instructor, readable by the course's people, pushed to ACTIVE students as notifications. */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final CourseRepository courses;
    private final CourseAnnouncementRepository announcements;
    private final CourseInstructorRepository courseInstructors;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final UserProfileRepository userProfiles;
    private final EventNotifier eventNotifier;

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> list(UUID courseId, UUID userId, String role) {
        boolean allowed = switch (role) {
            case "ADMIN" -> true;
            case "INSTRUCTOR" -> courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, userId);
            default -> enrollments.findByCourseIdAndStudentId(courseId, userId)
                    .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE || e.getStatus() == EnrollmentStatus.COMPLETED).isPresent();
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You do not have access to this course's announcements");
        }
        return announcements.findByCourse_IdOrderByCreatedAtDesc(courseId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AnnouncementResponse post(UUID courseId, UUID userId, String role, String title, String content) {
        boolean allowed = "ADMIN".equals(role)
                || ("INSTRUCTOR".equals(role) && courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, userId));
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the admin or an assigned instructor can post announcements");
        }
        Course course = courses.findById(courseId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));

        CourseAnnouncement a = new CourseAnnouncement();
        a.setCourse(course);
        a.setAuthor(users.getReferenceById(userId));
        a.setTitle(title.trim());
        a.setContent(content.trim());
        CourseAnnouncement saved = announcements.saveAndFlush(a);

        String preview = saved.getContent().length() > 140 ? saved.getContent().substring(0, 140) + "..." : saved.getContent();
        for (Enrollment e : enrollments.findByCourseIdAndStatus(courseId, EnrollmentStatus.ACTIVE)) {
            UUID studentId = e.getStudent().getId();
            eventNotifier.publish(new NotificationRequestedEvent(studentId, "COURSE_ANNOUNCEMENT", "Thông báo khóa \"" + course.getTitle() + "\": " + saved.getTitle(),
                            preview, "announcement:" + saved.getId() + ":" + studentId, "COURSE", courseId, Instant.now()));
        }
        return toResponse(saved);
    }

    private AnnouncementResponse toResponse(CourseAnnouncement a) {
        User author = a.getAuthor();
        String name = userProfiles.findById(author.getId()).map(UserProfile::getFullName).orElse(author.getEmail());
        return new AnnouncementResponse(a.getId(), a.getTitle(), a.getContent(), name, author.getRole().name(), a.getCreatedAt());
    }
}
