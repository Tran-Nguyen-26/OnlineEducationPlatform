package com.oep.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.notification.Notification;
import com.oep.worker.config.OutboxWorkerProperties;
import com.oep.worker.config.SqsConfig;
import com.oep.worker.repository.NotificationRepository;
import com.oep.worker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSqsConsumerTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private SqsConfig sqsConfig;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    private OutboxWorkerProperties properties;
    private ObjectMapper objectMapper;
    private NotificationSqsConsumer consumer;

    private static final String QUEUE_URL = "https://sqs.ap-southeast-1.amazonaws.com/123/oep-notification-queue";
    private static final String DLQ_URL = "https://sqs.ap-southeast-1.amazonaws.com/123/oep-notification-dlq";

    @BeforeEach
    void setUp() {
        properties = new OutboxWorkerProperties();
        properties.getConsumer().setMaxRetries(5);
        properties.getConsumer().setMaxMessages(10);
        properties.getConsumer().setPollWaitSeconds(2);

        objectMapper = new ObjectMapper();

        consumer = new NotificationSqsConsumer(
                sqsClient,
                sqsConfig,
                properties,
                notificationRepository,
                userRepository,
                objectMapper
        );
        consumer.setQueueUrl(QUEUE_URL);
        consumer.setDlqUrl(DLQ_URL);
    }

    private Message createSqsMessage(String messageId, String body, int receiveCount) {
        return Message.builder()
                .messageId(messageId)
                .receiptHandle("receipt-handle-" + messageId)
                .body(body)
                .attributes(Map.of(
                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, String.valueOf(receiveCount)
                ))
                .build();
    }

    @Test
    @DisplayName("1. Successfully consumes SQS message and creates Notification in database with dedupe_key")
    void testConsumerSuccessfulIngestion_CreatesNotification() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String dedupeKey = "QUIZ_PUBLISHED:" + targetId + ":" + userId;

        String body = String.format("""
                {
                    "eventType": "QUIZ_PUBLISHED",
                    "title": "New Quiz Available",
                    "message": "Quiz has been published by instructor",
                    "recipientUserId": "%s",
                    "targetId": "%s",
                    "targetType": "QUIZ",
                    "dedupeKey": "%s"
                }
                """, userId, targetId, dedupeKey);

        Message message = createSqsMessage("msg-001", body, 1);
        User user = new User();
        user.setId(userId);
        user.setEmail("student@oep.edu.vn");

        when(notificationRepository.existsByDedupeKey(dedupeKey)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        boolean result = consumer.processMessage(message);

        assertThat(result).isTrue();

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notifCaptor.capture());

        Notification saved = notifCaptor.getValue();
        assertThat(saved.getDedupeKey()).isEqualTo(dedupeKey);
        assertThat(saved.getTitle()).isEqualTo("New Quiz Available");
        assertThat(saved.getMessage()).isEqualTo("Quiz has been published by instructor");
        assertThat(saved.getUser().getId()).isEqualTo(userId);
        assertThat(saved.isRead()).isFalse();

        // Message should be deleted from main SQS queue
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("2. Idempotency: Duplicate message with existing dedupe_key is suppressed (0 duplicate notifications)")
    void testConsumerDuplicateMessage_SuppressedByIdempotencyKey() {
        UUID userId = UUID.randomUUID();
        String dedupeKey = "ASSIGNMENT_GRADED:target-123:" + userId;
        String body = String.format("""
                {
                    "eventType": "ASSIGNMENT_GRADED",
                    "title": "Assignment Graded",
                    "message": "Grade is 10/10",
                    "recipientUserId": "%s",
                    "dedupeKey": "%s"
                }
                """, userId, dedupeKey);

        Message message = createSqsMessage("msg-002", body, 1);

        // Dedupe key already exists in DB!
        when(notificationRepository.existsByDedupeKey(dedupeKey)).thenReturn(true);

        boolean result = consumer.processMessage(message);

        assertThat(result).isTrue();
        // NEVER save duplicate notification
        verify(notificationRepository, never()).save(any(Notification.class));
        // Acknowledge/delete message from SQS
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("3. Race condition: DB unique constraint violation suppresses duplicate and acknowledges message")
    void testConsumerDuplicateViaConstraintViolation_Suppressed() {
        UUID userId = UUID.randomUUID();
        String dedupeKey = "COURSE_ANNOUNCEMENT:ann-01:" + userId;
        String body = String.format("""
                {
                    "eventType": "COURSE_ANNOUNCEMENT",
                    "title": "Course Announcement",
                    "message": "Holiday schedule",
                    "recipientUserId": "%s",
                    "dedupeKey": "%s"
                }
                """, userId, dedupeKey);

        Message message = createSqsMessage("msg-003", body, 1);
        User user = new User();
        user.setId(userId);

        when(notificationRepository.existsByDedupeKey(dedupeKey)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation on uq_notifications_dedupe_key"));

        boolean result = consumer.processMessage(message);

        assertThat(result).isTrue();
        // Acknowledge and delete message so it doesn't loop
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("4. Transient Error: Message failing with retry count < 5 is kept in queue for redelivery")
    void testConsumerTransientError_RetriesMessage() {
        UUID userId = UUID.randomUUID();
        String body = String.format("""
                {
                    "eventType": "ENROLLMENT_CONFIRMED",
                    "title": "Enrollment Confirmed",
                    "recipientUserId": "%s"
                }
                """, userId);

        Message message = createSqsMessage("msg-004", body, 2); // 2nd attempt

        when(notificationRepository.existsByDedupeKey(any())).thenReturn(false);
        when(userRepository.findById(userId)).thenThrow(new RuntimeException("Transient DB timeout"));

        boolean result = consumer.processMessage(message);

        assertThat(result).isFalse();
        // Should NOT delete from main queue (allows redelivery)
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        // Should NOT send to DLQ yet
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("5. Poisoned Message: Message exceeding 5 failed attempts is forwarded to DLQ and removed from main queue")
    void testConsumerExceeds5Retries_RoutesToDLQ() {
        UUID userId = UUID.randomUUID();
        String body = String.format("""
                {
                    "eventType": "POISON_EVENT",
                    "title": "Poison Message",
                    "recipientUserId": "%s"
                }
                """, userId);

        Message message = createSqsMessage("msg-poison-005", body, 5); // 5th attempt!

        when(notificationRepository.existsByDedupeKey(any())).thenReturn(false);
        when(userRepository.findById(userId)).thenThrow(new RuntimeException("Unrecoverable data error"));

        boolean result = consumer.processMessage(message);

        assertThat(result).isFalse();

        // 1. Must forward to DLQ
        ArgumentCaptor<SendMessageRequest> dlqCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(dlqCaptor.capture());
        assertThat(dlqCaptor.getValue().queueUrl()).isEqualTo(DLQ_URL);
        assertThat(dlqCaptor.getValue().messageBody()).isEqualTo(body);

        // 2. Must delete from main queue so it stops poisoning workers
        ArgumentCaptor<DeleteMessageRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient).deleteMessage(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().queueUrl()).isEqualTo(QUEUE_URL);
    }

    @Test
    @DisplayName("6. Dedupe Key Fallback: Automatically computes {eventType}:{targetId}:{recipientUserId} if not provided")
    void testConsumerAutoComputesDedupeKeyIfMissingInPayload() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        String body = String.format("""
                {
                    "eventType": "CERTIFICATE_ISSUED",
                    "title": "Certificate Issued",
                    "message": "Congrats",
                    "recipientUserId": "%s",
                    "targetId": "%s"
                }
                """, userId, targetId);

        Message message = createSqsMessage("msg-006", body, 1);
        User user = new User();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        boolean result = consumer.processMessage(message);

        assertThat(result).isTrue();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        String expectedKey = "CERTIFICATE_ISSUED:" + targetId + ":" + userId;
        assertThat(captor.getValue().getDedupeKey()).isEqualTo(expectedKey);
    }
}
