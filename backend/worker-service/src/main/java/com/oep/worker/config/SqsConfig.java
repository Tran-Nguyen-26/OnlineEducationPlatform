package com.oep.worker.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Getter
@Configuration
public class SqsConfig {

    @Value("${aws.sqs.endpoint:http://localhost:4566}")
    private String endpoint;

    @Value("${aws.region:ap-southeast-1}")
    private String region;

    @Value("${aws.credentials.access-key:test}")
    private String accessKey;

    @Value("${aws.credentials.secret-key:test}")
    private String secretKey;

    @Value("${aws.sqs.queue-name:oep-notification-queue}")
    private String queueName;

    @Value("${aws.sqs.dlq-name:oep-notification-dlq}")
    private String dlqName;

    @Value("${aws.sqs.auto-create-queues:true}")
    private boolean autoCreateQueues;

    @Bean
    @ConditionalOnMissingBean(SqsClient.class)
    public SqsClient sqsClient() {
        log.info("Initializing SqsClient for endpoint={} region={}", endpoint, region);
        var builder = SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    /**
     * Resolves or auto-creates the SQS queue and Dead Letter Queue (DLQ).
     * Automatically sets maxReceiveCount = 5 on the redrive policy for DLQ routing.
     */
    public String resolveOrCreateQueueUrl(SqsClient sqsClient, String name, String dlqArn) {
        try {
            return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).queueUrl();
        } catch (QueueDoesNotExistException e) {
            if (!autoCreateQueues) {
                throw e;
            }
            log.info("Queue {} does not exist, auto-creating on SQS/LocalStack...", name);
            Map<QueueAttributeName, String> attributes = new HashMap<>();
            if (dlqArn != null && !dlqArn.isBlank()) {
                String redrivePolicy = String.format("{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"5\"}", dlqArn);
                attributes.put(QueueAttributeName.REDRIVE_POLICY, redrivePolicy);
            }
            var response = sqsClient.createQueue(CreateQueueRequest.builder()
                    .queueName(name)
                    .attributes(attributes)
                    .build());
            return response.queueUrl();
        }
    }

    public String resolveOrCreateDlqUrl(SqsClient sqsClient) {
        try {
            return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(dlqName).build()).queueUrl();
        } catch (QueueDoesNotExistException e) {
            if (!autoCreateQueues) {
                throw e;
            }
            log.info("DLQ {} does not exist, auto-creating...", dlqName);
            var response = sqsClient.createQueue(CreateQueueRequest.builder()
                    .queueName(dlqName)
                    .build());
            return response.queueUrl();
        }
    }

    public String getQueueArn(SqsClient sqsClient, String queueUrl) {
        try {
            var response = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build());
            return response.attributes().get(QueueAttributeName.QUEUE_ARN);
        } catch (Exception e) {
            log.warn("Could not retrieve Queue ARN for {}: {}", queueUrl, e.getMessage());
            return "";
        }
    }
}
