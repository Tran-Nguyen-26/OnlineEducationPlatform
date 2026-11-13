package com.oep.corelms.dto.enrollment;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;
public record EnrollmentResponse(UUID id, UUID courseId, UUID studentId, EnrollmentStatus status,
                                 String createdVia, Instant enrolledAt, Instant completedAt,
                                 Instant withdrawnAt, Instant removedAt, String courseTitle,
                                 Instant accessExpiresAt, AccessState accessState) {
    public enum AccessState { ACTIVE, ARCHIVED_STATIC, UNAVAILABLE }

    public static EnrollmentResponse from(Enrollment e) {
        return new EnrollmentResponse(e.getId(), e.getCourse().getId(), e.getStudent().getId(), e.getStatus(),
                e.getCreatedVia(), e.getEnrolledAt(), e.getCompletedAt(), e.getWithdrawnAt(), e.getRemovedAt(),
                e.getCourse().getTitle(), e.getAccessExpiresAt(), accessStateAt(e, Instant.now()));
    }

    public static AccessState accessStateAt(Enrollment enrollment, Instant now) {
        return switch (enrollment.getStatus()) {
            case WITHDRAWN, REMOVED -> AccessState.UNAVAILABLE;
            case ACTIVE, COMPLETED -> enrollment.getAccessExpiresAt() != null
                    && now.isAfter(enrollment.getAccessExpiresAt()) ? AccessState.ARCHIVED_STATIC : AccessState.ACTIVE;
        };
    }
}
