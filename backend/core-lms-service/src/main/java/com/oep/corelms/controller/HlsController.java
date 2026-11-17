package com.oep.corelms.controller;

import com.oep.corelms.service.HlsPlaylistService;
import com.oep.corelms.service.StudentCourseContentService;
import com.oep.coremodel.entity.course.LessonContent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Adaptive streaming playlists (HLS) for transcoded lesson videos; same authorisation as the file view URL. */
@RestController
@RequiredArgsConstructor
public class HlsController {

    private static final String M3U8 = "application/vnd.apple.mpegurl";

    private final StudentCourseContentService contentService;
    private final HlsPlaylistService playlists;

    @GetMapping("/api/lesson-contents/{contentId}/hls/master.m3u8")
    @Transactional(readOnly = true)
    public ResponseEntity<String> master(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID contentId) {
        LessonContent c = contentService.authorizeContent(contentId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"));
        return ResponseEntity.ok().header("Content-Type", M3U8).cacheControl(CacheControl.noStore()).body(playlists.master(c));
    }

    @GetMapping("/api/lesson-contents/{contentId}/hls/{variant}.m3u8")
    @Transactional(readOnly = true)
    public ResponseEntity<String> variant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID contentId, @PathVariable String variant) {
        LessonContent c = contentService.authorizeContent(contentId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"));
        return ResponseEntity.ok().header("Content-Type", M3U8).cacheControl(CacheControl.noStore()).body(playlists.variant(c, variant));
    }
}
