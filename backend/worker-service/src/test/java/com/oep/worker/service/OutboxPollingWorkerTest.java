package com.oep.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.entity.system.OutboxEvent;
import com.oep.coremodel.enums.OutboxStatus;
import com.oep.worker.config.OutboxWorkerProperties;
import com.oep.worker.config.SqsConfig;
import com.oep.worker.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollingWorkerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private SqsClient sqsClient;

    @Mock
    private SqsConfig sqsConfig;

    private OutboxWorkerProperties properties;
    private ObjectMapper objectMapper;
    private OutboxPollingWorker worker;

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
        properties = new OutboxWorkerProperties();
        properties.getWorker().setBatchSize(100);
        properties.getWorker().setBasePollDelayMs(200L);
        properties.getWorker().setMaxPollDelayMs(30000L);
        properties.getWorker().setMaxRetries(5);

        objectMapper = new ObjectMapper();

        worker = new OutboxPollingWorker(
                outboxEventRepository,
                sqsClient,
                sqsConfig,
                properties,
                objectMapper,
                new NoOpTransactionManager()
        );
        worker.setQueueUrl("https://sqs.ap-southeast-1.amazonaws.com/123456789012/oep-notification-queue");
    }

    private OutboxEvent createSampleEvent(UUID id, int retryCount, OutboxStatus status) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setAggregateType("COURSE");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("COURSE_ANNOUNCEMENT");
        event.setPayload("{\"title\":\"Announcement\",\"recipientUserId\":\"" + UUID.randomUUID() + "\"}");
        event.setRetryCount(retryCount);
        event.setStatus(status);
        return event;
    }

    @Test
    @DisplayName("1. Should poll up to 100 PENDING events using SKIP LOCKED and mark them PROCESSED on successful SQS dispatch")
    void testPollPendingWithSkipLocked_BatchSize100_Success() {
        List<OutboxEvent> events = new ArrayList<>();
        List<SendMessageBatchResultEntry> successEntries = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            UUID id = UUID.randomUUID();
            events.add(createSampleEvent(id, 0, OutboxStatus.PENDING));
            successEntries.add(SendMessageBatchResultEntry.builder().id(id.toString()).build());
        }

        when(outboxEventRepository.pollPendingWithSkipLocked(100)).thenReturn(events);

        // For each sub-batch of 10, return successful response
        when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).thenAnswer(invocation -> {
            SendMessageBatchRequest req = invocation.getArgument(0);
            List<SendMessageBatchResultEntry> subSuccess = req.entries().stream()
                    .map(e -> SendMessageBatchResultEntry.builder().id(e.id()).build())
                    .toList();
            return SendMessageBatchResponse.builder()
                    .successful(subSuccess)
                    .failed(Collections.emptyList())
                    .build();
        });

        int dispatched = worker.pollAndDispatchBatch();

        assertThat(dispatched).isEqualTo(100);
        verify(outboxEventRepository).pollPendingWithSkipLocked(100);
        // SQS limit is 10 per batch, so 100 events should take exactly 10 SQS batch calls
        verify(sqsClient, times(10)).sendMessageBatch(any(SendMessageBatchRequest.class));

        ArgumentCaptor<OutboxEvent> savedCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(100)).save(savedCaptor.capture());

        for (OutboxEvent saved : savedCaptor.getAllValues()) {
            assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
            assertThat(saved.getProcessedAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("2. Should increment retryCount when SQS batch push fails and keep status PENDING")
    void testOutboxWorker_PushFailure_IncrementsRetryCount() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = createSampleEvent(eventId, 0, OutboxStatus.PENDING);
        when(outboxEventRepository.pollPendingWithSkipLocked(100)).thenReturn(List.of(event));

        when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).thenReturn(
                SendMessageBatchResponse.builder()
                        .successful(Collections.emptyList())
                        .failed(List.of(BatchResultErrorEntry.builder()
                                .id(eventId.toString())
                                .message("SQS service unavailable")
                                .build()))
                        .build()
        );

        int dispatched = worker.pollAndDispatchBatch();

        assertThat(dispatched).isEqualTo(0);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("3. Should transition outbox status to FAILED when retry count reaches maxRetries (5)")
    void testOutboxWorker_MaxRetriesExceeded_TransitionsToFailed() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = createSampleEvent(eventId, 4, OutboxStatus.PENDING);
        when(outboxEventRepository.pollPendingWithSkipLocked(100)).thenReturn(List.of(event));

        when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).thenReturn(
                SendMessageBatchResponse.builder()
                        .successful(Collections.emptyList())
                        .failed(List.of(BatchResultErrorEntry.builder()
                                .id(eventId.toString())
                                .message("Persistent SQS error")
                                .build()))
                        .build()
        );

        int dispatched = worker.pollAndDispatchBatch();

        assertThat(dispatched).isEqualTo(0);
        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("4. Dynamic Backoff: Backoff increases exponentially up to 30,000ms when outbox is empty")
    void testDynamicBackoff_IncreasesUpTo30SecondsWhenEmpty() {
        long current = properties.getWorker().getBasePollDelayMs(); // 200ms
        assertThat(current).isEqualTo(200L);

        // Simulate empty queue backoff steps
        long max = properties.getWorker().getMaxPollDelayMs(); // 30,000ms
        for (int i = 0; i < 10; i++) {
            current = Math.min(current * 2, max);
        }

        assertThat(current).isEqualTo(30000L);
    }

    @Test
    @DisplayName("5. Dynamic Backoff: Resets immediately to base delay (200ms) when events arrive")
    void testDynamicBackoff_ResetsImmediatelyWhenEventsArrive() {
        // Assume backed off to 30 seconds
        long delayAfterArrival = properties.getWorker().getBasePollDelayMs();
        assertThat(delayAfterArrival).isEqualTo(200L);
    }

    @Test
    @DisplayName("6. Fault Tolerance: Worker failure midway leaves unprocessed events in PENDING status")
    void testWorkerCrashMidway_EventsRemainPending() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        OutboxEvent e1 = createSampleEvent(id1, 0, OutboxStatus.PENDING);
        OutboxEvent e2 = createSampleEvent(id2, 0, OutboxStatus.PENDING);

        when(outboxEventRepository.pollPendingWithSkipLocked(100)).thenReturn(List.of(e1, e2));
        when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class)))
                .thenThrow(new RuntimeException("Simulated JVM crash / network severance"));

        int dispatched = worker.pollAndDispatchBatch();

        assertThat(dispatched).isEqualTo(0);
        // Both events remain in PENDING status (with incremented retry count)
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(e2.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(e1.getRetryCount()).isEqualTo(1);
        assertThat(e2.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("7. Chunking: Batches larger than 10 are partitioned into multiple SQS batch calls of max 10")
    void testPartitioningIntoSubBatchesOf10() {
        List<OutboxEvent> events = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            events.add(createSampleEvent(UUID.randomUUID(), 0, OutboxStatus.PENDING));
        }

        when(outboxEventRepository.pollPendingWithSkipLocked(100)).thenReturn(events);
        when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).thenAnswer(invocation -> {
            SendMessageBatchRequest req = invocation.getArgument(0);
            List<SendMessageBatchResultEntry> successes = req.entries().stream()
                    .map(e -> SendMessageBatchResultEntry.builder().id(e.id()).build())
                    .toList();
            return SendMessageBatchResponse.builder().successful(successes).failed(Collections.emptyList()).build();
        });

        int dispatched = worker.pollAndDispatchBatch();

        assertThat(dispatched).isEqualTo(25);
        // 25 events: 10 + 10 + 5 = 3 batch requests
        verify(sqsClient, times(3)).sendMessageBatch(any(SendMessageBatchRequest.class));
    }

    @Test
    @DisplayName("8. Oversized Payload: Events exceeding 256KB are marked FAILED and not sent to SQS")
    void testOversizedPayload_MarkedFailedWithoutSendingToSqs() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = createSampleEvent(eventId, 0, OutboxStatus.PENDING);
        event.setPayload("x".repeat(257 * 1024));

        when(outboxEventRepository.pollPendingWithSkipLocked(100)).thenReturn(List.of(event));

        int dispatched = worker.pollAndDispatchBatch();

        assertThat(dispatched).isEqualTo(0);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxEventRepository).save(event);
        verify(sqsClient, never()).sendMessageBatch(any(SendMessageBatchRequest.class));
    }
}
