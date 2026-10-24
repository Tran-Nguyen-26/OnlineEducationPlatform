package com.oep.corelms.controller;

import com.oep.corelms.dto.auth.AuthResponse;
import com.oep.corelms.dto.auth.ChangePasswordRequest;
import com.oep.corelms.dto.auth.LoginRequest;
import com.oep.corelms.dto.auth.RefreshTokenRequest;
import com.oep.corelms.dto.auth.RegisterRequest;
import com.oep.corelms.service.AuthService;
import com.oep.corelms.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/api/auth/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        String deviceInfo = servletRequest.getHeader(HttpHeaders.USER_AGENT);
        AuthResponse response = authService.register(request, deviceInfo);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        String deviceInfo = servletRequest.getHeader(HttpHeaders.USER_AGENT);
        AuthResponse response = authService.login(request, deviceInfo);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest servletRequest
    ) {
        String deviceInfo = servletRequest.getHeader(HttpHeaders.USER_AGENT);
        AuthResponse response = authService.refreshToken(request, deviceInfo);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null) {
            authService.logout(request.refreshToken());
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/api/auth/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());
        authService.changePassword(userId, request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    /**
     * JWKS endpoint exposed so api-gateway (and any resource server) can fetch the public key to verify JWTs.
     * Accessible publicly at GET /.well-known/jwks.json and /api/auth/jwks.json
     */
    @GetMapping(value = {"/.well-known/jwks.json", "/api/auth/jwks.json"}, produces = "application/json")
    public Map<String, Object> jwks() {
        return jwtService.getJwkSetJson();
    }
}
