package com.oep.corelms.service;

import com.oep.corelms.dto.notification.NotificationResponse;
import com.oep.corelms.repository.NotificationRepository;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.notification.Notification;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
    }

    private Notification createNotification(UUID id, boolean read) {
        Notification n = new Notification();
        n.setId(id);
        n.setUser(user);
        n.setEventType("ASSIGNMENT_GRADED");
        n.setTitle("Assignment Graded");
        n.setMessage("Your score is 95/100");
        n.setDedupeKey("ASSIGNMENT_GRADED:target:" + userId);
        n.setIsRead(read);
        n.setCreatedAt(Instant.now());
        return n;
    }

    @Test
    @DisplayName("Should list all user notifications paginated")
    void listNotifications_All_ReturnsPage() {
        Notification n = createNotification(UUID.randomUUID(), false);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(n)));

        Page<NotificationResponse> result = notificationService.listNotifications(userId, false, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("ASSIGNMENT_GRADED", result.getContent().get(0).eventType());
    }

    @Test
    @DisplayName("Should list unread notifications when unreadOnly is true")
    void listNotifications_UnreadOnly_ReturnsUnreadPage() {
        Notification n = createNotification(UUID.randomUUID(), false);
        when(notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(eq(userId), eq(false), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(n)));

        Page<NotificationResponse> result = notificationService.listNotifications(userId, true, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertFalse(result.getContent().get(0).isRead());
    }

    @Test
    @DisplayName("Should count unread notifications correctly")
    void countUnread_ReturnsCount() {
        when(notificationRepository.countByUserIdAndRead(userId, false)).thenReturn(7L);

        long count = notificationService.countUnread(userId);

        assertEquals(7L, count);
    }

    @Test
    @DisplayName("Should mark notification as read when owned by user")
    void markAsRead_OwnedByUser_Success() {
        UUID notifId = UUID.randomUUID();
        Notification n = createNotification(notifId, false);
        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(notifId, userId);

        assertTrue(response.isRead());
        assertNotNull(response.readAt());
    }

    @Test
    @DisplayName("Should throw FORBIDDEN when user marks notification belonging to another user")
    void markAsRead_BelongsToAnotherUser_ThrowsForbidden() {
        UUID notifId = UUID.randomUUID();
        Notification n = createNotification(notifId, false);
        UUID otherUserId = UUID.randomUUID();

        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(n));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                notificationService.markAsRead(notifId, otherUserId)
        );

        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("Should mark all notifications as read")
    void markAllAsRead_Success() {
        Notification n1 = createNotification(UUID.randomUUID(), false);
        Notification n2 = createNotification(UUID.randomUUID(), false);
        when(notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false))
                .thenReturn(List.of(n1, n2));

        int updated = notificationService.markAllAsRead(userId);

        assertEquals(2, updated);
        assertTrue(n1.isRead());
        assertTrue(n2.isRead());
        verify(notificationRepository).saveAll(any());
    }
}
