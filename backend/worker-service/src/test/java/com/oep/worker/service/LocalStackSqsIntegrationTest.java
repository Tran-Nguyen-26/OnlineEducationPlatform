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
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Live Integration Test running directly against LocalStack SQS (http://localhost:4566).
 * Automatically executes if LocalStack is active; skips safely if not running.
 */
class LocalStackSqsIntegrationTest {

    private static final String LOCALSTACK_ENDPOINT = "http://localhost:4566";
    private static SqsClient sqsClient;
    private static SqsConfig sqsConfig;
    private static boolean localStackAvailable = false;

    private OutboxEventRepository outboxEventRepository;
    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private OutboxWorkerProperties properties;
    private ObjectMapper objectMapper;

    private String testQueueUrl;
    private String testDlqUrl;

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

    @BeforeAll
    static void checkLocalStack() {
        try {
            URL url = new URL(LOCALSTACK_ENDPOINT + "/_localstack/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            localStackAvailable = (responseCode == 200);
        } catch (Exception e) {
            localStackAvailable = false;
        }

        if (localStackAvailable) {
            sqsClient = SqsClient.builder()
                    .region(Region.AP_SOUTHEAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                    .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                    .build();

            sqsConfig = new SqsConfig();
            ReflectionTestUtils.setField(sqsConfig, "endpoint", LOCALSTACK_ENDPOINT);
            ReflectionTestUtils.setField(sqsConfig, "region", "ap-southeast-1");
            ReflectionTestUtils.setField(sqsConfig, "accessKey", "test");
            ReflectionTestUtils.setField(sqsConfig, "secretKey", "test");
            ReflectionTestUtils.setField(sqsConfig, "queueName", "localstack-live-queue-" + UUID.randomUUID().toString().substring(0, 8));
            ReflectionTestUtils.setField(sqsConfig, "dlqName", "localstack-live-dlq-" + UUID.randomUUID().toString().substring(0, 8));
            ReflectionTestUtils.setField(sqsConfig, "autoCreateQueues", true);
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (sqsClient != null) {
            sqsClient.close();
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(localStackAvailable, "LocalStack is not reachable at " + LOCALSTACK_ENDPOINT + ", skipping live test");

        objectMapper = new ObjectMapper();
        properties = new OutboxWorkerProperties();
        properties.getWorker().setBatchSize(10);
        properties.getWorker().setMaxRetries(5);
        properties.getConsumer().setMaxRetries(5);
        properties.getConsumer().setMaxMessages(10);
        properties.getConsumer().setPollWaitSeconds(1);

        outboxEventRepository = mock(OutboxEventRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);

        // Resolve or create live queues on LocalStack
        testDlqUrl = sqsConfig.resolveOrCreateDlqUrl(sqsClient);
        String dlqArn = sqsConfig.getQueueArn(sqsClient, testDlqUrl);
        testQueueUrl = sqsConfig.resolveOrCreateQueueUrl(sqsClient, sqsConfig.getQueueName(), dlqArn);
    }

    @AfterEach
    void cleanUpQueues() {
        if (localStackAvailable && sqsClient != null) {
            try {
                if (testQueueUrl != null) sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(testQueueUrl).build());
                if (testDlqUrl != null) sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(testDlqUrl).build());
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("1. LocalStack: Auto-creates Main Queue and DLQ with RedrivePolicy")
    void testLocalStackQueueAndDlqCreation() {
        assertThat(testQueueUrl).isNotBlank().contains("http");
        assertThat(testDlqUrl).isNotBlank().contains("http");

        GetQueueAttributesResponse attrs = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(testQueueUrl)
                .attributeNames(QueueAttributeName.REDRIVE_POLICY)
                .build());

        String redrivePolicy = attrs.attributes().get(QueueAttributeName.REDRIVE_POLICY);
        assertThat(redrivePolicy).isNotNull().contains("deadLetterTargetArn").contains("maxReceiveCount");
    }

    @Test
    @DisplayName("2. LocalStack: Worker dispatches outbox event into real LocalStack SQS queue")
    void testLocalStackWorkerDispatchesToSqs() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OutboxEvent event = new OutboxEvent();
        event.setId(eventId);
        event.setAggregateType("COURSE");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("COURSE_ANNOUNCEMENT");
        event.setPayload(String.format("""
                {
                    "eventType": "COURSE_ANNOUNCEMENT",
                    "title": "LocalStack Test Title",
                    "message": "Message via LocalStack",
                    "recipientUserId": "%s"
                }
                """, userId));
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);

        when(outboxEventRepository.pollPendingWithSkipLocked(anyInt())).thenReturn(List.of(event));

        OutboxPollingWorker worker = new OutboxPollingWorker(
                outboxEventRepository, sqsClient, sqsConfig, properties, objectMapper, new NoOpTransactionManager()
        );
        worker.setQueueUrl(testQueueUrl);

        int dispatched = worker.pollAndDispatchBatch();
        assertThat(dispatched).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSED);

        // Receive directly from LocalStack SQS to verify it actually arrived
        ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(testQueueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(2)
                .build());

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).body()).contains("LocalStack Test Title");
    }

    @Test
    @DisplayName("3. LocalStack: Consumer reads message from LocalStack, creates Notification and acknowledges")
    void testLocalStackConsumerProcessesMessage() {
        UUID userId = UUID.randomUUID();
        String dedupeKey = "QUIZ_PUBLISHED:quiz-101:" + userId;
        String body = String.format("""
                {
                    "eventType": "QUIZ_PUBLISHED",
                    "title": "Quiz via LocalStack",
                    "message": "Start your quiz now",
                    "recipientUserId": "%s",
                    "dedupeKey": "%s"
                }
                """, userId, dedupeKey);

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(testQueueUrl)
                .messageBody(body)
                .build());

        User user = new User();
        user.setId(userId);
        user.setEmail("student@localstack.invalid");

        when(notificationRepository.existsByDedupeKey(dedupeKey)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NotificationSqsConsumer consumer = new NotificationSqsConsumer(
                sqsClient, sqsConfig, properties, notificationRepository, userRepository, objectMapper
        );
        consumer.setQueueUrl(testQueueUrl);
        consumer.setDlqUrl(testDlqUrl);

        int processed = consumer.consumeMessagesOnce();
        assertThat(processed).isEqualTo(1);

        verify(notificationRepository).save(any(Notification.class));

        // Ensure queue is empty now (message was deleted from LocalStack SQS)
        ReceiveMessageResponse emptyCheck = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(testQueueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(1)
                .build());
        assertThat(emptyCheck.messages()).isEmpty();
    }

    @Test
    @DisplayName("4. LocalStack: Consumer routes poisoned message exceeding retries to LocalStack DLQ")
    void testLocalStackConsumerRoutesToDlq() {
        UUID userId = UUID.randomUUID();
        String body = String.format("""
                {
                    "eventType": "POISON_EVENT",
                    "title": "Poison via LocalStack",
                    "recipientUserId": "%s"
                }
                """, userId);

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(testQueueUrl)
                .messageBody(body)
                .build());

        when(userRepository.findById(userId)).thenThrow(new RuntimeException("Simulated Unrecoverable Error"));

        properties.getConsumer().setMaxRetries(1); // 1st attempt exceeds maxRetries

        NotificationSqsConsumer consumer = new NotificationSqsConsumer(
                sqsClient, sqsConfig, properties, notificationRepository, userRepository, objectMapper
        );
        consumer.setQueueUrl(testQueueUrl);
        consumer.setDlqUrl(testDlqUrl);

        int processed = consumer.consumeMessagesOnce();
        assertThat(processed).isEqualTo(0);

        // Verify message arrived in LocalStack DLQ
        ReceiveMessageResponse dlqResponse = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(testDlqUrl)
                .maxNumberOfMessages(1)
                .messageAttributeNames("All")
                .waitTimeSeconds(2)
                .build());

        assertThat(dlqResponse.messages()).hasSize(1);
        Message dlqMsg = dlqResponse.messages().get(0);
        assertThat(dlqMsg.body()).contains("Poison via LocalStack");
        assertThat(dlqMsg.messageAttributes()).containsKey("dlqReason");
        assertThat(dlqMsg.messageAttributes()).containsKey("retryCount");
    }
}
