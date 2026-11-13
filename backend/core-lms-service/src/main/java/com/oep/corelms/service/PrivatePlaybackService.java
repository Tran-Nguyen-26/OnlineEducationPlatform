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
            select e.id, l.id, l.title, v.id, u.email, v.object_key
            from enrollments e
            join users u on u.id = e.student_id
            join courses c on c.id = e.course_id
            join course_modules m on m.course_id = c.id
            join lessons l on l.module_id = m.id
            join lesson_contents v on v.lesson_id = l.id
            where e.student_id = ? and c.id = ? and e.status = 'ACTIVE'
              and (e.access_expires_at is null or e.access_expires_at >= now())
              and u.account_status = 'ACTIVE' and u.role = 'STUDENT'
              and c.lifecycle_status = 'PUBLISHED'
              and v.content_type = 'VIDEO' and v.upload_status = 'AVAILABLE' and v.duration_seconds > 0
              and v.object_key = concat('media/', cast(v.id as text), '/master.m3u8')
              and not exists (
                  select 1 from course_modules previous_module
                  join lessons previous_lesson on previous_lesson.module_id = previous_module.id
                  where previous_module.course_id = c.id
                    and (previous_module.position, previous_module.id, previous_lesson.position, previous_lesson.id)
                      < (m.position, m.id, l.position, l.id)
              )
            """;

    private final JdbcTemplate jdbc;
    private final HeartbeatBuffer heartbeat;
    private final S3Client storage;
    private final MediaStorageConfig.Properties properties;

    public record Video(UUID enrollmentId, UUID lessonId, String title, UUID videoId) {}
    public record Session(UUID streamToken, String watermark) {}
    private record AuthorizedVideo(Video video, String email, String key) {}

    public List<Video> list(UUID actor, UUID course) {
        return find(actor, course, null).stream().map(AuthorizedVideo::video).toList();
    }

    public Session open(UUID actor, UUID course, UUID videoId) {
        AuthorizedVideo authorized = requireVideo(actor, course, videoId);
        Video video = authorized.video();
        return new Session(heartbeat.open(actor, video.enrollmentId(), video.lessonId(), videoId),
                authorized.email() + " · " + actor);
    }

    public ResponseInputStream<GetObjectResponse> read(UUID actor, UUID course, UUID videoId, UUID token, String path) {
        if (!HLS_PATH.matcher(path).matches()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy học liệu");
        }
        AuthorizedVideo authorized = requireVideo(actor, course, videoId);
        Video video = authorized.video();
        heartbeat.requirePlaybackStream(actor, video.enrollmentId(), video.lessonId(), videoId, token);
        String prefix = authorized.key().substring(0, authorized.key().lastIndexOf('/') + 1);
        return storage.getObject(request -> request.bucket(properties.bucket()).key(prefix + path));
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
        return find(actor, course, video).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Video chưa mở hoặc quyền học đã hết hạn"));
    }

    private List<AuthorizedVideo> find(UUID actor, UUID course, UUID video) {
        String query = PLAYABLE + (video == null ? " order by v.position, v.id" : " and v.id = ?");
        Object[] parameters = video == null ? new Object[]{actor, course} : new Object[]{actor, course, video};
        return jdbc.query(query, (row, index) -> new AuthorizedVideo(
                new Video(row.getObject(1, UUID.class), row.getObject(2, UUID.class), row.getString(3), row.getObject(4, UUID.class)),
                row.getString(5), row.getString(6)), parameters);
    }
}
