package com.oep.worker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.notification.Notification;
import com.oep.worker.config.OutboxWorkerProperties;
import com.oep.worker.config.SqsConfig;
import com.oep.worker.repository.NotificationRepository;
import com.oep.worker.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SQS Consumer for notification events.
 * <p>
 * Key Features & Idempotency:
 * <ul>
 *   <li>Guarantees exactly-once effect: Suppresses duplicates via {@code dedupe_key} UNIQUE constraint.</li>
 *   <li>Retries: Failed messages are retried up to 5 times.</li>
 *   <li>Dead Letter Queue (DLQ): Messages exceeding 5 failed attempts are routed to {@code oep-notification-dlq}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSqsConsumer {

    private final SqsClient sqsClient;
    private final SqsConfig sqsConfig;
    private final OutboxWorkerProperties properties;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService consumerExecutor;

    @Getter
    private String queueUrl;

    @Getter
    private String dlqUrl;

    @PostConstruct
    public void init() {
        if (properties.getConsumer().isEnabled()) {
            start();
        }
    }

    @PreDestroy
    public void shutdown() {
        stop();
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting NotificationSqsConsumer...");
            resolveQueues();
            consumerExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sqs-notification-consumer");
                t.setDaemon(true);
                return t;
            });
            consumerExecutor.submit(this::consumptionLoop);
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping NotificationSqsConsumer...");
            if (consumerExecutor != null) {
                consumerExecutor.shutdownNow();
                try {
                    if (!consumerExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                        log.warn("Notification consumer executor did not terminate cleanly");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void resolveQueues() {
        try {
            this.dlqUrl = sqsConfig.resolveOrCreateDlqUrl(sqsClient);
            String dlqArn = sqsConfig.getQueueArn(sqsClient, dlqUrl);
            this.queueUrl = sqsConfig.resolveOrCreateQueueUrl(sqsClient, sqsConfig.getQueueName(), dlqArn);
            log.info("Resolved queueUrl={}, dlqUrl={}", queueUrl, dlqUrl);
        } catch (Exception e) {
            log.warn("Could not pre-resolve SQS queues during consumer start: {}", e.getMessage());
        }
    }

    private void consumptionLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                consumeMessagesOnce();
            } catch (Exception e) {
                log.error("Error in SQS consumer loop: {}", e.getMessage(), e);
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Polls and processes one batch of messages from SQS.
     *
     * @return Number of successfully processed/acknowledged messages.
     */
    public int consumeMessagesOnce() {
        ensureQueues();

        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(properties.getConsumer().getMaxMessages())
                .waitTimeSeconds(Math.min(properties.getConsumer().getPollWaitSeconds(), 20))
                .attributeNamesWithStrings("All")
                .messageAttributeNames("All")
                .build();

        ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
        List<Message> messages = response.messages();

        if (messages.isEmpty()) {
            return 0;
        }

        int processedCount = 0;
        for (Message message : messages) {
            boolean handled = processMessage(message);
            if (handled) {
                processedCount++;
            }
        }
        return processedCount;
    }

    public boolean processMessage(Message message) {
        String body = message.body();
        try {
            JsonNode root = objectMapper.readTree(body);

            String eventType = text(root, "eventType", "GENERAL_NOTIFICATION");
            String title = text(root, "title", "Notification");
            String messageText = text(root, "message", "");
            String targetType = text(root, "targetType", null);
            String actionUrl = text(root, "actionUrl", null);

            UUID targetId = null;
            if (root.hasNonNull("targetId")) {
                try {
                    targetId = UUID.fromString(root.get("targetId").asText());
                } catch (IllegalArgumentException ignored) {}
            }

            String userIdStr = text(root, "recipientUserId", null);
            if (userIdStr == null && root.hasNonNull("userId")) {
                userIdStr = root.get("userId").asText();
            }

            if (userIdStr == null || userIdStr.isBlank()) {
                throw new IllegalArgumentException("Message missing required recipientUserId: " + body);
            }
            UUID recipientUserId = UUID.fromString(userIdStr);

            String dedupeKey = text(root, "dedupeKey", null);
            if (dedupeKey == null || dedupeKey.isBlank()) {
                dedupeKey = String.format("%s:%s:%s",
                        eventType,
                        targetId != null ? targetId : "global",
                        recipientUserId);
            }

            // 1. Idempotency Check: if dedupe_key already exists, acknowledge and discard
            if (notificationRepository.existsByDedupeKey(dedupeKey)) {
                log.info("Duplicate notification suppressed for dedupe_key: {}", dedupeKey);
                acknowledgeMessage(message);
                return true;
            }

            // 2. Fetch or reference Recipient User
            User user = userRepository.findById(recipientUserId)
                    .orElseGet(() -> {
                        User fallbackUser = new User();
                        fallbackUser.setId(recipientUserId);
                        fallbackUser.setEmail("user-" + recipientUserId.toString().substring(0, 8) + "@oep.invalid");
                        return userRepository.save(fallbackUser);
                    });

            // 3. Create Notification
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setEventType(eventType);
            notification.setTitle(title);
            notification.setMessage(messageText);
            notification.setDedupeKey(dedupeKey);
            notification.setTargetType(targetType);
            notification.setTargetId(targetId);
            notification.setActionUrl(actionUrl);
            notification.setMetadata(root.has("metadata") ? root.get("metadata").toString() : null);
            notification.setIsRead(false);

            try {
                notificationRepository.save(notification);
                log.debug("Created notification [id={}, dedupeKey={}]", notification.getId(), dedupeKey);
                acknowledgeMessage(message);
                return true;
            } catch (DataIntegrityViolationException ex) {
                // Concurrent insert with duplicate dedupe_key caught at DB level
                log.info("Duplicate notification suppressed via DB constraint for dedupeKey: {}", dedupeKey);
                acknowledgeMessage(message);
                return true;
            }

        } catch (Exception e) {
            log.error("Failed to process message [id={}]: {}", message.messageId(), e.getMessage());
            handleMessageFailure(message, e);
            return false;
        }
    }

    private void handleMessageFailure(Message message, Exception ex) {
        int receiveCount = getReceiveCount(message);
        int maxRetries = properties.getConsumer().getMaxRetries();

        if (receiveCount >= maxRetries) {
            log.error("Message {} reached maximum retries ({} >= {}). Forwarding to DLQ...",
                    message.messageId(), receiveCount, maxRetries);
            forwardToDlq(message, ex.getMessage(), receiveCount);
            acknowledgeMessage(message);
        } else {
            log.warn("Message {} failed (attempt {}/{}). Retrying on next visibility window. Error: {}",
                    message.messageId(), receiveCount, maxRetries, ex.getMessage());
            // Intentionally not deleted from queue, so SQS redelivers after visibility timeout
        }
    }

    private void forwardToDlq(Message message, String errorReason, int attemptCount) {
        try {
            ensureQueues();
            Map<String, MessageAttributeValue> dlqAttributes = new HashMap<>(message.messageAttributes());
            dlqAttributes.put("dlqReason", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(errorReason != null ? errorReason : "Unknown error")
                    .build());
            dlqAttributes.put("retryCount", MessageAttributeValue.builder()
                    .dataType("Number")
                    .stringValue(String.valueOf(attemptCount))
                    .build());
            dlqAttributes.put("failedAt", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(Instant.now().toString())
                    .build());

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(dlqUrl)
                    .messageBody(message.body())
                    .messageAttributes(dlqAttributes)
                    .build());

            log.info("Successfully routed poisoned message {} to DLQ: {}", message.messageId(), dlqUrl);
        } catch (Exception e) {
            log.error("Failed to forward poisoned message {} to DLQ: {}", message.messageId(), e.getMessage(), e);
        }
    }

    private void acknowledgeMessage(Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (Exception e) {
            log.error("Failed to delete acknowledged message {} from SQS: {}", message.messageId(), e.getMessage());
        }
    }

    public int getReceiveCount(Message message) {
        String countStr = null;
        if (message.attributesAsStrings() != null) {
            countStr = message.attributesAsStrings().get("ApproximateReceiveCount");
        }
        if (countStr == null && message.attributes() != null) {
            countStr = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        }
        if (countStr != null && !countStr.isBlank()) {
            try {
                return Integer.parseInt(countStr);
            } catch (NumberFormatException ignored) {}
        }
        if (message.messageAttributes().containsKey("retryCount")) {
            try {
                return Integer.parseInt(message.messageAttributes().get("retryCount").stringValue());
            } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    private void ensureQueues() {
        if (queueUrl == null || dlqUrl == null) {
            resolveQueues();
        }
    }

    public void setQueueUrl(String queueUrl) {
        this.queueUrl = queueUrl;
    }

    public void setDlqUrl(String dlqUrl) {
        this.dlqUrl = dlqUrl;
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        if (node.hasNonNull(field)) {
            return node.get(field).asText();
        }
        return defaultValue;
    }
}
