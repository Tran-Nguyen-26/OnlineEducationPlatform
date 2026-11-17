package com.oep.corelms.controller;

import com.oep.corelms.dto.student.MyCertificateResponse;
import com.oep.corelms.dto.student.ProfileResponse;
import com.oep.corelms.dto.student.UpdateProfileRequest;
import com.oep.corelms.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final com.oep.corelms.service.DataPrivacyService dataPrivacyService;

    @GetMapping("/api/users/me/profile")
    public ProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return profileService.get(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/api/users/me/profile")
    public ProfileResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/api/users/me/certificates")
    public List<MyCertificateResponse> myCertificates(@AuthenticationPrincipal Jwt jwt) {
        return profileService.myCertificates(UUID.fromString(jwt.getSubject()));
    }

    public record AvatarRequest(String fileName, String mimeType, long fileSize) {}

    public record ConfirmAvatarRequest(String objectKey) {}

    @org.springframework.web.bind.annotation.PostMapping("/api/users/me/avatar/upload-url")
    public ProfileService.AvatarUpload avatarUploadUrl(@RequestBody AvatarRequest r) {
        return profileService.requestAvatarUpload(r.fileName(), r.mimeType(), r.fileSize());
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/users/me/avatar/confirm")
    public ProfileResponse confirmAvatar(@AuthenticationPrincipal Jwt jwt, @RequestBody ConfirmAvatarRequest r) {
        return profileService.confirmAvatar(UUID.fromString(jwt.getSubject()), r.objectKey());
    }

    /** Right of access: everything the platform stores about the caller, as a downloadable JSON file. */
    @org.springframework.web.bind.annotation.GetMapping("/api/users/me/export")
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> exportMyData(@AuthenticationPrincipal Jwt jwt) {
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"oep-my-data.json\"")
                .body(dataPrivacyService.export(UUID.fromString(jwt.getSubject())));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/users/me/avatar")
    public ProfileResponse removeAvatar(@AuthenticationPrincipal Jwt jwt) {
        return profileService.removeAvatar(UUID.fromString(jwt.getSubject()));
    }
}
