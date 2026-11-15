package com.oep.corelms.service;

import com.oep.corelms.dto.social.MessageResponse;
import com.oep.corelms.repository.CourseChannelRepository;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.MessageRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.entity.social.CourseChannel;
import com.oep.coremodel.entity.social.Message;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** One Q&A channel per course, shared by its enrolled students, assigned instructors, and admins. */
@Service
@RequiredArgsConstructor
public class CourseMessageService {

    private final CourseRepository courses;
    private final CourseChannelRepository channels;
    private final MessageRepository messages;
    private final EnrollmentRepository enrollments;
    private final CourseInstructorRepository courseInstructors;
    private final UserRepository users;
    private final UserProfileRepository userProfiles;

    @Transactional(readOnly = true)
    public List<MessageResponse> list(UUID courseId, UUID userId, String role) {
        requireAccess(courseId, userId, role);
        return channels.findByCourse_Id(courseId)
                .map(channel -> messages.findByChannel_IdOrderByCreatedAtAsc(channel.getId()).stream()
                        .map(this::toResponse)
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    public MessageResponse post(UUID courseId, UUID userId, String role, String content) {
        requireAccess(courseId, userId, role);
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));

        CourseChannel channel = channels.findByCourse_Id(courseId).orElseGet(() -> {
            CourseChannel c = new CourseChannel();
            c.setCourse(course);
            return channels.save(c);
        });

        Message message = new Message();
        message.setChannel(channel);
        message.setSender(users.getReferenceById(userId));
        message.setContent(content);
        return toResponse(messages.saveAndFlush(message));
    }

    private void requireAccess(UUID courseId, UUID userId, String role) {
        boolean allowed = switch (role) {
            case "ADMIN" -> true;
            case "INSTRUCTOR" -> courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, userId);
            default -> enrollments.findByCourseIdAndStudentId(courseId, userId)
                    .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                    .isPresent();
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You do not have access to this course's discussion");
        }
    }

    private MessageResponse toResponse(Message message) {
        User sender = message.getSender();
        String name = userProfiles.findById(sender.getId()).map(UserProfile::getFullName).orElse(sender.getEmail());
        return new MessageResponse(message.getId(), sender.getId(), name, sender.getRole(), message.getContent(), message.getCreatedAt());
    }
}
