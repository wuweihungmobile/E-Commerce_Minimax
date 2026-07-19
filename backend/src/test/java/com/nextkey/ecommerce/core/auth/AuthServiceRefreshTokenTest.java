package com.nextkey.ecommerce.core.auth;

import com.nextkey.ecommerce.api.dto.AuthResponse;
import com.nextkey.ecommerce.api.dto.RefreshTokenRequest;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuthService Refresh Token 功能單元測試
 *
 * 測試範圍：
 * - 成功刷新 Token
 * - 無效 Refresh Token
 * - 過期 Refresh Token
 * - 用戶不存在
 * - 用戶被停用
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService: JWT 刷新")
class AuthServiceRefreshTokenTest {

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
    private TenantMemberRepository tenantMemberRepository;

    @InjectMocks
    private AuthService authService;

    // 測試資料
    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_EMAIL = "user@example.com";
    private static final String VALID_REFRESH_TOKEN = "valid-refresh-token";
    private static final String INVALID_REFRESH_TOKEN = "invalid-refresh-token";
    private static final String EXPIRED_REFRESH_TOKEN = "expired-refresh-token";
    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    // ── 成功刷新 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("refreshToken_validToken_returnsNewAuthResponse")
    void refreshToken_validToken_returnsNewAuthResponse() {
        // Arrange
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(VALID_REFRESH_TOKEN)
                .build();

        User user = buildActiveUser(User.UserRole.BUYER);
        Tenant tenant = buildTenant();

        when(jwtTokenService.validateToken(VALID_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenService.isTokenExpired(VALID_REFRESH_TOKEN)).thenReturn(false);
        when(jwtTokenService.getUserId(VALID_REFRESH_TOKEN)).thenReturn(TEST_USER_ID);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(user.getTenantId())).thenReturn(Optional.of(tenant));
        when(jwtTokenService.generateAccessToken(any(UUID.class), anyString(), anyString(), anyString()))
                .thenReturn("new-access-token");
        when(jwtTokenService.generateRefreshToken(any(UUID.class))).thenReturn("new-refresh-token");
        when(jwtTokenService.getAccessTokenExpiration()).thenReturn(1800L);
        when(refreshTokenService.isRefreshTokenValid(any(UUID.class), anyString())).thenReturn(true);

        // Act
        AuthResponse response = authService.refreshToken(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(1800L);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("refreshToken_validTokenWithNullTenantAndNoMembership_usesSystemTenant")
    void refreshToken_validTokenWithNullTenant_usesSystemTenant() {
        // Arrange
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(VALID_REFRESH_TOKEN)
                .build();

        User user = buildActiveUser(User.UserRole.BUYER);
        user.setTenantId(null); // 無租戶

        Tenant systemTenant = Tenant.builder()
                .id(UUID.fromString(AppConstants.SYSTEM_TENANT_ID))
                .name("System")
                .slug("system")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();

        when(jwtTokenService.validateToken(VALID_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenService.isTokenExpired(VALID_REFRESH_TOKEN)).thenReturn(false);
        when(jwtTokenService.getUserId(VALID_REFRESH_TOKEN)).thenReturn(TEST_USER_ID);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(tenantMemberRepository.findByUserId(TEST_USER_ID)).thenReturn(List.of());
        when(tenantRepository.findById(UUID.fromString(AppConstants.SYSTEM_TENANT_ID)))
                .thenReturn(Optional.of(systemTenant));
        when(jwtTokenService.generateAccessToken(any(UUID.class), anyString(), anyString(), anyString()))
                .thenReturn("new-access-token");
        when(jwtTokenService.generateRefreshToken(any(UUID.class))).thenReturn("new-refresh-token");
        when(jwtTokenService.getAccessTokenExpiration()).thenReturn(1800L);
        when(refreshTokenService.isRefreshTokenValid(any(UUID.class), anyString())).thenReturn(true);

        // Act
        AuthResponse response = authService.refreshToken(request);

        // Assert
        assertThat(response).isNotNull();
        verify(tenantRepository).findById(UUID.fromString(AppConstants.SYSTEM_TENANT_ID));
    }

    @Test
    @DisplayName("refreshToken_nullTenantButHasTenantMembership_resolvesFromTenantMember"
            + "（Sprint 99：比照 login() 補上 tenant_members 查詢，避免誤退化為 SYSTEM_TENANT_ID）")
    void refreshToken_nullTenantButHasMembership_resolvesFromTenantMember() {
        // Arrange
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(VALID_REFRESH_TOKEN)
                .build();

        User user = buildActiveUser(User.UserRole.STORE_OWNER);
        user.setTenantId(null); // User.tenantId 未同步，但 tenant_members 有正確關聯

        TenantMember membership = TenantMember.builder()
                .tenantId(TEST_TENANT_ID)
                .userId(TEST_USER_ID)
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
        Tenant tenant = buildTenant();

        when(jwtTokenService.validateToken(VALID_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenService.isTokenExpired(VALID_REFRESH_TOKEN)).thenReturn(false);
        when(jwtTokenService.getUserId(VALID_REFRESH_TOKEN)).thenReturn(TEST_USER_ID);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(tenantMemberRepository.findByUserId(TEST_USER_ID)).thenReturn(List.of(membership));
        when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));
        when(jwtTokenService.generateAccessToken(any(UUID.class), anyString(), anyString(), anyString()))
                .thenReturn("new-access-token");
        when(jwtTokenService.generateRefreshToken(any(UUID.class))).thenReturn("new-refresh-token");
        when(jwtTokenService.getAccessTokenExpiration()).thenReturn(1800L);
        when(refreshTokenService.isRefreshTokenValid(any(UUID.class), anyString())).thenReturn(true);

        // Act
        AuthResponse response = authService.refreshToken(request);

        // Assert
        assertThat(response).isNotNull();
        verify(tenantRepository).findById(TEST_TENANT_ID);
        verify(tenantRepository, never()).findById(UUID.fromString(AppConstants.SYSTEM_TENANT_ID));
    }

    // ── 無效 Refresh Token ───────────────────────────────────────────

    @Test
    @DisplayName("refreshToken_invalidToken_throwsE1003BusinessException")
    void refreshToken_invalidToken_throwsBusinessException() {
        // Arrange
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(INVALID_REFRESH_TOKEN)
                .build();

        when(jwtTokenService.validateToken(INVALID_REFRESH_TOKEN)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1003);
                    assertThat(bex.getMessage()).contains("Invalid refresh token");
                });

        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("refreshToken_tamperedToken_throwsE1003BusinessException")
    void refreshToken_tamperedToken_throwsBusinessException() {
        // Arrange
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("tampered-token")
                .build();

        when(jwtTokenService.validateToken("tampered-token")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1003);
                });
    }

    // ── 過期 Refresh Token ──────────────────────────────────────────

    @Test
    @DisplayName("refreshToken_expiredToken_throwsE1002BusinessException")
    void refreshToken_expiredToken_throwsBusinessException() {
        // Arrange
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(EXPIRED_REFRESH_TOKEN)
                .build();

        when(jwtTokenService.validateToken(EXPIRED_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenService.isTokenExpired(EXPIRED_REFRESH_TOKEN)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1002);
                    assertThat(bex.getMessage()).contains("Refresh token expired");
                });
    }

    @Test
    @DisplayName("refreshToken_expiredToken_neverQueriesUser")
    void refreshToken_expiredToken_neverQueriesUser() {
        // Arrange
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(EXPIRED_REFRESH_TOKEN)
                .build();

        when(jwtTokenService.validateToken(EXPIRED_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenService.isTokenExpired(EXPIRED_REFRESH_TOKEN)).thenReturn(true);

        // Act
        try {
            authService.refreshToken(request);
        } catch (BusinessException e) {
            // 預期的例外
        }

        // Assert
        verify(userRepository, never()).findById(any());
    }

    // ── 用戶不存在 ──────────────────────────────────────────────────

    @Test
    @DisplayName("refreshToken_userNotFound_throwsE1006BusinessException")
    void refreshToken_userNotFound_throwsBusinessException() {
        // Arrange
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(VALID_REFRESH_TOKEN)
                .build();

        when(jwtTokenService.validateToken(VALID_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenService.isTokenExpired(VALID_REFRESH_TOKEN)).thenReturn(false);
        when(jwtTokenService.getUserId(VALID_REFRESH_TOKEN)).thenReturn(TEST_USER_ID);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1006);
                });
    }

    // ── 用戶被停用 ──────────────────────────────────────────────────

    @Test
    @DisplayName("refreshToken_userSuspended_throwsE1004BusinessException")
    void refreshToken_userSuspended_throwsBusinessException() {
        // Arrange
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(VALID_REFRESH_TOKEN)
                .build();

        User suspendedUser = buildActiveUser(User.UserRole.BUYER);
        suspendedUser.setStatus("SUSPENDED");

        when(jwtTokenService.validateToken(VALID_REFRESH_TOKEN)).thenReturn(true);
        when(jwtTokenService.isTokenExpired(VALID_REFRESH_TOKEN)).thenReturn(false);
        when(jwtTokenService.getUserId(VALID_REFRESH_TOKEN)).thenReturn(TEST_USER_ID);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(suspendedUser));

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1004);
                    assertThat(bex.getMessage()).contains("Account not active");
                });
    }

    // ── 輔助方法 ───────────────────────────────────────────────────

    private User buildActiveUser(User.UserRole role) {
        User user = User.builder()
                .email(TEST_EMAIL)
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
