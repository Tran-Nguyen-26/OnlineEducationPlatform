package com.oep.corelms.controller;

import com.oep.corelms.service.PrivatePlaybackService;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/playback")
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = "oep.media.endpoint")
@RequiredArgsConstructor
public class PrivatePlaybackController {
    private final PrivatePlaybackService playback;
    private final com.oep.corelms.service.LessonMasteryService mastery;

    @GetMapping("/lessons")
    public List<com.oep.corelms.service.LessonMasteryService.Lesson> lessons(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return mastery.lessons(UUID.fromString(jwt.getSubject()), courseId);
    }

    @GetMapping("/lessons/{lessonId}/documents")
    public List<PrivatePlaybackService.Document> documents(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
            @PathVariable UUID lessonId) {
        return playback.documents(UUID.fromString(jwt.getSubject()), courseId, lessonId);
    }

    @PostMapping("/lessons/{lessonId}/documents/{documentId}")
    public ResponseEntity<java.util.Map<String, String>> openDocument(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId, @PathVariable UUID lessonId, @PathVariable UUID documentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(java.util.Map.of("url", playback.openDocument(UUID.fromString(jwt.getSubject()), courseId, lessonId, documentId)));
    }

    @GetMapping
    public List<PrivatePlaybackService.Video> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return playback.list(UUID.fromString(jwt.getSubject()), courseId);
    }

    @PostMapping("/{videoId}")
    public ResponseEntity<PrivatePlaybackService.Session> open(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID courseId, @PathVariable UUID videoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(playback.open(UUID.fromString(jwt.getSubject()), courseId, videoId));
    }

    @GetMapping("/{videoId}/{*asset}")
    public ResponseEntity<StreamingResponseBody> asset(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
            @PathVariable UUID videoId, @PathVariable String asset, @RequestHeader("X-Playback-Session") UUID token) {
        String path = asset.startsWith("/") ? asset.substring(1) : asset;
        try {
            var object = playback.read(UUID.fromString(jwt.getSubject()), courseId, videoId, token, path);
            StreamingResponseBody body = output -> {
                try (object) {
                    object.transferTo(output);
                }
            };
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType(path.endsWith(".m3u8")
                            ? "application/vnd.apple.mpegurl" : "video/mp2t"))
                    .contentLength(object.response().contentLength()).body(body);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy học liệu");
            throw ex;
        }
    }

    @GetMapping("/{videoId}/transcript")
    public ResponseEntity<String> transcript(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
            @PathVariable UUID videoId, @RequestHeader("X-Playback-Session") UUID token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_PLAIN)
                .body(playback.transcript(UUID.fromString(jwt.getSubject()), courseId, videoId, token));
    }
}
