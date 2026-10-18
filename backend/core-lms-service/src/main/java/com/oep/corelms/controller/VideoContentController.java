package com.oep.corelms.controller;

import com.oep.corelms.dto.request.*;
import com.oep.corelms.dto.response.*;
import com.oep.corelms.service.content.VideoContentService;
import com.oep.corelms.service.progress.UploadProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class VideoContentController {

    private final VideoContentService videoContentService;
    private final UploadProgressService uploadProgressService;

    /**
     * 1. Xin Presigned PUT URL để upload video đơn lẻ trực tiếp lên MinIO.
     */
    @PostMapping("/api/contents/{lessonId}/video/presign")
    public ResponseEntity<PresignedUploadResponse> presignUpload(
            @PathVariable UUID lessonId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InitUploadRequest request
    ) {
        UUID instructorId = UUID.fromString(jwt.getSubject());
        PresignedUploadResponse response = videoContentService.initUpload(lessonId, instructorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2. Xác nhận đã upload file lên MinIO hoàn tất (kích hoạt worker xử lý qua RabbitMQ).
     */
    @PostMapping("/api/contents/{contentId}/video/confirm")
    public ResponseEntity<ConfirmUploadResponse> confirmUpload(
            @PathVariable UUID contentId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmUploadRequest request
    ) {
        UUID instructorId = UUID.fromString(jwt.getSubject());
        ConfirmUploadResponse response = videoContentService.confirmUpload(contentId, instructorId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. Theo dõi tiến độ transcode video theo thời gian thực qua Server-Sent Events (SSE).
     */
    @GetMapping(value = "/api/contents/{contentId}/upload-progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getUploadProgress(@PathVariable UUID contentId) {
        // Timeout 30 phút cho quá trình transcode
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        uploadProgressService.register(contentId, emitter);
        return emitter;
    }

    /**
     * 4. Khởi tạo Multipart Upload cho video dung lượng lớn (> 100MB).
     */
    @PostMapping("/api/contents/{lessonId}/video/multipart/init")
    public ResponseEntity<InitMultipartResponse> initMultipart(
            @PathVariable UUID lessonId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InitMultipartRequest request
    ) {
        UUID instructorId = UUID.fromString(jwt.getSubject());
        InitMultipartResponse response = videoContentService.initMultipartUpload(lessonId, instructorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 5. Lấy Presigned URL cho từng part trong Multipart Upload.
     */
    @PostMapping("/api/contents/{contentId}/video/multipart/presign-part")
    public ResponseEntity<PresignedPartResponse> presignPart(
            @PathVariable UUID contentId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PresignPartRequest request
    ) {
        UUID instructorId = UUID.fromString(jwt.getSubject());
        PresignedPartResponse response = videoContentService.presignPartUpload(contentId, instructorId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 6. Hoàn tất Multipart Upload sau khi client upload xong tất cả các part.
     */
    @PostMapping("/api/contents/{contentId}/video/multipart/complete")
    public ResponseEntity<ConfirmUploadResponse> completeMultipart(
            @PathVariable UUID contentId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CompleteMultipartRequest request
    ) {
        UUID instructorId = UUID.fromString(jwt.getSubject());
        ConfirmUploadResponse response = videoContentService.completeMultipartUpload(contentId, instructorId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 7. Lấy stream URL (HLS master playlist) để phát video (dành cho học viên/giảng viên).
     */
    @GetMapping("/api/contents/{contentId}/stream")
    public ResponseEntity<StreamUrlResponse> getStreamUrl(
            @PathVariable UUID contentId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        StreamUrlResponse response = videoContentService.getStreamUrl(contentId, userId);
        return ResponseEntity.ok(response);
    }
}
