package com.oep.corelms.service;

import com.oep.corelms.dto.auth.AuthResponse;
import com.oep.corelms.dto.auth.ChangePasswordRequest;
import com.oep.corelms.dto.auth.LoginRequest;
import com.oep.corelms.dto.auth.RefreshTokenRequest;
import com.oep.corelms.dto.auth.RegisterRequest;
import com.oep.corelms.repository.AuthIdentityRepository;
import com.oep.corelms.repository.RefreshTokenRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.entity.identity.RefreshToken;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuthProvider;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit & Security Matrix Tests")
@Tag("security-matrix")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private AuthIdentityRepository authIdentityRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private UserProfile sampleProfile;
    private AuthIdentity sampleIdentity;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenValidityDays", 14L);

        sampleUser = new User();
        sampleUser.setId(UUID.randomUUID());
        sampleUser.setEmail("student@oep.edu.vn");
        sampleUser.setRole(UserRole.STUDENT);
        sampleUser.setAccountStatus(AccountStatus.ACTIVE);

        sampleProfile = new UserProfile();
        sampleProfile.setUserId(sampleUser.getId());
        sampleProfile.setUser(sampleUser);
        sampleProfile.setFullName("Nguyen Van A");

        sampleIdentity = new AuthIdentity();
        sampleIdentity.setId(UUID.randomUUID());
        sampleIdentity.setUser(sampleUser);
        sampleIdentity.setProvider(AuthProvider.LOCAL);
        sampleIdentity.setPasswordHash("hashed_password_123");
        sampleIdentity.setMustChangePassword(false);
    }

    @Nested
    @DisplayName("Registration Tests")
    class RegisterTests {

        @Test
        @DisplayName("Register with valid data succeeds and issues tokens")
        void register_validData_succeeds() {
            RegisterRequest req = new RegisterRequest(
                    "student@oep.edu.vn",
                    "P@ssword123",
                    "Nguyen Van A",
                    null, null, null, null
            );

            when(userRepository.findByEmail("student@oep.edu.vn")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(sampleUser);
            when(passwordEncoder.encode("P@ssword123")).thenReturn("hashed_pwd");
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("access_token_jwt");
            when(jwtService.getAccessTokenValiditySeconds()).thenReturn(900L);

            AuthResponse resp = authService.register(req, "Mozilla/5.0");

            assertNotNull(resp);
            assertEquals("access_token_jwt", resp.accessToken());
            assertEquals("Nguyen Van A", resp.user().fullName());
            assertEquals(UserRole.STUDENT, resp.user().role());

            verify(userRepository).save(any(User.class));
            verify(userProfileRepository).save(any(UserProfile.class));
            verify(authIdentityRepository).save(any(AuthIdentity.class));
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Register with duplicate email throws EMAIL_TAKEN")
        void register_duplicateEmail_throwsException() {
            RegisterRequest req = new RegisterRequest(
                    "student@oep.edu.vn",
                    "P@ssword123",
                    "Nguyen Van A",
                    null, null, null, null
            );

            when(userRepository.findByEmail("student@oep.edu.vn")).thenReturn(Optional.of(sampleUser));

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authService.register(req, "device")
            );

            assertEquals(ErrorCode.EMAIL_TAKEN, ex.getCode());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Login & RBAC Security Tests")
    class LoginTests {

        @Test
        @DisplayName("Login with valid credentials returns tokens")
        void login_validCredentials_succeeds() {
            LoginRequest req = new LoginRequest("student@oep.edu.vn", "P@ssword123");

            when(userRepository.findByEmail("student@oep.edu.vn")).thenReturn(Optional.of(sampleUser));
            when(authIdentityRepository.findByUserIdAndProvider(sampleUser.getId(), AuthProvider.LOCAL))
                    .thenReturn(Optional.of(sampleIdentity));
            when(passwordEncoder.matches("P@ssword123", "hashed_password_123")).thenReturn(true);
            when(userProfileRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleProfile));
            when(jwtService.generateAccessToken(sampleUser)).thenReturn("access_token_jwt");
            when(jwtService.getAccessTokenValiditySeconds()).thenReturn(900L);

            AuthResponse resp = authService.login(req, "Chrome");

            assertNotNull(resp);
            assertEquals("access_token_jwt", resp.accessToken());
            assertEquals(sampleUser.getId(), resp.user().id());
            assertEquals(UserRole.STUDENT, resp.user().role());
        }

        @Test
        @DisplayName("Login with unknown email throws BAD_CREDENTIALS")
        void login_unknownEmail_throwsBadCredentials() {
            LoginRequest req = new LoginRequest("unknown@oep.edu.vn", "P@ssword123");
            when(userRepository.findByEmail("unknown@oep.edu.vn")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authService.login(req, "agent")
            );
            assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getCode());
        }

        @Test
        @DisplayName("Login with disabled account throws ACCOUNT_DISABLED")
        void login_disabledAccount_throwsAccountDisabled() {
            sampleUser.setAccountStatus(AccountStatus.DISABLED);
            LoginRequest req = new LoginRequest("student@oep.edu.vn", "P@ssword123");
            when(userRepository.findByEmail("student@oep.edu.vn")).thenReturn(Optional.of(sampleUser));

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authService.login(req, "agent")
            );
            assertEquals(ErrorCode.ACCOUNT_DISABLED, ex.getCode());
        }

        @Test
        @DisplayName("Login with wrong password throws BAD_CREDENTIALS")
        void login_wrongPassword_throwsBadCredentials() {
            LoginRequest req = new LoginRequest("student@oep.edu.vn", "WrongPassword");

            when(userRepository.findByEmail("student@oep.edu.vn")).thenReturn(Optional.of(sampleUser));
            when(authIdentityRepository.findByUserIdAndProvider(sampleUser.getId(), AuthProvider.LOCAL))
                    .thenReturn(Optional.of(sampleIdentity));
            when(passwordEncoder.matches("WrongPassword", "hashed_password_123")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authService.login(req, "agent")
            );
            assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getCode());
        }
    }

    @Nested
    @DisplayName("Token Rotation & Session Management")
    class TokenRotationTests {

        private String computeSha256Hex(String input) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }

        @Test
        @DisplayName("Refresh token rotates token and invalidates old token")
        void refreshToken_validToken_rotatesSuccessfully() throws Exception {
            String rawToken = "sample-raw-refresh-token";
            String tokenHash = computeSha256Hex(rawToken);

            RefreshToken existing = new RefreshToken();
            existing.setId(UUID.randomUUID());
            existing.setUser(sampleUser);
            existing.setTokenHash(tokenHash);
            existing.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
            existing.setRevoked(false);

            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash))
                    .thenReturn(Optional.of(existing));
            when(userProfileRepository.findById(sampleUser.getId()))
                    .thenReturn(Optional.of(sampleProfile));
            when(authIdentityRepository.findByUserIdAndProvider(sampleUser.getId(), AuthProvider.LOCAL))
                    .thenReturn(Optional.of(sampleIdentity));
            when(jwtService.generateAccessToken(sampleUser)).thenReturn("new_access_token");
            when(jwtService.getAccessTokenValiditySeconds()).thenReturn(900L);

            AuthResponse resp = authService.refreshToken(new RefreshTokenRequest(rawToken), "mobile");

            assertNotNull(resp);
            assertEquals("new_access_token", resp.accessToken());
            assertTrue(existing.isRevoked(), "Old refresh token must be revoked on rotation");
        }

        @Test
        @DisplayName("Refresh token that is expired throws TOKEN_EXPIRED and marks revoked")
        void refreshToken_expired_throwsTokenExpired() throws Exception {
            String rawToken = "expired-raw-token";
            String tokenHash = computeSha256Hex(rawToken);

            RefreshToken expiredToken = new RefreshToken();
            expiredToken.setId(UUID.randomUUID());
            expiredToken.setUser(sampleUser);
            expiredToken.setTokenHash(tokenHash);
            expiredToken.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
            expiredToken.setRevoked(false);

            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash))
                    .thenReturn(Optional.of(expiredToken));

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authService.refreshToken(new RefreshTokenRequest(rawToken), "browser")
            );

            assertEquals(ErrorCode.TOKEN_EXPIRED, ex.getCode());
            assertTrue(expiredToken.isRevoked(), "Expired refresh token must be marked revoked");
        }

        @Test
        @DisplayName("Logout revokes existing refresh token")
        void logout_validToken_revokesToken() throws Exception {
            String rawToken = "valid-token-for-logout";
            String tokenHash = computeSha256Hex(rawToken);

            RefreshToken token = new RefreshToken();
            token.setUser(sampleUser);
            token.setTokenHash(tokenHash);
            token.setRevoked(false);

            when(refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash))
                    .thenReturn(Optional.of(token));

            authService.logout(rawToken);

            assertTrue(token.isRevoked());
            verify(refreshTokenRepository).save(token);
        }
    }

    @Nested
    @DisplayName("Password Change Tests")
    class PasswordChangeTests {

        @Test
        @DisplayName("Change password with correct old password updates hash and revokes all refresh tokens")
        void changePassword_validOldPassword_succeeds() {
            ChangePasswordRequest req = new ChangePasswordRequest("OldPass123", "NewPass456!");
            UUID userId = sampleUser.getId();

            when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
            when(authIdentityRepository.findByUserIdAndProvider(userId, AuthProvider.LOCAL))
                    .thenReturn(Optional.of(sampleIdentity));
            when(passwordEncoder.matches("OldPass123", "hashed_password_123")).thenReturn(true);
            when(passwordEncoder.encode("NewPass456!")).thenReturn("new_hashed_pwd");

            authService.changePassword(userId, req);

            assertEquals("new_hashed_pwd", sampleIdentity.getPasswordHash());
            assertFalse(sampleIdentity.isMustChangePassword());
            verify(authIdentityRepository).save(sampleIdentity);
            verify(refreshTokenRepository).revokeAllByUserId(userId);
        }

        @Test
        @DisplayName("Change password with wrong old password throws BAD_CREDENTIALS")
        void changePassword_wrongOldPassword_throwsBadCredentials() {
            ChangePasswordRequest req = new ChangePasswordRequest("WrongOldPass", "NewPass456!");
            UUID userId = sampleUser.getId();

            when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
            when(authIdentityRepository.findByUserIdAndProvider(userId, AuthProvider.LOCAL))
                    .thenReturn(Optional.of(sampleIdentity));
            when(passwordEncoder.matches("WrongOldPass", "hashed_password_123")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authService.changePassword(userId, req)
            );

            assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getCode());
            verify(refreshTokenRepository, never()).revokeAllByUserId(any());
        }
    }
}
