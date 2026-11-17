package com.oep.corelms.service;

import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.TranscodeStatus;
import com.oep.coremodel.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HlsPlaylistServiceTest {

    private StorageService storage;
    private HlsPlaylistService service;
    private LessonContent ready;

    @BeforeEach
    void setUp() {
        storage = mock(StorageService.class);
        service = new HlsPlaylistService(storage);
        ready = new LessonContent();
        ready.setTranscodeStatus(TranscodeStatus.READY);
        ready.setHlsPrefix("hls/abc");
    }

    @Test
    void masterPlaylistPointsVariantsAtTheApi() {
        when(storage.readText("hls/abc/master.m3u8")).thenReturn(
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720\nv0/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nv1/index.m3u8\n");
        String out = service.master(ready);
        assertTrue(out.contains("\nv0.m3u8\n"));
        assertTrue(out.contains("\nv1.m3u8\n"));
        assertFalse(out.contains("index.m3u8"));
        assertTrue(out.startsWith("#EXTM3U"));
    }

    @Test
    void variantPlaylistSegmentsBecomePresignedUrls() {
        when(storage.readText("hls/abc/v0/index.m3u8")).thenReturn("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg_0000.ts\n#EXT-X-ENDLIST\n");
        when(storage.presignView(anyString())).thenAnswer(i -> "https://s3/" + i.getArgument(0) + "?sig=1");
        String out = service.variant(ready, "v0");
        assertTrue(out.contains("https://s3/hls/abc/v0/seg_0000.ts?sig=1"));
        assertTrue(out.contains("#EXT-X-ENDLIST"));
        assertEquals(1, out.lines().filter(l -> l.startsWith("https://")).count());
    }

    @Test
    void rejectsPathTraversalInVariantName() {
        assertThrows(BusinessException.class, () -> service.variant(ready, "../secret"));
        assertThrows(BusinessException.class, () -> service.variant(ready, "v0/../../x"));
    }

    @Test
    void refusesWhenTranscodeIsNotReady() {
        LessonContent pending = new LessonContent();
        pending.setTranscodeStatus(TranscodeStatus.PROCESSING);
        assertThrows(BusinessException.class, () -> service.master(pending));
    }
}
