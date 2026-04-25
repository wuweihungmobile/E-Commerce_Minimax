package com.nextkey.ecommerce.core.auth;

import com.nextkey.ecommerce.api.dto.LogoutRequest;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
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
 * AuthService 登出功能單元測試
 *
 * 測試範圍：
 * - 登出並失效特定 Refresh Token
 * - 登出並失效所有 Refresh Token
 * - 無效用戶 ID 不拋例外
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService: 會員登出")
class AuthServiceLogoutTest {

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

    @InjectMocks
    private AuthService authService;

    // 測試資料
    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final String TEST_REFRESH_TOKEN = "valid-refresh-token";

    // ── 特定 Token 登出 ───────────────────────────────────────────────

    @Test
    @DisplayName("logout_withSpecificRefreshToken_blacklistsToken")
    void logout_withSpecificRefreshToken_blacklistsToken() {
        // Arrange
        LogoutRequest request = LogoutRequest.builder()
                .refreshToken(TEST_REFRESH_TOKEN)
                .build();

        doNothing().when(refreshTokenService).blacklistRefreshToken(any(UUID.class), anyString());

        // Act
        authService.logout(TEST_USER_ID, request);

        // Assert
        verify(refreshTokenService).blacklistRefreshToken(TEST_USER_ID, TEST_REFRESH_TOKEN);
        verify(refreshTokenService, never()).blacklistAllRefreshTokens(any(UUID.class));
    }

    @Test
    @DisplayName("logout_withSpecificRefreshToken_doesNotQueryUserRepository")
    void logout_withSpecificRefreshToken_doesNotQueryUserRepository() {
        // Arrange
        LogoutRequest request = LogoutRequest.builder()
                .refreshToken(TEST_REFRESH_TOKEN)
                .build();

        doNothing().when(refreshTokenService).blacklistRefreshToken(any(UUID.class), anyString());

        // Act
        authService.logout(TEST_USER_ID, request);

        // Assert - 登出只需要失效 token，不需要查詢用戶
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).findByEmailAndStatus(anyString(), anyString());
    }

    // ── 失效所有 Token ───────────────────────────────────────────────

    @Test
    @DisplayName("logout_withNullRefreshToken_blacklistsAllTokens")
    void logout_withNullRefreshToken_blacklistsAllTokens() {
        // Arrange
        LogoutRequest request = LogoutRequest.builder()
                .refreshToken(null)
                .build();

        doNothing().when(refreshTokenService).blacklistAllRefreshTokens(any(UUID.class));

        // Act
        authService.logout(TEST_USER_ID, request);

        // Assert
        verify(refreshTokenService).blacklistAllRefreshTokens(TEST_USER_ID);
        verify(refreshTokenService, never()).blacklistRefreshToken(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("logout_withEmptyRefreshToken_blacklistsAllTokens")
    void logout_withEmptyRefreshToken_blacklistsAllTokens() {
        // Arrange
        LogoutRequest request = LogoutRequest.builder()
                .refreshToken("")
                .build();

        doNothing().when(refreshTokenService).blacklistAllRefreshTokens(any(UUID.class));

        // Act
        authService.logout(TEST_USER_ID, request);

        // Assert
        verify(refreshTokenService).blacklistAllRefreshTokens(TEST_USER_ID);
    }

    // ── 無效用戶 ID ─────────────────────────────────────────────────

    @Test
    @DisplayName("logout_invalidUserId_noException")
    void logout_invalidUserId_noException() {
        // Arrange
        UUID invalidUserId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        LogoutRequest request = LogoutRequest.builder()
                .refreshToken(TEST_REFRESH_TOKEN)
                .build();

        // 即使用戶不存在，logout 也不應該拋例外
        // 因為 refreshToken 已經失效，無需驗證用戶

        // Act & Assert - 不拋例外
        assertThatCode(() -> authService.logout(invalidUserId, request))
                .doesNotThrowAnyException();

        verify(refreshTokenService).blacklistRefreshToken(invalidUserId, TEST_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("logout_withNullUserId_noException")
    void logout_withNullUserId_noException() {
        // Arrange
        LogoutRequest request = LogoutRequest.builder()
                .refreshToken(TEST_REFRESH_TOKEN)
                .build();

        // Act & Assert - null userId 也不拋例外（理論上不應該發生）
        // 但 logout 方法只調用 refreshTokenService，不需要用戶資訊
        assertThatCode(() -> authService.logout(null, request))
                .doesNotThrowAnyException();
    }

    // ── 輔助方法 ───────────────────────────────────────────────────

    private User buildActiveUser(User.UserRole role) {
        User user = User.builder()
                .email("user@example.com")
                .passwordHash("$2a$10$encoded")
                .fullName("Test User")
                .role(role)
                .status("ACTIVE")
                .emailVerified(false)
                .build();
        user.setId(TEST_USER_ID);
        user.setTenantId(TEST_TENANT_ID);
        user.setCreatedAt(Instant.now());
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