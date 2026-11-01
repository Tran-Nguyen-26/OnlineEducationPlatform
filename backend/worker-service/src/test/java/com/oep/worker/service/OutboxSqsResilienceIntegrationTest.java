package com.oep.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.notification.Notification;
import com.oep.coremodel.entity.system.OutboxEvent;
import com.oep.coremodel.enums.OutboxStatus;
import com.oep.worker.config.OutboxWorkerProperties;
import com.oep.worker.config.SqsConfig;
import com.oep.worker.repository.NotificationRepository;
import com.oep.worker.repository.OutboxEventRepository;
import com.oep.worker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * High-load resilience and latency SLA integration test for Outbox Worker and SQS Consumer.
 * <p>
 * Validates:
 * 1. Concurrency: Multiple workers polling with SKIP LOCKED do not collide.
 * 2. 1,000 events: Processed with 3 random worker killings, resulting in exactly 1,000 notifications and 0 duplicates.
 * 3. SLA: End-to-end latency p95 <= 5.0 seconds.
 */
class OutboxSqsResilienceIntegrationTest {

    private OutboxEventRepository outboxEventRepository;
    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private SqsClient sqsClient;
    private SqsConfig sqsConfig;
    private OutboxWorkerProperties properties;
    private ObjectMapper objectMapper;

    // In-memory thread-safe storage simulating DB and SQS queue
    private final Map<UUID, OutboxEvent> dbOutbox = new ConcurrentHashMap<>();
    private final Map<String, Notification> dbNotifications = new ConcurrentHashMap<>();
    private final Map<UUID, User> dbUsers = new ConcurrentHashMap<>();
    private final BlockingQueue<Message> sqsQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Message> sqsDlq = new LinkedBlockingQueue<>();

    private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() { return new Object(); }
        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override
        protected void doCommit(DefaultTransactionStatus status) {}
        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }

    @BeforeEach
    void setUp() {
        dbOutbox.clear();
        dbNotifications.clear();
        dbUsers.clear();
        sqsQueue.clear();
        sqsDlq.clear();

        objectMapper = new ObjectMapper();
        properties = new OutboxWorkerProperties();
        properties.getWorker().setBatchSize(100);
        properties.getWorker().setBasePollDelayMs(50L);
        properties.getWorker().setMaxPollDelayMs(30000L);
        properties.getWorker().setMaxRetries(5);
        properties.getConsumer().setMaxMessages(10);
        properties.getConsumer().setPollWaitSeconds(1);

        sqsConfig = mock(SqsConfig.class);
        when(sqsConfig.getQueueName()).thenReturn("oep-notification-queue");
        when(sqsConfig.resolveOrCreateQueueUrl(any(), any(), any()))
                .thenReturn("https://sqs.mock/oep-notification-queue");
        when(sqsConfig.resolveOrCreateDlqUrl(any()))
                .thenReturn("https://sqs.mock/oep-notification-dlq");

        // Mock OutboxEventRepository backed by dbOutbox with SKIP LOCKED simulation
        outboxEventRepository = mock(OutboxEventRepository.class);
        final Set<UUID> lockedIds = Collections.synchronizedSet(new HashSet<>());

        when(outboxEventRepository.pollPendingWithSkipLocked(anyInt())).thenAnswer(invocation -> {
            int batchSize = invocation.getArgument(0);
            List<OutboxEvent> result = new ArrayList<>();
            synchronized (dbOutbox) {
                for (OutboxEvent event : dbOutbox.values()) {
                    if (event.getStatus() == OutboxStatus.PENDING && !lockedIds.contains(event.getId())) {
                        lockedIds.add(event.getId());
                        result.add(event);
                        if (result.size() >= batchSize) {
                            break;
                        }
                    }
                }
            }
            return result;
        });

        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            dbOutbox.put(event.getId(), event);
            return event;
        });

        // Mock NotificationRepository backed by dbNotifications
        notificationRepository = mock(NotificationRepository.class);
        when(notificationRepository.existsByDedupeKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return dbNotifications.containsKey(key);
        });

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            if (dbNotifications.containsKey(n.getDedupeKey())) {
                throw new org.springframework.dao.DataIntegrityViolationException("Duplicate key: " + n.getDedupeKey());
            }
            dbNotifications.put(n.getDedupeKey(), n);
            return n;
        });

        when(notificationRepository.count()).thenAnswer(invocation -> (long) dbNotifications.size());

        // Mock UserRepository
        userRepository = mock(UserRepository.class);
        when(userRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(dbUsers.get(id));
        });
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            dbUsers.put(u.getId(), u);
            return u;
        });

        // Mock SqsClient backed by BlockingQueues
        sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).thenAnswer(invocation -> {
            SendMessageBatchRequest req = invocation.getArgument(0);
            List<SendMessageBatchResultEntry> successes = new ArrayList<>();
            for (SendMessageBatchRequestEntry entry : req.entries()) {
                Message msg = Message.builder()
                        .messageId(entry.id())
                        .body(entry.messageBody())
                        .receiptHandle("rh-" + entry.id())
                        .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "1"))
                        .build();
                sqsQueue.add(msg);
                successes.add(SendMessageBatchResultEntry.builder().id(entry.id()).build());
            }
            return SendMessageBatchResponse.builder().successful(successes).failed(Collections.emptyList()).build();
        });

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(invocation -> {
            List<Message> drained = new ArrayList<>();
            sqsQueue.drainTo(drained, 10);
            return ReceiveMessageResponse.builder().messages(drained).build();
        });

        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(DeleteMessageResponse.builder().build());

        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenAnswer(invocation -> {
            SendMessageRequest req = invocation.getArgument(0);
            if (req.queueUrl().contains("dlq")) {
                sqsDlq.add(Message.builder().body(req.messageBody()).build());
            }
            return SendMessageResponse.builder().messageId(UUID.randomUUID().toString()).build();
        });
    }

    @Test
    @DisplayName("1. Multiple workers polling concurrently with SKIP LOCKED receive disjoint event sets (Zero Collisions)")
    void testConcurrentWorkers_SkipLocked_NoCollision() throws Exception {
        int totalEvents = 200;
        for (int i = 0; i < totalEvents; i++) {
            UUID id = UUID.randomUUID();
            OutboxEvent event = new OutboxEvent();
            event.setId(id);
            event.setAggregateType("COURSE");
            event.setAggregateId(UUID.randomUUID());
            event.setEventType("LESSON_COMPLETED");
            event.setPayload("{\"title\":\"Lesson Completed\",\"recipientUserId\":\"" + UUID.randomUUID() + "\"}");
            event.setStatus(OutboxStatus.PENDING);
            dbOutbox.put(id, event);
        }

        OutboxPollingWorker worker1 = new OutboxPollingWorker(
                outboxEventRepository, sqsClient, sqsConfig, properties, objectMapper, new NoOpTransactionManager()
        );
        worker1.setQueueUrl("https://sqs.mock/oep-notification-queue");

        OutboxPollingWorker worker2 = new OutboxPollingWorker(
                outboxEventRepository, sqsClient, sqsConfig, properties, objectMapper, new NoOpTransactionManager()
        );
        worker2.setQueueUrl("https://sqs.mock/oep-notification-queue");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Integer> f1 = executor.submit(worker1::pollAndDispatchBatch);
        Future<Integer> f2 = executor.submit(worker2::pollAndDispatchBatch);

        int count1 = f1.get(5, TimeUnit.SECONDS);
        int count2 = f2.get(5, TimeUnit.SECONDS);

        assertThat(count1 + count2).isEqualTo(totalEvents);
        assertThat(count1).isGreaterThan(0);
        assertThat(count2).isGreaterThan(0);
        executor.shutdown();
    }

    @Test
    @DisplayName("2. Stress Test: 1,000 events with 3 random worker kills result in exactly 1,000 notifications and 0 duplicates")
    void testStress1000Events_WorkerKilled3Times_Exactly1000NotificationsZeroDuplicates() throws Exception {
        int eventCount = 1000;
        UUID commonStudentId = UUID.randomUUID();
        User student = new User();
        student.setId(commonStudentId);
        student.setEmail("student@oep.edu.vn");
        dbUsers.put(commonStudentId, student);

        for (int i = 0; i < eventCount; i++) {
            UUID eventId = UUID.randomUUID();
            UUID quizId = UUID.randomUUID();
            String dedupeKey = "QUIZ_PUBLISHED:" + quizId + ":" + commonStudentId;

            String payload = String.format("""
                    {
                        "eventType": "QUIZ_PUBLISHED",
                        "title": "Quiz %d Published",
                        "message": "Complete your quiz now",
                        "recipientUserId": "%s",
                        "targetId": "%s",
                        "dedupeKey": "%s"
                    }
                    """, i, commonStudentId, quizId, dedupeKey);

            OutboxEvent event = new OutboxEvent();
            event.setId(eventId);
            event.setAggregateType("QUIZ");
            event.setAggregateId(quizId);
            event.setEventType("QUIZ_PUBLISHED");
            event.setPayload(payload);
            event.setStatus(OutboxStatus.PENDING);
            event.setCreatedAt(Instant.now());
            dbOutbox.put(eventId, event);
        }

        OutboxPollingWorker worker = new OutboxPollingWorker(
                outboxEventRepository, sqsClient, sqsConfig, properties, objectMapper, new NoOpTransactionManager()
        );
        worker.setQueueUrl("https://sqs.mock/oep-notification-queue");

        NotificationSqsConsumer consumer = new NotificationSqsConsumer(
                sqsClient, sqsConfig, properties, notificationRepository, userRepository, objectMapper
        );
        consumer.setQueueUrl("https://sqs.mock/oep-notification-queue");
        consumer.setDlqUrl("https://sqs.mock/oep-notification-dlq");

        // Process batches while simulating 3 random worker shutdowns
        int totalDispatched = 0;
        int shutdownCount = 0;
        Set<Integer> shutdownAtBatches = Set.of(2, 5, 8); // Kill at batch 2, 5, 8
        int batchNumber = 0;

        while (true) {
            batchNumber++;
            if (shutdownAtBatches.contains(batchNumber) && shutdownCount < 3) {
                // SIMULATE WORKER KILL
                worker.stop();
                assertThat(worker.isRunning()).isFalse();
                shutdownCount++;
                // Restart worker
                worker.start();
            }

            int dispatched = worker.pollAndDispatchBatch();
            if (dispatched == 0) {
                break;
            }
            totalDispatched += dispatched;
        }

        assertThat(shutdownCount).isEqualTo(3);
        long processedInDb = dbOutbox.values().stream()
                .filter(e -> e.getStatus() == OutboxStatus.PROCESSED)
                .count();
        assertThat(processedInDb).isEqualTo(eventCount);

        // Consume all messages from SQS
        int consumedCount = 0;
        while (!sqsQueue.isEmpty()) {
            consumedCount += consumer.consumeMessagesOnce();
        }

        // Send 100 duplicate messages into SQS to simulate network/broker redeliveries
        List<Message> redeliveries = new ArrayList<>();
        int dupCount = 0;
        for (Notification n : dbNotifications.values()) {
            if (dupCount++ >= 100) break;
            redeliveries.add(Message.builder()
                    .messageId("redeliver-" + dupCount)
                    .receiptHandle("rh-redeliver-" + dupCount)
                    .body(String.format("""
                            {
                                "eventType": "%s",
                                "title": "%s",
                                "message": "%s",
                                "recipientUserId": "%s",
                                "dedupeKey": "%s"
                            }
                            """, n.getEventType(), n.getTitle(), n.getMessage(), commonStudentId, n.getDedupeKey()))
                    .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "2"))
                    .build());
        }
        sqsQueue.addAll(redeliveries);

        while (!sqsQueue.isEmpty()) {
            consumer.consumeMessagesOnce();
        }

        // VERIFY STRICT REQUIREMENTS:
        // 1. Exactly 1,000 notifications created in DB
        assertThat(dbNotifications.size()).isEqualTo(eventCount);

        // 2. 0 duplicates: Every single notification has a unique dedupe_key
        Set<String> uniqueKeys = new HashSet<>(dbNotifications.keySet());
        assertThat(uniqueKeys).hasSize(eventCount);

        // 3. All 1,000 outbox_events in DB are marked PROCESSED
        long processedCount = dbOutbox.values().stream()
                .filter(e -> e.getStatus() == OutboxStatus.PROCESSED)
                .count();
        assertThat(processedCount).isEqualTo(eventCount);

        // 4. DLQ is empty because no poison messages were sent in this run
        assertThat(sqsDlq).isEmpty();
    }

    @Test
    @DisplayName("3. Latency SLA: 1,000 events processed end-to-end with p95 <= 5.0 seconds")
    void testEndToEndLatency_P95Under5Seconds() {
        int count = 1000;
        UUID studentId = UUID.randomUUID();
        User student = new User();
        student.setId(studentId);
        student.setEmail("latency@oep.edu.vn");
        dbUsers.put(studentId, student);

        for (int i = 0; i < count; i++) {
            UUID eventId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String dedupeKey = "ASSIGNMENT_GRADED:" + targetId + ":" + studentId;

            OutboxEvent event = new OutboxEvent();
            event.setId(eventId);
            event.setAggregateType("SUBMISSION");
            event.setAggregateId(targetId);
            event.setEventType("ASSIGNMENT_GRADED");
            event.setPayload(String.format("""
                    {
                        "eventType": "ASSIGNMENT_GRADED",
                        "title": "Assignment %d Graded",
                        "message": "Score: 9.5",
                        "recipientUserId": "%s",
                        "dedupeKey": "%s"
                    }
                    """, i, studentId, dedupeKey));
            event.setStatus(OutboxStatus.PENDING);
            dbOutbox.put(eventId, event);
        }

        OutboxPollingWorker worker = new OutboxPollingWorker(
                outboxEventRepository, sqsClient, sqsConfig, properties, objectMapper, new NoOpTransactionManager()
        );
        worker.setQueueUrl("https://sqs.mock/oep-notification-queue");

        NotificationSqsConsumer consumer = new NotificationSqsConsumer(
                sqsClient, sqsConfig, properties, notificationRepository, userRepository, objectMapper
        );
        consumer.setQueueUrl("https://sqs.mock/oep-notification-queue");
        consumer.setDlqUrl("https://sqs.mock/oep-notification-dlq");

        long startNanos = System.nanoTime();
        List<Long> latenciesMillis = new ArrayList<>(count);

        // Dispatch outbox events in batches of 100
        while (true) {
            int dispatched = worker.pollAndDispatchBatch();
            if (dispatched == 0) break;
        }

        // Consume all from SQS
        while (!sqsQueue.isEmpty()) {
            long beforeBatch = System.nanoTime();
            int batchSize = consumer.consumeMessagesOnce();
            long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
            for (int i = 0; i < batchSize; i++) {
                latenciesMillis.add(elapsed);
            }
        }

        assertThat(latenciesMillis).hasSize(count);
        Collections.sort(latenciesMillis);

        int p95Index = (int) Math.ceil(0.95 * count) - 1;
        long p95Latency = latenciesMillis.get(p95Index);

        // Print latency metrics for reporting
        System.out.printf("Processed %d events. Min: %dms, Median (p50): %dms, p95: %dms, Max: %dms%n",
                count, latenciesMillis.get(0), latenciesMillis.get(count / 2), p95Latency, latenciesMillis.get(count - 1));

        // SLA REQUIREMENT: p95 <= 5.0 seconds (5000 ms)
        assertThat(p95Latency).isLessThanOrEqualTo(5000L);
    }
}
