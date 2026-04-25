package com.nextkey.ecommerce.core.auth;

import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.api.dto.RegisterResponse;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * US-M03-001 會員註冊 - 單元測試
 *
 * 驗證範圍：
 * - AC-M03-001-1: 成功註冊，回傳 RegisterResponse
 * - AC-M03-001-2: Email 已被註冊，拋出 E_1005 BusinessException
 * - userType 對應 User.UserRole 映射正確性
 * - userType 為 null 時預設 BUYER
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("US-M03-001: 會員註冊")
class AuthServiceRegisterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthService authService;

    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_PASSWORD = "SecurePass123";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedHash";

    // ── AC-M03-001-1: 成功註冊 ─────────────────────────────────────────

    @Test
    @DisplayName("AC-M03-001-1: 成功註冊（userType=BUYER，預設值）")
    void register_success_defaultBuyer() {
        // Arrange
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .userType(null)
                .build();

        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        User savedUser = User.builder()
                .email(TEST_EMAIL)
                .passwordHash(ENCODED_PASSWORD)
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
        savedUser.setId(userId);
        savedUser.setCreatedAt(now);

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        RegisterResponse response = authService.register(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(response.getUserType()).isEqualTo("BUYER");
        assertThat(response.getCreatedAt()).isEqualTo(now);

        // 驗證密碼有被加密
        verify(passwordEncoder).encode(TEST_PASSWORD);
        // 驗證儲存了正確 role
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(User.UserRole.BUYER);
    }

    @Test
    @DisplayName("AC-M03-001-1: 成功註冊（userType=BUYER，明確指定）")
    void register_success_explicitBuyer() {
        // Arrange
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .userType("BUYER")
                .build();

        User savedUser = buildSavedUser(User.UserRole.BUYER);
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        RegisterResponse response = authService.register(request);

        // Assert
        assertThat(response.getUserType()).isEqualTo("BUYER");
    }

    @Test
    @DisplayName("AC-M03-001-1: 成功註冊（userType=SELLER）")
    void register_success_seller() {
        // Arrange
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .userType("SELLER")
                .build();

        User savedUser = buildSavedUser(User.UserRole.SELLER);
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        RegisterResponse response = authService.register(request);

        // Assert
        assertThat(response.getUserType()).isEqualTo("SELLER");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(User.UserRole.SELLER);
    }

    @Test
    @DisplayName("AC-M03-001-1: 成功註冊（userType=HOST）")
    void register_success_host() {
        // Arrange
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .userType("HOST")
                .build();

        User savedUser = buildSavedUser(User.UserRole.HOST);
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        RegisterResponse response = authService.register(request);

        // Assert
        assertThat(response.getUserType()).isEqualTo("HOST");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(User.UserRole.HOST);
    }

    // ── AC-M03-001-2: Email 已被註冊 ──────────────────────────────────

    @Test
    @DisplayName("AC-M03-001-2: Email 已被註冊，拋出 E-1005 BusinessException")
    void register_emailAlreadyExists_throwsBusinessException() {
        // Arrange
        RegisterRequest request = RegisterRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .userType("BUYER")
                .build();

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo(ErrorCode.E_1005);
                    assertThat(bex.getFullCode()).isEqualTo("E-1005");
                });

        // 確認未呼叫 save
        verify(userRepository, never()).save(any());
        // 確認未呼叫密碼加密
        verify(passwordEncoder, never()).encode(any());
    }

    // ── 輔助方法 ──────────────────────────────────────────────────────

    private User buildSavedUser(User.UserRole role) {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email(TEST_EMAIL)
                .passwordHash(ENCODED_PASSWORD)
                .role(role)
                .status("ACTIVE")
                .build();
        user.setId(userId);
        user.setCreatedAt(Instant.now());
        return user;
    }
}
