package com.nextkey.ecommerce.core.auth;

import com.nextkey.ecommerce.api.dto.AuthResponse;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.infrastructure.security.LoginAttemptService;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuthService 登入功能單元測試
 *
 * 測試範圍：
 * - 成功登入流程
 * - 錯誤密碼拒絕
 * - 帳號不存在
 * - 帳號被停用
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService: 會員登入")
class AuthServiceLoginTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService authService;

    // 測試資料
    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_PASSWORD = "SecurePass123";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedHash";
    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    // ── 成功登入 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("login_success_returnsAuthResponse")
    void login_success_returnsAuthResponse() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        User user = buildActiveUser(User.UserRole.BUYER);
        Tenant tenant = buildTenant();

        when(userRepository.findByEmailAndStatus(TEST_EMAIL, "ACTIVE")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(TEST_PASSWORD, user.getPasswordHash())).thenReturn(true);
        when(tenantRepository.findById(any(UUID.class))).thenReturn(Optional.of(tenant));
        when(jwtTokenService.generateAccessToken(any(UUID.class), anyString(), anyString(), anyString()))
                .thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");
        when(jwtTokenService.getAccessTokenExpiration()).thenReturn(1800L);
        doNothing().when(refreshTokenService).storeRefreshToken(any(UUID.class), anyString());

        // Act
        AuthResponse response = authService.login(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(1800L);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getUser().getEmail()).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("login_success_updatesLastLoginAt")
    void login_success_updatesLastLoginAt() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        User user = buildActiveUser(User.UserRole.BUYER);
        Instant beforeLogin = user.getLastLoginAt();

        Tenant tenant = buildTenant();

        when(userRepository.findByEmailAndStatus(TEST_EMAIL, "ACTIVE")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(TEST_PASSWORD, user.getPasswordHash())).thenReturn(true);
        when(tenantRepository.findById(any(UUID.class))).thenReturn(Optional.of(tenant));
        when(jwtTokenService.generateAccessToken(any(UUID.class), anyString(), anyString(), anyString()))
                .thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");
        when(jwtTokenService.getAccessTokenExpiration()).thenReturn(1800L);
        doNothing().when(refreshTokenService).storeRefreshToken(any(UUID.class), anyString());

        // Act
        authService.login(request);

        // Assert
        verify(userRepository).save(any(User.class));
        // lastLoginAt 應該被更新
        assertThat(user.getLastLoginAt()).isAfterOrEqualTo(beforeLogin);
    }

    // ── 錯誤密碼 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("login_wrongPassword_throwsE1001BusinessException")
    void login_wrongPassword_throwsBusinessException() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password("WrongPassword123")
                .build();

        User user = buildActiveUser(User.UserRole.BUYER);

        when(userRepository.findByEmailAndStatus(TEST_EMAIL, "ACTIVE")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword123", user.getPasswordHash())).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1001);
                    assertThat(bex.getFullCode()).isEqualTo("E-1001");
                });
    }

    @Test
    @DisplayName("login_wrongPassword_neverGeneratesToken")
    void login_wrongPassword_neverGeneratesToken() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password("WrongPassword123")
                .build();

        User user = buildActiveUser(User.UserRole.BUYER);

        when(userRepository.findByEmailAndStatus(TEST_EMAIL, "ACTIVE")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword123", user.getPasswordHash())).thenReturn(false);

        // Act
        try {
            authService.login(request);
        } catch (BusinessException e) {
            // 預期的例外
        }

        // Assert
        verify(jwtTokenService, never()).generateAccessToken(any(), any(), any(), any());
        verify(jwtTokenService, never()).generateRefreshToken(any());
    }

    // ── 帳號不存在 ───────────────────────────────────────────────────

    @Test
    @DisplayName("login_emailNotFound_throwsE1001BusinessException")
    void login_emailNotFound_throwsBusinessException() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password(TEST_PASSWORD)
                .build();

        when(userRepository.findByEmailAndStatus("nonexistent@example.com", "ACTIVE"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1001);
                });

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenService, never()).generateAccessToken(any(), any(), any(), any());
    }

    // ── 帳號被停用 ──────────────────────────────────────────────────

    @Test
    @DisplayName("login_userStatusSuspended_throwsBusinessException")
    void login_userStatusSuspended_throwsBusinessException() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        // findByEmailAndStatus 不會找到 SUSPENDED 的用戶
        when(userRepository.findByEmailAndStatus(TEST_EMAIL, "ACTIVE"))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1001);
                });
    }

    // ── 無密碼哈希 ───────────────────────────────────────────────────

    @Test
    @DisplayName("login_nullPasswordHash_throwsE1001BusinessException")
    void login_nullPasswordHash_throwsBusinessException() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();

        User user = buildActiveUser(User.UserRole.BUYER);
        user.setPasswordHash(null); // 模擬 OAuth 用戶或密碼未設置

        when(userRepository.findByEmailAndStatus(TEST_EMAIL, "ACTIVE")).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1001);
                });
    }

    // ── 輔助方法 ─────────────────────────────────────────────────────

    private User buildActiveUser(User.UserRole role) {
        User user = User.builder()
                .email(TEST_EMAIL)
                .passwordHash(ENCODED_PASSWORD)
                .fullName("Test User")
                .role(role)
                .status("ACTIVE")
                .emailVerified(false)
                .build();
        user.setId(TEST_USER_ID);
        user.setTenantId(TEST_TENANT_ID);
        user.setCreatedAt(Instant.now());
        user.setLastLoginAt(Instant.now().minusSeconds(3600)); // 1小時前
        return user;
    }

    private Tenant buildTenant() {
        return Tenant.builder()
                .id(TEST_TENANT_ID)
                .name("Test Tenant")
                .slug("test-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
    }
}
