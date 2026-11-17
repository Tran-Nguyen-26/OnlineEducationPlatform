package com.oep.worker.video;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.TranscodeStatus;
import com.oep.coremodel.event.VideoTranscodeRequestedEvent;
import com.oep.worker.repository.LessonContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Consumes "video.transcode": downloads the original, builds the HLS ladder with ffmpeg, uploads it under
 * {@code hls/{contentId}/} and records READY (or FAILED, which an admin can retry from the UI). One video at a time per
 * worker instance (prefetch 1): transcoding is CPU heavy and long-running.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscodeListener {

    private final S3Client s3;
    private final VideoTranscoder transcoder;
    private final LessonContentRepository contents;
    private final TransactionTemplate tx;

    @Value("${oep.storage.bucket}")
    private String bucket;

    @RabbitListener(queues = MessagingConstants.QUEUE_VIDEO, containerFactory = "videoListenerFactory")
    public void onTranscodeRequested(VideoTranscodeRequestedEvent event) {
        log.info("Transcoding content {} ({})", event.contentId(), event.objectKey());
        setStatus(event.contentId(), TranscodeStatus.PROCESSING, null);
        Path work = null;
        try {
            work = Files.createTempDirectory("oep-transcode-");
            Path source = work.resolve("source");
            try (var in = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(event.objectKey()).build())) {
                Files.copy(in, source);
            }
            Path out = work.resolve("hls");
            Files.createDirectories(out);
            transcoder.toHls(source, out);

            String prefix = "hls/" + event.contentId();
            upload(out, prefix);
            setStatus(event.contentId(), TranscodeStatus.READY, prefix);
            log.info("Content {} is ready for adaptive streaming", event.contentId());
        } catch (Exception e) {
            // not rethrown: re-delivering would repeat an expensive failure; the admin can retry from the UI
            log.error("Transcoding of {} failed: {}", event.contentId(), e.toString());
            setStatus(event.contentId(), TranscodeStatus.FAILED, null);
        } finally {
            if (work != null) deleteQuietly(work);
        }
    }

    private void upload(Path outDir, String prefix) throws IOException {
        try (Stream<Path> files = Files.walk(outDir)) {
            for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String rel = outDir.relativize(f).toString().replace('\\', '/');
                String type = rel.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : rel.endsWith(".ts") ? "video/mp2t" : "application/octet-stream";
                s3.putObject(PutObjectRequest.builder().bucket(bucket).key(prefix + "/" + rel).contentType(type).build(), RequestBody.fromFile(f));
            }
        }
    }

    private void setStatus(java.util.UUID contentId, TranscodeStatus status, String prefix) {
        tx.executeWithoutResult(s -> contents.findById(contentId).ifPresent((LessonContent c) -> {
            c.setTranscodeStatus(status);
            if (prefix != null) c.setHlsPrefix(prefix);
            contents.save(c);
        }));
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) { /* best effort */ }
    }
}
