package com.oep.corelms.service;

import com.oep.coremodel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PrivatePlaybackServiceTest {
    @Test
    void rejectsPathsOutsideTheGeneratedMediaFilesBeforeStorageAccess() {
        var database = mock(JdbcTemplate.class);
        var heartbeat = mock(HeartbeatBuffer.class);
        var storage = mock(S3Client.class);
        var playback = new PrivatePlaybackService(database, heartbeat, storage, null);
        UUID id = UUID.randomUUID();
        for (String path : List.of("../master.m3u8", "480p/../../master.m3u8", "1080p/index.m3u8", "source.mp4")) {
            assertThatThrownBy(() -> playback.read(id, id, id, id, path)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(database, heartbeat, storage);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsAStreamAfterAnotherDeviceTakesOver() {
        var redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        UUID actor = UUID.randomUUID(), enrollment = UUID.randomUUID(), lesson = UUID.randomUUID();
        UUID video = UUID.randomUUID(), current = UUID.randomUUID();
        when(hash.multiGet(anyString(), eq(List.of("token", "enrollment", "lesson", "content"))))
                .thenReturn(List.of(current.toString(), enrollment.toString(), lesson.toString(), video.toString()));
        var heartbeat = new HeartbeatBuffer(redis, null);
        heartbeat.requirePlaybackStream(actor, enrollment, lesson, video, current);
        assertThatThrownBy(() -> heartbeat.requirePlaybackStream(actor, enrollment, lesson, video, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("replaced");
    }
}
