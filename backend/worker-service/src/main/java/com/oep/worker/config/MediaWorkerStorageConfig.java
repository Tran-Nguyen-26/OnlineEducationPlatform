package com.oep.worker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "oep.media.endpoint")
public class MediaWorkerStorageConfig {
    @Bean
    ThreadPoolTaskScheduler lessonMediaScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("lesson-media-");
        return scheduler;
    }

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("worker-scheduled-");
        return scheduler;
    }

    @Bean
    S3Client mediaStorage(@Value("${oep.media.endpoint}") URI endpoint,
                          @Value("${oep.media.access-key}") String accessKey,
                          @Value("${oep.media.secret-key}") String secretKey) {
        return S3Client.builder().endpointOverride(endpoint).region(Region.of("auto")).forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))).build();
    }
}
