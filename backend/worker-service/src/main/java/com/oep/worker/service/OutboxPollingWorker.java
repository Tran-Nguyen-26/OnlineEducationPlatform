package com.oep.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.entity.system.OutboxEvent;
import com.oep.coremodel.enums.OutboxStatus;
import com.oep.worker.config.OutboxWorkerProperties;
import com.oep.worker.config.SqsConfig;
import com.oep.worker.repository.OutboxEventRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker that polls PENDING outbox events using PostgreSQL 'FOR UPDATE SKIP LOCKED'
 * and dispatches them to AWS SQS (LocalStack / AWS).
 * <p>
 * Key Features & Guarantees:
 * <ul>
 *   <li>Concurrency: 'SKIP LOCKED' prevents worker collisions across horizontal replicas.</li>
 *   <li>Batching: Polls in batches of 100 and uses SQS SendMessageBatch for high throughput.</li>
 *   <li>Dynamic Backoff: Backs off exponentially up to 30s when outbox is empty; resets instantly upon event arrival.</li>
 *   <li>Fault Tolerance: If worker crashes midway, database transaction rolls back, zero events lost.</li>
 *   <li>Retry & Failure: Increments retry_count upon SQS failure; transitions to FAILED when retry exceeds 5.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPollingWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final SqsClient sqsClient;
    private final SqsConfig sqsConfig;
    private final OutboxWorkerProperties properties;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService pollerExecutor;
    private String queueUrl;

    @Getter
    private volatile long currentPollDelayMs;

    @PostConstruct
    public void init() {
        this.currentPollDelayMs = properties.getWorker().getBasePollDelayMs();
        if (properties.getWorker().isEnabled()) {
            start();
        }
    }

    @PreDestroy
    public void shutdown() {
        stop();
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting OutboxPollingWorker with batchSize={}, baseDelay={}ms, maxDelay={}ms",
                    properties.getWorker().getBatchSize(),
                    properties.getWorker().getBasePollDelayMs(),
                    properties.getWorker().getMaxPollDelayMs());

            try {
                String dlqUrl = sqsConfig.resolveOrCreateDlqUrl(sqsClient);
                String dlqArn = sqsConfig.getQueueArn(sqsClient, dlqUrl);
                this.queueUrl = sqsConfig.resolveOrCreateQueueUrl(sqsClient, sqsConfig.getQueueName(), dlqArn);
            } catch (Exception e) {
                log.warn("Could not pre-resolve SQS queue during start (will lazy resolve): {}", e.getMessage());
            }

            pollerExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "outbox-poller");
                t.setDaemon(true);
                return t;
            });
            pollerExecutor.submit(this::pollingLoop);
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping OutboxPollingWorker...");
            if (pollerExecutor != null) {
                pollerExecutor.shutdownNow();
                try {
                    if (!pollerExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                        log.warn("Outbox poller executor did not terminate gracefully");
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

    private void pollingLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                int dispatchedCount = pollAndDispatchBatch();
                if (dispatchedCount == 0) {
                    // Empty queue: exponential backoff up to 30 seconds
                    currentPollDelayMs = Math.min(
                            currentPollDelayMs * 2,
                            properties.getWorker().getMaxPollDelayMs()
                    );
                } else {
                    // Events found: reset backoff immediately to base delay for fast throughput
                    currentPollDelayMs = properties.getWorker().getBasePollDelayMs();
                }

                TimeUnit.MILLISECONDS.sleep(currentPollDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unhandled error in outbox polling loop: {}", e.getMessage(), e);
                try {
                    currentPollDelayMs = Math.min(
                            currentPollDelayMs * 2,
                            properties.getWorker().getMaxPollDelayMs()
                    );
                    TimeUnit.MILLISECONDS.sleep(currentPollDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Polls a batch of up to {@code batchSize} PENDING events using FOR UPDATE SKIP LOCKED
     * and dispatches them to SQS.
     *
     * @return Number of successfully processed events in this poll cycle.
     */
    public int pollAndDispatchBatch() {
        ensureQueueUrl();
        if (queueUrl == null || queueUrl.isBlank()) {
            log.warn("SQS queueUrl is not available, skipping poll cycle.");
            return 0;
        }

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return txTemplate.execute(status -> {
            int batchSize = properties.getWorker().getBatchSize();
            List<OutboxEvent> events = outboxEventRepository.pollPendingWithSkipLocked(batchSize);

            if (events.isEmpty()) {
                return 0;
            }

            log.debug("Polled {} PENDING outbox events with SKIP LOCKED", events.size());

            // Dispatch in sub-batches of 10 (SQS SendMessageBatch limit)
            int processedCount = 0;
            List<List<OutboxEvent>> chunks = partition(events, 10);

            for (List<OutboxEvent> chunk : chunks) {
                processedCount += dispatchChunkToSqs(chunk);
            }

            return processedCount;
        });
    }

    private int dispatchChunkToSqs(List<OutboxEvent> chunk) {
        List<SendMessageBatchRequestEntry> entries = new ArrayList<>();
        Map<String, OutboxEvent> eventMap = new HashMap<>();

        for (OutboxEvent event : chunk) {
            byte[] payloadBytes = event.getPayload() != null
                    ? event.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : new byte[0];
            if (payloadBytes.length > 256 * 1024) {
                log.error("OutboxEvent {} payload size ({} bytes) exceeds SQS 256KB limit. Marked FAILED.",
                        event.getId(), payloadBytes.length);
                event.setStatus(OutboxStatus.FAILED);
                outboxEventRepository.save(event);
                continue;
            }

            String entryId = event.getId().toString();
            eventMap.put(entryId, event);

            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put("eventType", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(event.getEventType())
                    .build());
            messageAttributes.put("aggregateType", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(event.getAggregateType())
                    .build());
            messageAttributes.put("aggregateId", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(event.getAggregateId().toString())
                    .build());

            entries.add(SendMessageBatchRequestEntry.builder()
                    .id(entryId)
                    .messageBody(event.getPayload())
                    .messageAttributes(messageAttributes)
                    .build());
        }

        if (entries.isEmpty()) {
            return 0;
        }

        try {
            SendMessageBatchRequest batchRequest = SendMessageBatchRequest.builder()
                    .queueUrl(queueUrl)
                    .entries(entries)
                    .build();

            SendMessageBatchResponse response = sqsClient.sendMessageBatch(batchRequest);

            // Handle successful entries
            for (SendMessageBatchResultEntry successEntry : response.successful()) {
                OutboxEvent event = eventMap.get(successEntry.id());
                if (event != null) {
                    event.setStatus(OutboxStatus.PROCESSED);
                    event.setProcessedAt(Instant.now());
                    outboxEventRepository.save(event);
                }
            }

            // Handle failed entries
            for (BatchResultErrorEntry errorEntry : response.failed()) {
                OutboxEvent event = eventMap.get(errorEntry.id());
                if (event != null) {
                    handleEventPushFailure(event, errorEntry.message());
                }
            }

            return response.successful().size();
        } catch (Exception e) {
            log.error("Batch dispatch to SQS failed for chunk of {} items: {}", chunk.size(), e.getMessage());
            for (OutboxEvent event : chunk) {
                handleEventPushFailure(event, e.getMessage());
            }
            return 0;
        }
    }

    private void handleEventPushFailure(OutboxEvent event, String reason) {
        int newRetryCount = event.getRetryCount() + 1;
        event.setRetryCount(newRetryCount);
        int maxRetries = properties.getWorker().getMaxRetries();

        if (newRetryCount >= maxRetries) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("OutboxEvent {} exceeded max retries ({} >= {}). Marked FAILED. Reason: {}",
                    event.getId(), newRetryCount, maxRetries, reason);
        } else {
            log.warn("OutboxEvent {} push failed (retry {}/{}). Will retry. Reason: {}",
                    event.getId(), newRetryCount, maxRetries, reason);
        }
        outboxEventRepository.save(event);
    }

    private void ensureQueueUrl() {
        if (queueUrl == null || queueUrl.isBlank()) {
            String dlqUrl = sqsConfig.resolveOrCreateDlqUrl(sqsClient);
            String dlqArn = sqsConfig.getQueueArn(sqsClient, dlqUrl);
            this.queueUrl = sqsConfig.resolveOrCreateQueueUrl(sqsClient, sqsConfig.getQueueName(), dlqArn);
        }
    }

    public void setQueueUrl(String queueUrl) {
        this.queueUrl = queueUrl;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
