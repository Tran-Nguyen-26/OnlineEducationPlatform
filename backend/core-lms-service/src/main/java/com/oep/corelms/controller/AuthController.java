package com.oep.corelms.controller;

import com.oep.corelms.dto.auth.AuthResponse;
import com.oep.corelms.dto.auth.ChangePasswordRequest;
import com.oep.corelms.dto.auth.ForgotPasswordRequest;
import com.oep.corelms.dto.auth.ForgotPasswordResponse;
import com.oep.corelms.dto.auth.ForgotPasswordVerifyOtpRequest;
import com.oep.corelms.dto.auth.ForgotPasswordVerifyOtpResponse;
import com.oep.corelms.dto.auth.LoginRequest;
import com.oep.corelms.dto.auth.RefreshTokenRequest;
import com.oep.corelms.dto.auth.RegisterRequest;
import com.oep.corelms.dto.auth.ResetPasswordRequest;
import com.oep.corelms.dto.instructor.InstructorApplicationRequest;
import com.oep.corelms.dto.instructor.InstructorApplicationResponse;
import com.oep.corelms.service.AuthService;
import com.oep.corelms.service.ForgotPasswordService;
import com.oep.corelms.service.InstructorApplicationService;
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
    private final ForgotPasswordService forgotPasswordService;
    private final InstructorApplicationService instructorApplicationService;

    @PostMapping("/api/auth/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        String deviceInfo = servletRequest.getHeader(HttpHeaders.USER_AGENT);
        AuthResponse response = authService.register(request, deviceInfo);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/auth/register-instructor")
    public ResponseEntity<Map<String, Object>> registerTeacher(
            @Valid @RequestBody InstructorApplicationRequest request
    ) {
        InstructorApplicationResponse response = instructorApplicationService.submitApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "Đơn đăng ký tài khoản giảng viên đã được tiếp nhận thành công. Quản trị viên sẽ xét duyệt và gửi thông tin qua email của bạn.",
                "data", response
        ));
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

    @PostMapping("/api/auth/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        ForgotPasswordResponse response = forgotPasswordService.forgotPassword(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/forgot-password/verify-otp")
    public ResponseEntity<ForgotPasswordVerifyOtpResponse> verifyOtp(
            @Valid @RequestBody ForgotPasswordVerifyOtpRequest request
    ) {
        ForgotPasswordVerifyOtpResponse response = forgotPasswordService.verifyForgotPasswordOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        forgotPasswordService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập bằng mật khẩu mới."));
    }

    @GetMapping(value = {"/.well-known/jwks.json", "/api/auth/jwks.json"}, produces = "application/json")
    public Map<String, Object> jwks() {
        return jwtService.getJwkSetJson();
    }
}
