package com.oep.worker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "oep.media.endpoint")
public class LessonMediaWorker {
    private final JdbcTemplate jdbc;
    private final S3Client storage;
    private final HlsTranscoder transcoder;
    private final String bucket;

    public LessonMediaWorker(JdbcTemplate jdbc, S3Client storage, HlsTranscoder transcoder,
                             @Value("${oep.media.bucket}") String bucket) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.transcoder = transcoder;
        this.bucket = bucket;
    }

    @Scheduled(fixedDelayString = "${oep.media.poll-delay-ms:5000}", scheduler = "lessonMediaScheduler")
    @Transactional
    public void processNext() {
        var jobs = jdbc.query("""
                select id, object_key, content_type, file_size, mime_type from lesson_contents
                where upload_status = 'UPLOADED' and object_key like 'incoming/%'
                order by updated_at, id limit 1 for update skip locked
                """, (row, index) -> new Job(row.getObject(1, UUID.class), row.getString(2),
                row.getString(3), row.getLong(4), row.getString(5)));
        if (jobs.isEmpty()) return;
        Job job = jobs.getFirst();
        Path temporary = null;
        try {
            temporary = Files.createTempDirectory("oep-media-");
            Path input = temporary.resolve("source");
            storage.getObject(request -> request.bucket(bucket).key(job.key()), input);
            if (Files.size(input) != job.size()) throw new IOException("Unexpected object size");
            String destination = "media/" + job.id();
            Integer duration = null;
            if ("VIDEO".equals(job.type())) {
                Path output = temporary.resolve("hls");
                duration = transcoder.transcode(input, output, temporary.resolve("ffmpeg.log"));
                try (var files = Files.walk(output)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        String relative = output.relativize(file).toString().replace('\\', '/');
                        upload(destination + "/" + relative, file,
                                relative.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t");
                    }
                }
                destination += "/master.m3u8";
            } else {
                destination += "/" + job.key().substring(job.key().lastIndexOf('/') + 1);
                upload(destination, input, job.mime());
            }
            jdbc.update("""
                    update lesson_contents set object_key = ?, duration_seconds = ?, upload_status = 'AVAILABLE', updated_at = now()
                    where id = ?
                    """, destination, duration, job.id());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Media processing interrupted", ex);
        } catch (Exception ex) {
            log.warn("Media processing failed for content {}", job.id(), ex);
            jdbc.update("update lesson_contents set upload_status = 'FAILED', updated_at = now() where id = ?", job.id());
        } finally {
            cleanup(temporary);
        }
    }

    private void upload(String key, Path path, String type) {
        storage.putObject(request -> request.bucket(bucket).key(key).contentType(type), RequestBody.fromFile(path));
    }

    private void cleanup(Path directory) {
        if (directory == null) return;
        try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not remove media temporary files", ex);
        }
    }

    private record Job(UUID id, String key, String type, long size, String mime) {}
}
