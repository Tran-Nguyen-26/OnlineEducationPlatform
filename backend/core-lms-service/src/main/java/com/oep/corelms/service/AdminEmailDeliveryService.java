package com.oep.corelms.service;

import com.oep.corelms.repository.EmailNotificationEventRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.notification.EmailNotificationEvent;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuditAction;
import com.oep.coremodel.enums.NotificationDeliveryStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminEmailDeliveryService {

    private final EmailNotificationEventRepository events;
    private final UserRepository users;
    private final AuditService audit;

    public record Delivery(UUID id, String eventType, String recipientEmail, String subject,
                           NotificationDeliveryStatus status, int retryCount, String lastError,
                           Instant scheduledAt, Instant sentAt, String dedupeKey) {}
    public record DeliveryPage(List<Delivery> items, int page, int pageSize, long total, boolean hasNext) {}

    @Transactional(readOnly = true)
    public DeliveryPage listFailed(UUID actorId, int page, int pageSize) {
        requireAdmin(actorId);
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Trang phải từ 1 và kích thước tối đa là 100");
        }
        var results = events.findByStatusOrderByScheduledAtAsc(NotificationDeliveryStatus.FAILED,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "scheduledAt")));
        return new DeliveryPage(results.getContent().stream().map(this::toDelivery).toList(), page, pageSize,
                results.getTotalElements(), results.hasNext());
    }

    @Transactional
    public Delivery replay(UUID actorId, UUID eventId) {
        requireAdmin(actorId);
        EmailNotificationEvent event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy email lỗi"));
        if (event.getStatus() != NotificationDeliveryStatus.FAILED) {
            throw new BusinessException(ErrorCode.GRADING_STATE_CONFLICT, "Chỉ có thể chạy lại email đang ở trạng thái FAILED");
        }
        event.setStatus(NotificationDeliveryStatus.PENDING);
        event.setRetryCount(0);
        event.setScheduledAt(Instant.now());
        event.setSentAt(null);
        event.setLastError(null);
        events.saveAndFlush(event);
        audit.record(actorId, AuditAction.REPLAY_FAILED_EMAIL, "EMAIL_NOTIFICATION", eventId,
                Map.of("eventType", event.getEventType() == null ? "UNKNOWN" : event.getEventType(),
                        "dedupeKey", event.getDedupeKey() == null ? "" : event.getDedupeKey()));
        return toDelivery(event);
    }

    private void requireAdmin(UUID actorId) {
        boolean allowed = actorId != null && users.findById(actorId)
                .filter(user -> user.getRole() == UserRole.ADMIN && user.getAccountStatus() == AccountStatus.ACTIVE)
                .isPresent();
        if (!allowed) throw new BusinessException(ErrorCode.ADMIN_ROLE_REQUIRED, "Chỉ Admin đang hoạt động được quản lý email lỗi");
    }

    private Delivery toDelivery(EmailNotificationEvent event) {
        return new Delivery(event.getId(), event.getEventType(), event.getRecipientEmail(), event.getSubject(),
                event.getStatus(), event.getRetryCount(), event.getLastError(), event.getScheduledAt(),
                event.getSentAt(), event.getDedupeKey());
    }
}
