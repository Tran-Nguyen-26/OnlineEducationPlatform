package com.oep.corelms.service;

import com.oep.corelms.dto.notification.NotificationResponse;
import com.oep.corelms.repository.NotificationRepository;
import com.oep.coremodel.entity.notification.Notification;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing user notification inbox, polling, and read receipts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listNotifications(UUID userId, Boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "page must be >= 0 and size between 1 and 100");
        }
        var pageable = PageRequest.of(page, size);
        if (Boolean.TRUE.equals(unreadOnly)) {
            return notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false, pageable)
                    .map(NotificationResponse::from);
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notificationRepository.countByUserIdAndRead(userId, false);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Notification not found"));

        if (!notification.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot access notifications belonging to another user");
        }

        if (!notification.isRead()) {
            notification.setIsRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false);
        Instant now = Instant.now();
        for (Notification n : unread) {
            n.setIsRead(true);
            n.setReadAt(now);
        }
        notificationRepository.saveAll(unread);
        return unread.size();
    }
}
