package com.oep.corelms.controller;

import com.oep.corelms.dto.auth.AuthResponse;
import com.oep.corelms.dto.auth.ChangePasswordRequest;
import com.oep.corelms.dto.auth.LoginRequest;
import com.oep.corelms.dto.auth.RefreshTokenRequest;
import com.oep.corelms.dto.auth.RegisterRequest;
import com.oep.corelms.service.AuthService;
import com.oep.corelms.service.JwtService;
import com.oep.coremodel.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest servletRequest;

    @InjectMocks
    private AuthController authController;

    private final UUID sampleUserId = UUID.randomUUID();
    private AuthResponse sampleResponse;

    @BeforeEach
    void setUp() {
        lenient().when(servletRequest.getHeader(HttpHeaders.USER_AGENT)).thenReturn("Mozilla/5.0 TestBrowser");
        AuthResponse.UserSummary summary = new AuthResponse.UserSummary(
                sampleUserId,
                "student@oep.edu.vn",
                "Nguyen Van A",
                UserRole.STUDENT,
                false
        );
        sampleResponse = AuthResponse.of("access-tok", "refresh-tok", 3600L, summary);
    }

    @Test
    void register_shouldReturnCreated() {
        RegisterRequest req = new RegisterRequest("student@oep.edu.vn", "Secret123!", "Nguyen Van A", null, null, null, null);

        when(authService.register(eq(req), any())).thenReturn(sampleResponse);

        ResponseEntity<AuthResponse> res = authController.register(req, servletRequest);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).isEqualTo(sampleResponse);
        verify(authService).register(eq(req), eq("Mozilla/5.0 TestBrowser"));
    }

    @Test
    void login_shouldReturnOk() {
        LoginRequest req = new LoginRequest("student@oep.edu.vn", "Secret123!");

        when(authService.login(eq(req), any())).thenReturn(sampleResponse);

        ResponseEntity<AuthResponse> res = authController.login(req, servletRequest);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isEqualTo(sampleResponse);
    }

    @Test
    void refresh_shouldReturnOk() {
        RefreshTokenRequest req = new RefreshTokenRequest("valid-refresh-tok");

        when(authService.refreshToken(eq(req), any())).thenReturn(sampleResponse);

        ResponseEntity<AuthResponse> res = authController.refresh(req, servletRequest);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isEqualTo(sampleResponse);
    }

    @Test
    void logout_withNonNullRequest_shouldInvokeService() {
        RefreshTokenRequest req = new RefreshTokenRequest("some-refresh-token");

        ResponseEntity<Map<String, String>> res = authController.logout(req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsEntry("message", "Logged out successfully");
        verify(authService).logout("some-refresh-token");
    }

    @Test
    void logout_withNullRequest_shouldReturnOk() {
        ResponseEntity<Map<String, String>> res = authController.logout(null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authService, never()).logout(any());
    }

    @Test
    void changePassword_shouldReturnOk() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(sampleUserId.toString());
        ChangePasswordRequest req = new ChangePasswordRequest("OldPass123!", "NewPass123!");

        ResponseEntity<Map<String, String>> res = authController.changePassword(jwt, req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authService).changePassword(sampleUserId, req);
    }

    @Test
    void jwks_shouldReturnJwkSet() {
        Map<String, Object> jwkSet = Map.of("keys", java.util.List.of());
        when(jwtService.getJwkSetJson()).thenReturn(jwkSet);

        Map<String, Object> res = authController.jwks();

        assertThat(res).isEqualTo(jwkSet);
    }
}
