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
}
