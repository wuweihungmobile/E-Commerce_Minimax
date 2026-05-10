package com.nextkey.ecommerce.core.auth;

import com.nextkey.ecommerce.api.dto.UserInfoResponse;
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
import static org.mockito.Mockito.*;

/**
 * AuthService 取得當前用戶資訊單元測試
 *
 * 測試範圍：
 * - 成功取得用戶資訊
 * - 取得包含租戶資訊的用戶
 * - 用戶不存在拋出 E1006 例外
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService: 取得當前用戶資訊")
class AuthServiceGetCurrentUserTest {

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
    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_PHONE = "+886912345678";
    private static final String TEST_AVATAR_URL = "https://example.com/avatar.png";

    // ── 成功取得用戶資訊 ─────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentUser_validUserId_returnsUserInfo")
    void getCurrentUser_validUserId_returnsUserInfo() {
        // Arrange
        User user = buildActiveUser(User.UserRole.BUYER);
        Tenant tenant = buildTenant();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));

        // Act
        UserInfoResponse response = authService.getCurrentUser(TEST_USER_ID);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(response.getUserType()).isEqualTo("BUYER");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getEmailVerified()).isFalse();
        assertThat(response.getProfile()).isNotNull();
        assertThat(response.getProfile().getDisplayName()).isEqualTo("Test User");
        assertThat(response.getProfile().getPhone()).isEqualTo(TEST_PHONE);
        assertThat(response.getProfile().getAvatarUrl()).isEqualTo(TEST_AVATAR_URL);
        assertThat(response.getTenants()).hasSize(1);
    }

    @Test
    @DisplayName("getCurrentUser_validUserId_returnsCorrectTenantInfo")
    void getCurrentUser_validUserId_returnsCorrectTenantInfo() {
        // Arrange
        User user = buildActiveUser(User.UserRole.SELLER);
        Tenant tenant = buildTenant();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));

        // Act
        UserInfoResponse response = authService.getCurrentUser(TEST_USER_ID);

        // Assert
        assertThat(response.getTenants()).hasSize(1);
        UserInfoResponse.TenantInfo tenantInfo = response.getTenants().get(0);
        assertThat(tenantInfo.getTenantId()).isEqualTo(TEST_TENANT_ID);
        assertThat(tenantInfo.getTenantName()).isEqualTo("Test Tenant");
        assertThat(tenantInfo.getRole()).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("getCurrentUser_userNotFound_throwsE1006Exception")
    void getCurrentUser_userNotFound_throwsE1006Exception() {
        // Arrange
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.getCurrentUser(TEST_USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1006);
                    assertThat(bex.getMessage()).contains("User not found");
                });

        verify(tenantRepository, never()).findById(any());
    }

    // ── 無租戶用戶 ─────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentUser_userWithoutTenant_returnsEmptyTenantList")
    void getCurrentUser_userWithoutTenant_returnsEmptyTenantList() {
        // Arrange
        User user = buildActiveUser(User.UserRole.BUYER);
        user.setTenantId(null); // 無租戶

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

        // Act
        UserInfoResponse response = authService.getCurrentUser(TEST_USER_ID);

        // Assert
        assertThat(response.getTenants()).isEmpty();
        verify(tenantRepository, never()).findById(any());
    }

    // ── 租戶不存在 ─────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentUser_tenantNotFound_returnsEmptyTenantList")
    void getCurrentUser_tenantNotFound_returnsEmptyTenantList() {
        // Arrange
        User user = buildActiveUser(User.UserRole.BUYER);

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.empty());

        // Act
        UserInfoResponse response = authService.getCurrentUser(TEST_USER_ID);

        // Assert
        assertThat(response.getTenants()).isEmpty();
    }

    // ── 不同角色用戶 ────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentUser_sellerUser_returnsCorrectRole")
    void getCurrentUser_sellerUser_returnsCorrectRole() {
        // Arrange
        User user = buildActiveUser(User.UserRole.SELLER);
        Tenant tenant = buildTenant();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));

        // Act
        UserInfoResponse response = authService.getCurrentUser(TEST_USER_ID);

        // Assert
        assertThat(response.getUserType()).isEqualTo("SELLER");
        assertThat(response.getTenants().get(0).getRole()).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("getCurrentUser_hostUser_returnsCorrectRole")
    void getCurrentUser_hostUser_returnsCorrectRole() {
        // Arrange
        User user = buildActiveUser(User.UserRole.HOST);
        Tenant tenant = buildTenant();

        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(TEST_TENANT_ID)).thenReturn(Optional.of(tenant));

        // Act
        UserInfoResponse response = authService.getCurrentUser(TEST_USER_ID);

        // Assert
        assertThat(response.getUserType()).isEqualTo("HOST");
        assertThat(response.getTenants().get(0).getRole()).isEqualTo("HOST");
    }

    // ── 輔助方法 ───────────────────────────────────────────────────

    private User buildActiveUser(User.UserRole role) {
        User user = User.builder()
                .email(TEST_EMAIL)
                .passwordHash("$2a$10$encoded")
                .fullName("Test User")
                .phone(TEST_PHONE)
                .avatarUrl(TEST_AVATAR_URL)
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