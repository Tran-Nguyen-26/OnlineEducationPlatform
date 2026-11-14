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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

@Slf4j
@Component
@ConditionalOnProperty(name = "oep.media.endpoint")
public class LessonMediaWorker {
    private static final Pattern CAPTION_CUE = Pattern.compile("(?m)(\\d{2,}:\\d{2}:\\d{2}[.,]\\d{3})\\s+-->\\s+(\\d{2,}:\\d{2}:\\d{2}[.,]\\d{3})");
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
                select c.id, m.course_id, l.id, c.object_key, c.content_type, c.file_size, c.mime_type, c.file_name
                from lesson_contents c join lessons l on l.id = c.lesson_id
                join course_modules m on m.id = l.module_id
                where c.upload_status = 'UPLOADED' and c.object_key like 'incoming/%'
                order by c.updated_at, c.id limit 1 for update of c skip locked
                """, (row, index) -> new Job(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getObject(3, UUID.class), row.getString(4), row.getString(5), row.getLong(6),
                row.getString(7), row.getString(8)));
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
                if (isCaption(job.fileName(), job.key())) indexCaptions(job, Files.readString(input, StandardCharsets.UTF_8));
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

    private void indexCaptions(Job job, String transcript) {
        List<MatchResult> cues = CAPTION_CUE.matcher(transcript).results().toList();
        if (cues.isEmpty()) throw new IllegalArgumentException("Caption file contains no valid timed cues");
        List<String> chunks = new java.util.ArrayList<>();
        for (int index = 0; index < cues.size(); index++) {
            MatchResult cue = cues.get(index);
            int lineBreak = transcript.indexOf('\n', cue.end());
            if (lineBreak < 0) continue;
            int contentStart = lineBreak + 1;
            int contentEnd = index + 1 < cues.size() ? cues.get(index + 1).start() : transcript.length();
            String text = transcript.substring(contentStart, contentEnd)
                    .replaceAll("(?m)^\\s*\\d+\\s*$", "")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                    .replaceAll("\\s+", " ").trim();
            if (text.isBlank()) continue;
            chunks.add(transcript.substring(cue.start(), cue.end()) + "\n" + text);
        }
        if (chunks.isEmpty()) throw new IllegalArgumentException("Caption file contains no readable text cues");
        jdbc.update("delete from ai_document_chunks where content_id = ?", job.id());
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            int tokenEstimate = Math.max(1, chunk.codePointCount(0, chunk.length()) / 4);
            jdbc.update("""
                    insert into ai_document_chunks
                      (id, course_id, lesson_id, content_id, chunk_index, chunk_text, token_count, created_at)
                    values (gen_random_uuid(), ?, ?, ?, ?, ?, ?, now())
                    """, job.courseId(), job.lessonId(), job.id(), index, chunk, tokenEstimate);
        }
    }

    private boolean isCaption(String fileName, String objectKey) {
        String name = fileName == null ? objectKey : fileName;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".vtt") || lower.endsWith(".srt");
    }

    private void cleanup(Path directory) {
        if (directory == null) return;
        try (var files = Files.walk(directory)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not remove media temporary files", ex);
        }
    }

    private record Job(UUID id, UUID courseId, UUID lessonId, String key, String type,
                       long size, String mime, String fileName) {}
}
