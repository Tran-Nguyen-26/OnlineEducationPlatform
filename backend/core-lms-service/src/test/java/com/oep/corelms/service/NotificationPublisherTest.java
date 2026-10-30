package com.oep.corelms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.repository.OutboxEventRepository;
import com.oep.coremodel.entity.system.OutboxEvent;
import com.oep.coremodel.enums.OutboxStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationPublisher notificationPublisher;

    private UUID aggregateId;
    private UUID recipientUserId;

    @BeforeEach
    void setUp() {
        aggregateId = UUID.randomUUID();
        recipientUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should successfully publish outbox event in PENDING status with retryCount=0")
    void publish_Success_CreatesPendingOutboxEvent() throws Exception {
        Map<String, Object> payload = Map.of("quizId", aggregateId.toString(), "title", "Final Exam");
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"quizId\":\"" + aggregateId + "\"}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            event.setId(UUID.randomUUID());
            return event;
        });

        OutboxEvent result = notificationPublisher.publish("QUIZ", aggregateId, "QUIZ_PUBLISHED", payload);

        assertNotNull(result);
        assertEquals("QUIZ", result.getAggregateType());
        assertEquals(aggregateId, result.getAggregateId());
        assertEquals("QUIZ_PUBLISHED", result.getEventType());
        assertEquals(OutboxStatus.PENDING, result.getStatus());
        assertEquals(0, result.getRetryCount());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertEquals(OutboxStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should successfully format standard dedupeKey {event_type}:{target_id}:{user_id} in publishNotificationEvent")
    void publishNotificationEvent_Success_GeneratesStandardDedupeKey() throws Exception {
        String expectedDedupeKey = "ASSIGNMENT_GRADED:" + aggregateId + ":" + recipientUserId;
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"dedupeKey\":\"" + expectedDedupeKey + "\"}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEvent result = notificationPublisher.publishNotificationEvent(
                "ASSIGNMENT_GRADED",
                aggregateId,
                recipientUserId,
                "Assignment Graded",
                "Your Midterm submission was scored 9.0/10",
                "/courses/c1/assignments/a1",
                Map.of("score", 9.0)
        );

        assertNotNull(result);
        assertEquals("NOTIFICATION", result.getAggregateType());
        assertEquals("ASSIGNMENT_GRADED", result.getEventType());
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should compute standard dedupeKey correctly")
    void computeDedupeKey_StandardFormat() {
        String dedupeKey = notificationPublisher.computeDedupeKey("QUIZ_PUBLISHED", aggregateId, recipientUserId);
        assertEquals("QUIZ_PUBLISHED:" + aggregateId + ":" + recipientUserId, dedupeKey);
    }

    @Test
    @DisplayName("Should compute dedupeKey with fallbacks when target or user is null")
    void computeDedupeKey_WithNullTargetOrUser_UsesFallbacks() {
        assertEquals("BROADCAST:global:all", notificationPublisher.computeDedupeKey("BROADCAST", null, null));
        assertEquals("SYSTEM_ALERT:global:" + recipientUserId, notificationPublisher.computeDedupeKey("SYSTEM_ALERT", null, recipientUserId));
    }

    @Test
    @DisplayName("Should throw BusinessException when aggregateType is null or blank")
    void publish_MissingAggregateType_ThrowsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                notificationPublisher.publish("", aggregateId, "SOME_EVENT", Map.of())
        );
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
    }

    @Test
    @DisplayName("Should throw BusinessException when aggregateId is null")
    void publish_MissingAggregateId_ThrowsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                notificationPublisher.publish("COURSE", null, "COURSE_UPDATED", Map.of())
        );
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
    }

    @Test
    @DisplayName("Should throw BusinessException when eventType is null")
    void publish_MissingEventType_ThrowsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                notificationPublisher.publish("COURSE", aggregateId, null, Map.of())
        );
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
    }

    @Test
    @DisplayName("Should throw BusinessException when payload is null")
    void publish_MissingPayload_ThrowsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                notificationPublisher.publish("COURSE", aggregateId, "COURSE_UPDATED", null)
        );
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
    }

    @Test
    @DisplayName("Should throw BusinessException when ObjectMapper fails to serialize payload")
    void publish_JsonProcessingError_ThrowsBusinessException() throws Exception {
        Object badPayload = new Object();
        when(objectMapper.writeValueAsString(badPayload))
                .thenThrow(new JsonProcessingException("Serialization failed") {});

        BusinessException ex = assertThrows(BusinessException.class, () ->
                notificationPublisher.publish("COURSE", aggregateId, "COURSE_UPDATED", badPayload)
        );
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should throw BusinessException when recipientUserId is null in publishNotificationEvent")
    void publishNotificationEvent_NullRecipientUserId_ThrowsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                notificationPublisher.publishNotificationEvent(
                        "ASSIGNMENT_GRADED",
                        aggregateId,
                        null,
                        "Title",
                        "Message",
                        null,
                        null
                )
        );
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
        assertTrue(ex.getMessage().contains("recipientUserId"));
    }

    @Test
    @DisplayName("Should throw BusinessException when title is null or blank in publishNotificationEvent")
    void publishNotificationEvent_NullOrBlankTitle_ThrowsBusinessException() {
        BusinessException exNull = assertThrows(BusinessException.class, () ->
                notificationPublisher.publishNotificationEvent(
                        "ASSIGNMENT_GRADED",
                        aggregateId,
                        recipientUserId,
                        null,
                        "Message",
                        null,
                        null
                )
        );
        assertEquals(ErrorCode.VALIDATION_FAILED, exNull.getCode());

        BusinessException exBlank = assertThrows(BusinessException.class, () ->
                notificationPublisher.publishNotificationEvent(
                        "ASSIGNMENT_GRADED",
                        aggregateId,
                        recipientUserId,
                        "   ",
                        "Message",
                        null,
                        null
                )
        );
        assertEquals(ErrorCode.VALIDATION_FAILED, exBlank.getCode());
    }

    @Test
    @DisplayName("Should throw BusinessException when message is null or blank in publishNotificationEvent")
    void publishNotificationEvent_NullOrBlankMessage_ThrowsBusinessException() {
        BusinessException exNull = assertThrows(BusinessException.class, () ->
                notificationPublisher.publishNotificationEvent(
                        "ASSIGNMENT_GRADED",
                        aggregateId,
                        recipientUserId,
                        "Title",
                        null,
                        null,
                        null
                )
        );
        assertEquals(ErrorCode.VALIDATION_FAILED, exNull.getCode());

        BusinessException exBlank = assertThrows(BusinessException.class, () ->
                notificationPublisher.publishNotificationEvent(
                        "ASSIGNMENT_GRADED",
                        aggregateId,
                        recipientUserId,
                        "Title",
                        "   ",
                        null,
                        null
                )
        );
        assertEquals(ErrorCode.VALIDATION_FAILED, exBlank.getCode());
    }

    @Test
    @DisplayName("Should throw BusinessException when payload exceeds 256KB limit")
    void publish_PayloadExceeds256KB_ThrowsBusinessException() {
        UUID aggregateId = UUID.randomUUID();
        String hugePayload = "x".repeat(257 * 1024);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                notificationPublisher.publish("COURSE", aggregateId, "COURSE_UPDATED", hugePayload)
        );

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
        assertTrue(ex.getMessage().contains("256KB"));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should successfully include targetType in payload when using overloaded method")
    void publishNotificationEvent_WithTargetType_IncludesTargetTypeInPayload() throws Exception {
        UUID targetId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();

        when(objectMapper.writeValueAsString(any())).thenAnswer(inv ->
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(inv.getArgument(0)));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEvent result = notificationPublisher.publishNotificationEvent(
                "LESSON_COMPLETED",
                "LESSON",
                targetId,
                recipientUserId,
                "Lesson Done",
                "You finished lesson",
                "/lessons/" + targetId,
                Map.of("score", 100)
        );

        assertNotNull(result);
        assertTrue(result.getPayload().contains("\"targetType\":\"LESSON\""));
        assertTrue(result.getPayload().contains("\"score\":100"));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }
}
