package com.oep.corelms.service;

import com.oep.corelms.config.MediaStorageConfig;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.core.ResponseInputStream;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oep.media.endpoint")
public class PrivatePlaybackService {
    private static final Pattern HLS_PATH = Pattern.compile("(?:master\\.m3u8|(?:480p|720p)/(?:index\\.m3u8|segment-[0-9]{5,}\\.ts))");
    private static final String PLAYABLE = """
            select e.id, l.id, l.title, v.id, u.email, v.object_key, coalesce(e.access_expires_at < now(), false)
            from enrollments e
            join users u on u.id = e.student_id
            join courses c on c.id = e.course_id
            join course_modules m on m.course_id = c.id
            join lessons l on l.module_id = m.id
            join lesson_contents v on v.lesson_id = l.id
            where e.student_id = ? and c.id = ?
              and ((e.status = 'ACTIVE' and c.lifecycle_status = 'PUBLISHED'
                    and (e.access_expires_at is null or e.access_expires_at >= now()))
                   or (e.status in ('ACTIVE','COMPLETED') and c.lifecycle_status in ('PUBLISHED','ARCHIVED')
                       and e.access_expires_at < now()))
              and u.account_status = 'ACTIVE' and u.role = 'STUDENT'
              and v.content_type = 'VIDEO' and v.upload_status = 'AVAILABLE' and v.duration_seconds > 0
              and v.object_key = concat('media/', cast(v.id as text), '/master.m3u8')
            """;

    private final JdbcTemplate jdbc;
    private final HeartbeatBuffer heartbeat;
    private final S3Client storage;
    private final MediaStorageConfig.Properties properties;
    private final LessonMasteryService mastery;
    private final software.amazon.awssdk.services.s3.presigner.S3Presigner signer;
    private final ArchivedVideoQuota quota;

    public record Video(UUID enrollmentId, UUID lessonId, String title, UUID videoId) {}
    public record Session(UUID streamToken, String watermark, boolean archived) {}
    private record AuthorizedVideo(Video video, String email, String key, boolean archived) {}

    public List<Video> list(UUID actor, UUID course) {
        var unlocked = mastery.readableLessons(actor, course).stream().filter(LessonMasteryService.Lesson::unlocked)
                .map(LessonMasteryService.Lesson::id).collect(java.util.stream.Collectors.toSet());
        return find(actor, course, null).stream().map(AuthorizedVideo::video)
                .filter(video -> unlocked.contains(video.lessonId())).toList();
    }

    public Session open(UUID actor, UUID course, UUID videoId) {
        AuthorizedVideo authorized = requireVideo(actor, course, videoId);
        Video video = authorized.video();
        if (authorized.archived()) quota.requireAvailable(actor);
        return new Session(heartbeat.open(actor, video.enrollmentId(), video.lessonId(), videoId),
                authorized.email() + " · " + actor, authorized.archived());
    }

    public ResponseInputStream<GetObjectResponse> read(UUID actor, UUID course, UUID videoId, UUID token, String path) {
        if (!HLS_PATH.matcher(path).matches()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy học liệu");
        }
        AuthorizedVideo authorized = requireVideo(actor, course, videoId);
        Video video = authorized.video();
        heartbeat.requirePlaybackStream(actor, video.enrollmentId(), video.lessonId(), videoId, token);
        String prefix = authorized.key().substring(0, authorized.key().lastIndexOf('/') + 1);
        var object = storage.getObject(request -> request.bucket(properties.bucket()).key(prefix + path));
        if (authorized.archived() && path.endsWith(".ts")) {
            try {
                String rendition = path.substring(0, path.lastIndexOf('/') + 1);
                String playlist = storage.getObjectAsBytes(request -> request.bucket(properties.bucket())
                        .key(prefix + rendition + "index.m3u8")).asUtf8String();
                quota.reserveSegment(actor, segmentSeconds(playlist, path.substring(rendition.length())));
            } catch (RuntimeException ex) {
                object.abort();
                throw ex;
            }
        }
        return object;
    }

    static int segmentSeconds(String playlist, String segment) {
        double duration = 0;
        for (String entry : playlist.lines().map(String::strip).toList()) {
            if (entry.startsWith("#EXTINF:")) {
                try {
                    duration = Double.parseDouble(entry.substring(8).split(",", 2)[0]);
                } catch (NumberFormatException ex) {
                    duration = 0;
                }
            } else if (!entry.isEmpty() && !entry.startsWith("#")) {
                if (entry.equals(segment) && Double.isFinite(duration) && duration > 0) return (int) Math.ceil(duration);
                duration = 0;
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đoạn video trong danh sách phát");
    }

    public String transcript(UUID actor, UUID course, UUID videoId, UUID token) {
        Video video = requireVideo(actor, course, videoId).video();
        heartbeat.requirePlaybackStream(actor, video.enrollmentId(), video.lessonId(), videoId, token);
        var keys = jdbc.queryForList("""
                select captions.object_key from lesson_contents captions
                join lesson_contents video on video.id = ?
                where captions.lesson_id = video.lesson_id and captions.lo_id = video.lo_id
                  and captions.upload_status = 'AVAILABLE' and captions.content_type = 'FILE'
                  and (captions.object_key like 'media/%.vtt' or captions.object_key like 'media/%.srt')
                order by captions.position, captions.id limit 1
                """, String.class, videoId);
        if (keys.isEmpty()) return "";
        String key = keys.getFirst();
        return storage.getObjectAsBytes(request -> request.bucket(properties.bucket()).key(key)).asUtf8String();
    }

    private AuthorizedVideo requireVideo(UUID actor, UUID course, UUID video) {
        AuthorizedVideo item = find(actor, course, video).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Video chưa mở hoặc quyền học đã hết hạn"));
        mastery.requireReadable(actor, course, item.video().lessonId());
        return item;
    }

    public record Document(UUID id, String filename) {}

    public List<Document> documents(UUID actor, UUID course, UUID lesson) {
        mastery.requireReadable(actor, course, lesson);
        return jdbc.query("""
                select id, file_name from lesson_contents where lesson_id = ?
                  and content_type in ('PDF', 'DOCUMENT') and upload_status = 'AVAILABLE'
                  and object_key like 'media/%'
                order by position, id
                """, (row, index) -> new Document(row.getObject(1, UUID.class), row.getString(2)), lesson);
    }

    public String openDocument(UUID actor, UUID course, UUID lesson, UUID document) {
        mastery.requireReadable(actor, course, lesson);
        String key = jdbc.queryForList("""
                select object_key from lesson_contents where lesson_id = ? and id = ?
                  and content_type in ('PDF', 'DOCUMENT') and upload_status = 'AVAILABLE'
                  and object_key like 'media/%'
                """, String.class, lesson, document).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tài liệu"));
        var signed = signer.presignGetObject(request -> request.signatureDuration(java.time.Duration.ofMinutes(2))
                .getObjectRequest(get -> get.bucket(properties.bucket()).key(key)));
        jdbc.update("""
                insert into lesson_document_reads (enrollment_id, content_id, read_at)
                select e.id, ?, now() from enrollments e where e.student_id = ? and e.course_id = ? and e.status = 'ACTIVE'
                  and (e.access_expires_at is null or e.access_expires_at >= now())
                on conflict do nothing
                """, document, actor, course);
        return signed.url().toExternalForm();
    }

    public ArchivedVideoQuota.Status archivedQuota(UUID actor, UUID course) {
        boolean archived = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from enrollments e join users u on u.id = e.student_id
                  join courses c on c.id = e.course_id where e.student_id = ? and e.course_id = ?
                    and e.status in ('ACTIVE','COMPLETED') and e.access_expires_at < now()
                    and c.lifecycle_status in ('PUBLISHED','ARCHIVED') and u.account_status = 'ACTIVE' and u.role = 'STUDENT')
                """, Boolean.class, actor, course));
        return archived ? quota.status(actor) : null;
    }

    private List<AuthorizedVideo> find(UUID actor, UUID course, UUID video) {
        String query = PLAYABLE + (video == null ? " order by v.position, v.id" : " and v.id = ?");
        Object[] parameters = video == null ? new Object[]{actor, course} : new Object[]{actor, course, video};
        return jdbc.query(query, (row, index) -> new AuthorizedVideo(
                new Video(row.getObject(1, UUID.class), row.getObject(2, UUID.class), row.getString(3), row.getObject(4, UUID.class)),
                row.getString(5), row.getString(6), row.getBoolean(7)), parameters);
    }
}
