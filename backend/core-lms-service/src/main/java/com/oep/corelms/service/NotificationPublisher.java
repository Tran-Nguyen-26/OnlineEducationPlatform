package com.oep.corelms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.repository.OutboxEventRepository;
import com.oep.coremodel.entity.system.OutboxEvent;
import com.oep.coremodel.enums.OutboxStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for publishing transactional domain events and notification requests
 * using the Transactional Outbox Pattern.
 * <p>
 * Key Architectural & Reliability Guarantees:
 * <ul>
 *   <li><b>Transactional Atomicity:</b> Events are written directly into {@code outbox_events}
 *       within the same database transaction as the business operation, eliminating dual-write inconsistencies.</li>
 *   <li><b>Idempotent Deduplication:</b> Enforces standard {@code dedupe_key} convention
 *       ({@code {event_type}:{target_id}:{user_id}}) to prevent duplicate notification deliveries.</li>
 *   <li><b>Decoupling:</b> Downstream listeners / workers poll or consume outbox events asynchronously.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Publishes a generic domain event into the transactional outbox table.
     * <p>
     * Must participate in an active database transaction alongside the business entity update.
     *
     * @param aggregateType The aggregate class or entity name (e.g. "COURSE", "ENROLLMENT", "QUIZ")
     * @param aggregateId   The UUID identifying the business aggregate root
     * @param eventType     Specific event type identifier (e.g. "QUIZ_PUBLISHED", "ASSIGNMENT_GRADED")
     * @param payload       Event payload object to be serialized into JSONB
     * @return The persisted {@link OutboxEvent} in {@link OutboxStatus#PENDING} status
     * @throws BusinessException if mandatory parameters are missing or serialization fails
     */
    @Transactional
    public OutboxEvent publish(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "aggregateType must not be null or empty");
        }
        if (aggregateId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "aggregateId must not be null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "eventType must not be null or empty");
        }
        if (payload == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "payload must not be null");
        }

        String jsonPayload;
        try {
            if (payload instanceof String str) {
                jsonPayload = str;
            } else {
                jsonPayload = objectMapper.writeValueAsString(payload);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for {} (ID: {})", eventType, aggregateId, e);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Failed to serialize outbox event payload: " + e.getMessage());
        }

        if (jsonPayload == null) {
            jsonPayload = "{}";
        }

        byte[] payloadBytes = jsonPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (payloadBytes.length > 256 * 1024) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Outbox event payload exceeds maximum size limit of 256KB (size=" + payloadBytes.length + " bytes)");
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType(aggregateType);
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(jsonPayload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setRetryCount(0);

        OutboxEvent saved = outboxEventRepository.save(outboxEvent);
        log.info("Enqueued outbox event [id={}, type={}, aggregate={}:{}]",
                saved.getId(), eventType, aggregateType, aggregateId);
        return saved;
    }

    /**
     * Convenience helper to publish a standardized notification outbox event with
     * guaranteed deduplication key generation.
     *
     * @param eventType       Standard event type (e.g. "LESSON_COMPLETED", "QUIZ_PUBLISHED")
     * @param targetId        UUID of the target resource (e.g., quizId, courseId)
     * @param recipientUserId UUID of the user who should receive the notification
     * @param title           Notification headline/title
     * @param message         Notification body content
     * @param actionUrl       Optional frontend link URL
     * @param extraMetadata   Optional additional metadata map
     * @return The persisted {@link OutboxEvent}
     */
    /**
     * Convenience helper to publish a standardized notification outbox event with
     * guaranteed deduplication key generation and explicit targetType.
     */
    @Transactional
    public OutboxEvent publishNotificationEvent(
            String eventType,
            String targetType,
            UUID targetId,
            UUID recipientUserId,
            String title,
            String message,
            String actionUrl,
            Map<String, Object> extraMetadata
    ) {
        Map<String, Object> metadata = extraMetadata != null ? new HashMap<>(extraMetadata) : new HashMap<>();
        if (targetType != null && !targetType.isBlank()) {
            metadata.put("targetType", targetType);
        }
        return publishNotificationEvent(eventType, targetId, recipientUserId, title, message, actionUrl, metadata);
    }

    @Transactional
    public OutboxEvent publishNotificationEvent(
            String eventType,
            UUID targetId,
            UUID recipientUserId,
            String title,
            String message,
            String actionUrl,
            Map<String, Object> extraMetadata
    ) {
        if (recipientUserId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "recipientUserId must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "title must not be null or empty");
        }
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "message must not be null or empty");
        }

        String dedupeKey = computeDedupeKey(eventType, targetId, recipientUserId);

        Map<String, Object> payload = new HashMap<>();
        if (extraMetadata != null) {
            payload.putAll(extraMetadata);
        }
        payload.put("eventType", eventType);
        payload.put("targetId", targetId != null ? targetId.toString() : null);
        payload.put("recipientUserId", recipientUserId.toString());
        payload.put("title", title);
        payload.put("message", message);
        payload.put("actionUrl", actionUrl);
        payload.put("dedupeKey", dedupeKey);

        return publish("NOTIFICATION", targetId != null ? targetId : recipientUserId, eventType, payload);
    }

    /**
     * Generates a deterministic deduplication key following the project standard:
     * {@code {event_type}:{target_id}:{user_id}}
     *
     * @param eventType       Name of the event
     * @param targetId        UUID of the target entity
     * @param recipientUserId UUID of the recipient user
     * @return Formatted dedupe key string
     */
    public String computeDedupeKey(String eventType, UUID targetId, UUID recipientUserId) {
        String targetPart = targetId != null ? targetId.toString() : "global";
        String userPart = recipientUserId != null ? recipientUserId.toString() : "all";
        return String.format("%s:%s:%s", eventType, targetPart, userPart);
    }
}
