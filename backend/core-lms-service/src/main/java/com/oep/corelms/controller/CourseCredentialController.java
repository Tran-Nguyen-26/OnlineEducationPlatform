package com.oep.corelms.controller;

import com.oep.corelms.service.BadgeSigner;
import com.oep.corelms.service.CourseCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oep.badges.signing-key-file")
public class CourseCredentialController {
    private final CourseCredentialService credentials;
    private final BadgeSigner signer;

    @GetMapping("/api/v1/courses/{courseId}/credential")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<CourseCredentialService.Credential> owned(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(credentials.owned(UUID.fromString(jwt.getSubject()), courseId));
    }

    @PostMapping("/api/v1/courses/{courseId}/credential")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<CourseCredentialService.Credential> issue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(credentials.issue(UUID.fromString(jwt.getSubject()), courseId));
    }

    @GetMapping("/api/public/credentials/{id}")
    public CourseCredentialService.Verification verify(@PathVariable UUID id) {
        return credentials.verify(id);
    }

    @GetMapping("/api/public/credentials/key")
    public Map<String, Object> key() { return signer.publicKey(); }

    @GetMapping("/api/public/credentials/issuer")
    public Map<String, Object> issuer() { return signer.issuer(); }
}
