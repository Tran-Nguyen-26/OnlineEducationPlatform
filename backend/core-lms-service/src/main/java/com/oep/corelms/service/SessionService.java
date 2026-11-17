package com.oep.corelms.service;

import com.oep.corelms.service.EventNotifier;
import com.oep.corelms.dto.social.SessionDtos;
import com.oep.corelms.repository.AttendanceRecordRepository;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.CourseSessionRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.course.AttendanceRecord;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseSession;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AttendanceStatus;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.event.NotificationRequestedEvent;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Class schedule + attendance. Staff = ADMIN or an instructor assigned to the course; students see the schedule and only their own status. */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Set<EnrollmentStatus> ROSTER = EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final CourseRepository courses;
    private final CourseSessionRepository sessions;
    private final AttendanceRecordRepository attendance;
    private final CourseInstructorRepository courseInstructors;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final UserProfileRepository userProfiles;
    private final EventNotifier eventNotifier;

    @Transactional(readOnly = true)
    public List<SessionDtos.SessionResponse> list(UUID courseId, UUID userId, String role) {
        boolean staff = isStaff(courseId, userId, role);
        Enrollment mine = null;
        if (!staff) {
            mine = enrollments.findByCourseIdAndStudentId(courseId, userId).filter(e -> ROSTER.contains(e.getStatus()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "You do not have access to this course's schedule"));
        }
        Map<UUID, List<AttendanceRecord>> bySession = attendance.findBySession_Course_Id(courseId).stream()
                .collect(Collectors.groupingBy(r -> r.getSession().getId()));
        List<SessionDtos.SessionResponse> out = new ArrayList<>();
        for (CourseSession s : sessions.findByCourse_IdOrderByStartsAtAsc(courseId)) {
            List<AttendanceRecord> rs = bySession.getOrDefault(s.getId(), List.of());
            String myStatus = null;
            if (mine != null) {
                UUID myId = mine.getId();
                myStatus = rs.stream().filter(r -> r.getEnrollment().getId().equals(myId)).map(r -> r.getStatus().name()).findFirst().orElse(null);
            }
            out.add(new SessionDtos.SessionResponse(s.getId(), s.getTitle(), s.getStartsAt(), s.getEndsAt(), s.getLocation(), s.getNote(),
                    staff ? count(rs, AttendanceStatus.PRESENT) : 0, staff ? count(rs, AttendanceStatus.LATE) : 0,
                    staff ? count(rs, AttendanceStatus.EXCUSED) : 0, staff ? count(rs, AttendanceStatus.ABSENT) : 0,
                    staff ? rs.size() : 0, myStatus));
        }
        return out;
    }

    @Transactional
    public SessionDtos.SessionResponse create(UUID courseId, UUID userId, String role, SessionDtos.CreateRequest req) {
        requireStaff(courseId, userId, role);
        if (req.endsAt() != null && req.endsAt().isBefore(req.startsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endsAt must be after startsAt");
        }
        Course course = courses.findById(courseId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        CourseSession s = new CourseSession();
        s.setCourse(course);
        s.setTitle(req.title().trim());
        s.setStartsAt(req.startsAt());
        s.setEndsAt(req.endsAt());
        s.setLocation(req.location());
        s.setNote(req.note());
        s.setCreatedBy(users.getReferenceById(userId));
        CourseSession saved = sessions.saveAndFlush(s);
        for (Enrollment e : enrollments.findByCourseIdAndStatus(courseId, EnrollmentStatus.ACTIVE)) {
            UUID studentId = e.getStudent().getId();
            eventNotifier.publish(new NotificationRequestedEvent(studentId, "SESSION_SCHEDULED", "Buổi học mới: " + saved.getTitle(),
                            "Khóa \"" + course.getTitle() + "\" — " + WHEN.format(saved.getStartsAt())
                                    + (saved.getLocation() != null && !saved.getLocation().isBlank() ? " — " + saved.getLocation() : ""),
                            "session:" + saved.getId() + ":" + studentId, "COURSE", courseId, Instant.now()));
        }
        return new SessionDtos.SessionResponse(saved.getId(), saved.getTitle(), saved.getStartsAt(), saved.getEndsAt(), saved.getLocation(),
                saved.getNote(), 0, 0, 0, 0, 0, null);
    }

    @Transactional
    public void delete(UUID courseId, UUID sessionId, UUID userId, String role) {
        requireStaff(courseId, userId, role);
        CourseSession s = find(courseId, sessionId);
        attendance.deleteBySession_Id(sessionId);
        sessions.delete(s);
    }

    @Transactional(readOnly = true)
    public List<SessionDtos.RosterRow> roster(UUID courseId, UUID sessionId, UUID userId, String role) {
        requireStaff(courseId, userId, role);
        find(courseId, sessionId);
        Map<UUID, AttendanceRecord> marked = attendance.findBySession_Id(sessionId).stream()
                .collect(Collectors.toMap(r -> r.getEnrollment().getId(), Function.identity()));
        return enrollments.findByCourseIdOrderByEnrolledAtDesc(courseId).stream()
                .filter(e -> ROSTER.contains(e.getStatus()))
                .map(e -> {
                    AttendanceRecord r = marked.get(e.getId());
                    return new SessionDtos.RosterRow(e.getId(), e.getStudent().getId(), nameOf(e), e.getStudent().getEmail(),
                            r != null ? r.getStatus().name() : null, r != null ? r.getNote() : null);
                })
                .sorted(java.util.Comparator.comparing(SessionDtos.RosterRow::studentName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public int mark(UUID courseId, UUID sessionId, UUID userId, String role, SessionDtos.MarkRequest req) {
        requireStaff(courseId, userId, role);
        CourseSession session = find(courseId, sessionId);
        int saved = 0;
        for (SessionDtos.MarkEntry entry : req.entries()) {
            AttendanceStatus status;
            try {
                status = AttendanceStatus.valueOf(entry.status());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid attendance status: " + entry.status());
            }
            Enrollment enrollment = enrollments.findById(entry.enrollmentId())
                    .filter(e -> e.getCourse().getId().equals(courseId) && ROSTER.contains(e.getStatus()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Enrollment is not on this course's roster"));
            AttendanceRecord rec = attendance.findBySession_IdAndEnrollment_Id(sessionId, enrollment.getId()).orElseGet(AttendanceRecord::new);
            rec.setSession(session);
            rec.setEnrollment(enrollment);
            rec.setStatus(status);
            rec.setNote(entry.note());
            rec.setMarkedBy(users.getReferenceById(userId));
            rec.setMarkedAt(Instant.now());
            attendance.save(rec);
            saved++;
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SessionDtos.SummaryRow> summary(UUID courseId, UUID userId, String role) {
        requireStaff(courseId, userId, role);
        Map<UUID, List<AttendanceRecord>> byEnrollment = attendance.findBySession_Course_Id(courseId).stream()
                .collect(Collectors.groupingBy(r -> r.getEnrollment().getId()));
        return enrollments.findByCourseIdOrderByEnrolledAtDesc(courseId).stream()
                .filter(e -> ROSTER.contains(e.getStatus()))
                .map(e -> {
                    List<AttendanceRecord> rs = byEnrollment.getOrDefault(e.getId(), List.of());
                    int p = count(rs, AttendanceStatus.PRESENT), l = count(rs, AttendanceStatus.LATE);
                    int x = count(rs, AttendanceStatus.EXCUSED), a = count(rs, AttendanceStatus.ABSENT);
                    Double rate = rs.isEmpty() ? null : Math.round((p + l + x) * 1000.0 / rs.size()) / 10.0;
                    return new SessionDtos.SummaryRow(e.getId(), e.getStudent().getId(), nameOf(e), e.getStudent().getEmail(), rs.size(), p, l, x, a, rate);
                })
                .sorted(java.util.Comparator.comparing(SessionDtos.SummaryRow::studentName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private CourseSession find(UUID courseId, UUID sessionId) {
        return sessions.findById(sessionId).filter(s -> s.getCourse().getId().equals(courseId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Session not found in this course"));
    }

    private boolean isStaff(UUID courseId, UUID userId, String role) {
        return "ADMIN".equals(role) || ("INSTRUCTOR".equals(role) && courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, userId));
    }

    private void requireStaff(UUID courseId, UUID userId, String role) {
        if (!isStaff(courseId, userId, role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the admin or an assigned instructor can manage sessions and attendance");
        }
    }

    private String nameOf(Enrollment e) {
        return userProfiles.findById(e.getStudent().getId()).map(UserProfile::getFullName).orElse(e.getStudent().getEmail());
    }

    private static int count(List<AttendanceRecord> rs, AttendanceStatus s) {
        return (int) rs.stream().filter(r -> r.getStatus() == s).count();
    }
}
