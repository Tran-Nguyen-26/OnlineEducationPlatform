package com.oep.corelms.controller;

import com.oep.corelms.dto.identity.MyProfileDtos;
import com.oep.corelms.service.MyProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/profile")
@RequiredArgsConstructor
public class MyProfileController {
    private final MyProfileService profiles;

    @GetMapping
    public ResponseEntity<MyProfileDtos.Response> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(profiles.get(actor(jwt)));
    }

    @PatchMapping
    public ResponseEntity<MyProfileDtos.Response> update(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody MyProfileDtos.UpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(profiles.update(actor(jwt), request));
    }

    @PostMapping("/avatar")
    public ResponseEntity<MyProfileDtos.AvatarUploadTicket> initiateAvatar(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MyProfileDtos.AvatarUploadRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(profiles.initiateAvatar(actor(jwt), request));
    }

    @PostMapping("/avatar/confirm")
    public ResponseEntity<MyProfileDtos.Response> confirmAvatar(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MyProfileDtos.AvatarConfirmRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(profiles.confirmAvatar(actor(jwt), request));
    }

    private UUID actor(Jwt jwt) {
        if (jwt == null) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        return UUID.fromString(jwt.getSubject());
    }
}
