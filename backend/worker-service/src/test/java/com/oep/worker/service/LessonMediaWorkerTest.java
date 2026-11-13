package com.oep.worker.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LessonMediaWorkerTest {
    @Test
    void publishesMasterAndRenditionsBeforeMarkingVideoAvailable() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var storage = mock(S3Client.class);
        var transcoder = mock(HlsTranscoder.class);
        var row = mock(ResultSet.class);
        UUID id = UUID.randomUUID();
        when(row.getObject(1, UUID.class)).thenReturn(id);
        when(row.getString(2)).thenReturn("incoming/course/source.mp4");
        when(row.getString(3)).thenReturn("VIDEO");
        when(row.getLong(4)).thenReturn(3L);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any()))
                .thenAnswer(call -> List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)));
        when(storage.getObject(org.mockito.ArgumentMatchers.<Consumer<GetObjectRequest.Builder>>any(), any(Path.class)))
                .thenAnswer(call -> {
                    Files.write(call.getArgument(1, Path.class), new byte[3]);
                    return null;
                });
        when(transcoder.transcode(any(), any(), any())).thenAnswer(call -> {
            Path output = call.getArgument(1);
            Files.createDirectories(output.resolve("480p"));
            Files.writeString(output.resolve("480p/index.m3u8"), "#EXTM3U");
            Files.writeString(output.resolve("master.m3u8"), "#EXTM3U");
            return 42;
        });

        new LessonMediaWorker(jdbc, storage, transcoder, "private").processNext();

        var ordered = inOrder(storage, jdbc);
        ordered.verify(storage, times(2))
                .putObject(org.mockito.ArgumentMatchers.<Consumer<PutObjectRequest.Builder>>any(), any(RequestBody.class));
        ordered.verify(jdbc).update(contains("upload_status = 'AVAILABLE'"), eq("media/" + id + "/master.m3u8"),
                eq(42), eq(id));
    }
}
