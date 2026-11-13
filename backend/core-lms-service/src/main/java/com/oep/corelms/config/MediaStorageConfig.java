package com.oep.corelms.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "oep.media.endpoint")
@EnableConfigurationProperties(MediaStorageConfig.Properties.class)
public class MediaStorageConfig {
    @ConfigurationProperties("oep.media")
    public record Properties(URI endpoint, String bucket, String accessKey, String secretKey) {}

    @Bean
    S3Presigner mediaPresigner(Properties properties) {
        return S3Presigner.builder().endpointOverride(properties.endpoint()).region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(credentials(properties)).build();
    }

    @Bean
    S3Client mediaStorage(Properties properties) {
        return S3Client.builder().endpointOverride(properties.endpoint()).region(Region.of("auto"))
                .forcePathStyle(true).credentialsProvider(credentials(properties)).build();
    }

    private StaticCredentialsProvider credentials(Properties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
