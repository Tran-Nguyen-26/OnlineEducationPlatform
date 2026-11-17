package com.oep.corelms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Creates the content bucket on startup. CORS does not need configuring here: this MinIO build
 * answers every preflight with a permissive, origin-reflecting Access-Control-Allow-Origin by
 * default and rejects the PutBucketCors API call entirely ("A header you provided implies
 * functionality that is not implemented") — confirmed both via the AWS SDK and `mc cors set`.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class MinioBucketInitializer implements ApplicationRunner {

    private final S3Client s3Client;

    @Value("${oep.storage.bucket}")
    private String bucket;

    /** dev/local: create the bucket. AWS: the bucket is provisioned by infrastructure, so this is switched off. */
    @Value("${oep.storage.auto-create-bucket:true}")
    private boolean autoCreate;

    /** Comma-separated browser origins allowed to PUT/GET presigned URLs. Applied on real S3 (MinIO ignores bucket CORS). */
    @Value("${oep.storage.cors-origins:}")
    private String corsOrigins;

    @Override
    public void run(ApplicationArguments args) {
        if (!corsOrigins.isBlank()) applyCors();
        if (!autoCreate) return;
        boolean exists = s3Client.listBuckets().buckets().stream().anyMatch(b -> b.name().equals(bucket));
        if (!exists) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created storage bucket '{}'", bucket);
        }
    }

    private void applyCors() {
        try {
            var rule = software.amazon.awssdk.services.s3.model.CORSRule.builder()
                    .allowedOrigins(java.util.Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList())
                    .allowedMethods("GET", "PUT", "HEAD").allowedHeaders("*").exposeHeaders("ETag").maxAgeSeconds(3000).build();
            s3Client.putBucketCors(software.amazon.awssdk.services.s3.model.PutBucketCorsRequest.builder().bucket(bucket)
                    .corsConfiguration(software.amazon.awssdk.services.s3.model.CORSConfiguration.builder().corsRules(rule).build()).build());
            log.info("Applied bucket CORS for origins {}", corsOrigins);
        } catch (Exception e) {
            log.warn("Could not apply bucket CORS (set it in the bucket policy/IaC instead): {}", e.getMessage());
        }
    }
}
