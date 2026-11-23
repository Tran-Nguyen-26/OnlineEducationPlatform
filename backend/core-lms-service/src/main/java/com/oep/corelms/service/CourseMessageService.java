package com.oep.corelms.service;

import com.oep.corelms.service.EventNotifier;
import com.oep.corelms.dto.social.MessageResponse;
import com.oep.corelms.repository.CourseChannelRepository;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.MessageRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.entity.social.CourseChannel;
import com.oep.coremodel.entity.social.Message;
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
    private final LessonRepository lessons;
    private final EventNotifier eventNotifier;
    private final com.oep.corelms.repository.MessageAttachmentRepository attachmentRepo;
    private final StorageService storageService;

    /** lessonId null = the course's general thread; non-null = that lesson's own thread — the two never mix. */
    @Transactional(readOnly = true)
    public List<MessageResponse> list(UUID courseId, UUID userId, String role, UUID lessonId) {
        requireAccess(courseId, userId, role, false);
        return channels.findByCourse_Id(courseId)
                .map(channel -> (lessonId == null
                        ? messages.findByChannel_IdAndLessonIsNullOrderByCreatedAtAsc(channel.getId())
                        : messages.findByChannel_IdAndLesson_IdOrderByCreatedAtAsc(channel.getId(), lessonId)).stream()
                        .map(this::toResponse)
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    public MessageResponse post(UUID courseId, UUID userId, String role, String content, UUID lessonId) {
        requireAccess(courseId, userId, role, true);
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        Lesson lesson = lessonId == null ? null : lessons.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"));

        CourseChannel channel = channels.findByCourse_Id(courseId).orElseGet(() -> {
            CourseChannel c = new CourseChannel();
            c.setCourse(course);
            return channels.save(c);
        });

        Message message = new Message();
        message.setChannel(channel);
        message.setSender(users.getReferenceById(userId));
        message.setLesson(lesson);
        message.setContent(content);
        Message saved = messages.saveAndFlush(message);

        notifyOtherSide(course, userId, role, saved);
        return toResponse(saved);
    }

    /** A student's question notifies every assigned instructor; a teacher/admin reply notifies every active student. */
    private void notifyOtherSide(Course course, UUID senderId, String senderRole, Message message) {
        String senderName = userProfiles.findById(senderId).map(UserProfile::getFullName)
                .orElseGet(() -> users.findById(senderId).map(User::getEmail).orElse("Người dùng"));
        String preview = message.getContent().length() > 140 ? message.getContent().substring(0, 140) + "..." : message.getContent();

        List<UUID> recipientIds = "STUDENT".equals(senderRole)
                ? courseInstructors.findByCourse_Id(course.getId()).stream().map(ci -> ci.getInstructor().getId()).toList()
                : enrollments.findByCourseIdAndStatus(course.getId(), EnrollmentStatus.ACTIVE).stream()
                        .map(Enrollment::getStudent).map(User::getId).toList();

        for (UUID recipientId : recipientIds) {
            if (recipientId.equals(senderId)) continue;
            NotificationRequestedEvent event = new NotificationRequestedEvent(
                    recipientId, "COURSE_MESSAGE", "Tin nhắn mới trong \"" + course.getTitle() + "\"",
                    senderName + ": " + preview, "message:" + message.getId() + ":" + recipientId,
                    "COURSE", course.getId(), Instant.now());
            eventNotifier.publish(event);
        }
    }

    /** Reading old Q&A stays open to COMPLETED students (archived read-only mode, BRD); posting needs an ACTIVE enrollment. */
    private void requireAccess(UUID courseId, UUID userId, String role, boolean write) {
        boolean allowed = switch (role) {
            case "ADMIN" -> true;
            case "INSTRUCTOR" -> courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, userId);
            default -> enrollments.findByCourseIdAndStudentId(courseId, userId)
                    .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE || (!write && e.getStatus() == EnrollmentStatus.COMPLETED))
                    .isPresent();
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You do not have access to this course's discussion");
        }
    }

    private MessageResponse toResponse(Message message) {
        User sender = message.getSender();
        String name = userProfiles.findById(sender.getId()).map(UserProfile::getFullName).orElse(sender.getEmail());
        UUID lessonId = message.getLesson() == null ? null : message.getLesson().getId();
        var attachments = attachmentRepo.findByMessage_Id(message.getId()).stream()
                .map(a -> new MessageResponse.Attachment(a.getId(), a.getFileName(), a.getFileType(), a.getFileSize(), storageService.presignView(a.getObjectKey())))
                .toList();
        return new MessageResponse(message.getId(), sender.getId(), name, sender.getRole(), lessonId, message.getContent(), message.getCreatedAt(), attachments);
    }

    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS = 3;
    private static final java.util.Set<String> BLOCKED_EXT = java.util.Set.of("exe", "bat", "cmd", "sh", "msi", "js", "jar", "com", "scr");

    public record AttachmentUpload(String uploadUrl, String objectKey) {}

    /** Only the message's author may attach files (max 3 per message, 10MB each, no executables). */
    @Transactional(readOnly = true)
    public AttachmentUpload requestAttachmentUpload(UUID courseId, UUID messageId, UUID userId, String role, String fileName, String mimeType, long fileSize) {
        requireAccess(courseId, userId, role, true);
        Message m = ownMessage(courseId, messageId, userId);
        String ext = fileName == null || !fileName.contains(".") ? "" : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (BLOCKED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Loại file này không được phép đính kèm");
        }
        if (fileSize <= 0 || fileSize > MAX_ATTACHMENT_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "File đính kèm tối đa 10MB");
        }
        if (attachmentRepo.countByMessage_Id(m.getId()) >= MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tối đa " + MAX_ATTACHMENTS + " file cho mỗi tin nhắn");
        }
        String key = storageService.newObjectKey(fileName).replaceFirst("^lesson-contents/", "message-attachments/");
        String type = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
        return new AttachmentUpload(storageService.presignUpload(key, type), key);
    }

    @Transactional
    public MessageResponse confirmAttachment(UUID courseId, UUID messageId, UUID userId, String role, String objectKey, String fileName, String mimeType, long fileSize) {
        requireAccess(courseId, userId, role, true);
        Message m = ownMessage(courseId, messageId, userId);
        if (objectKey == null || !objectKey.startsWith("message-attachments/")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid attachment key");
        }
        if (attachmentRepo.countByMessage_Id(m.getId()) >= MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tối đa " + MAX_ATTACHMENTS + " file cho mỗi tin nhắn");
        }
        com.oep.coremodel.entity.social.MessageAttachment a = new com.oep.coremodel.entity.social.MessageAttachment();
        a.setMessage(m);
        a.setObjectKey(objectKey);
        a.setFileName(fileName);
        a.setFileType(mimeType);
        a.setFileSize(fileSize);
        attachmentRepo.save(a);
        return toResponse(m);
    }

    private Message ownMessage(UUID courseId, UUID messageId, UUID userId) {
        Message m = messages.findById(messageId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Message not found"));
        if (!m.getChannel().getCourse().getId().equals(courseId) || !m.getSender().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người gửi mới đính kèm file vào tin nhắn");
        }
        return m;
    }
}
