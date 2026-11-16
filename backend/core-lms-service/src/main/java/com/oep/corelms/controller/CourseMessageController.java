package com.oep.corelms.controller;

import com.oep.corelms.dto.social.MessageResponse;
import com.oep.corelms.dto.social.PostMessageRequest;
import com.oep.corelms.service.CourseMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/messages")
@RequiredArgsConstructor
public class CourseMessageController {

    private final CourseMessageService messageService;

    @GetMapping
    public List<MessageResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                       @RequestParam(required = false) UUID lessonId) {
        return messageService.list(courseId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"), lessonId);
    }

    @PostMapping
    public MessageResponse post(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                 @Valid @RequestBody PostMessageRequest request) {
        return messageService.post(courseId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"),
                request.content(), request.lessonId());
    }

    public record AttachmentRequest(String fileName, String mimeType, long fileSize) {}

    public record ConfirmRequest(String objectKey, String fileName, String mimeType, long fileSize) {}

    @PostMapping("/{messageId}/attachments/upload-url")
    public CourseMessageService.AttachmentUpload attachmentUploadUrl(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                                                      @PathVariable UUID messageId, @RequestBody AttachmentRequest r) {
        return messageService.requestAttachmentUpload(courseId, messageId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"),
                r.fileName(), r.mimeType(), r.fileSize());
    }

    @PostMapping("/{messageId}/attachments")
    public MessageResponse confirmAttachment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                             @PathVariable UUID messageId, @RequestBody ConfirmRequest r) {
        return messageService.confirmAttachment(courseId, messageId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"),
                r.objectKey(), r.fileName(), r.mimeType(), r.fileSize());
    }
}
