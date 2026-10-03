package com.nextkey.ecommerce.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nextkey.ecommerce.api.dto.AuthResponse;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.LogoutRequest;
import com.nextkey.ecommerce.api.dto.RefreshTokenRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.api.dto.RegisterResponse;
import com.nextkey.ecommerce.api.dto.UserInfoResponse;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.infrastructure.security.LoginAttemptService;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * AuthService 單元測試（Sprint 66 US-002）。
 *
 * <p>背景：AuthService（register/login/refreshToken/logout/getCurrentUser 共 5 個
 * public 方法）先前完全零測試覆蓋——是全站認證流程的核心，無任何自動化驗證保護。
 */
@DisplayName("AuthService 單元測試")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private AccountSecurityService accountSecurityService;

    @InjectMocks
    private AuthService authService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    private User buildActiveUser() {
        return User.builder()
                .id(USER_ID)
                .email("buyer@example.com")
                .passwordHash("hashed-password")
                .fullName("Buyer One")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .build();
    }

    private void stubGeneratedTokens() {
        lenient().when(jwtTokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("access-token");
        lenient().when(jwtTokenService.generateRefreshToken(any())).thenReturn("refresh-token");
        lenient().when(jwtTokenService.getAccessTokenExpiration()).thenReturn(900000L);
    }

    // ── register ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("register_newEmail_createsUserSuccessfully")
        void register_newEmail_createsUserSuccessfully() {
            RegisterRequest request = RegisterRequest.builder()
                    .email("new@example.com").password("Password123!")
                    .fullName("New User").build();
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Password123!")).thenReturn("hashed");
            when(userRepository.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(USER_ID);
                return u;
            });

            RegisterResponse response = authService.register(request);

            assertThat(response.getEmail()).isEqualTo("new@example.com");
            assertThat(response.getUserType()).isEqualTo("BUYER");
        }

        @Test
        @DisplayName("register_emailAlreadyExists_throwsE1005")
        void register_emailAlreadyExists_throwsE1005() {
            RegisterRequest request = RegisterRequest.builder()
                    .email("dup@example.com").password("Password123!").build();
            when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1005);
        }

        @Test
        @DisplayName("DEF-244: register 不再接受 tenantId，不得建立任何租戶關聯（防止未登入訪客透過"
                + "公開註冊端點奪取任一店鋪的 STORE_OWNER 權限）")
        void register_doesNotCreateAnyTenantAssociation() {
            RegisterRequest request = RegisterRequest.builder()
                    .email("store@example.com").password("Password123!")
                    .userType("SELLER").build();
            when(userRepository.existsByEmail("store@example.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userRepository.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(USER_ID);
                return u;
            });

            authService.register(request);

            verify(tenantMemberRepository, never()).save(any());
            verify(tenantRepository, never()).findById(any());
        }
    }

    // ── completeLogin（DEF-296：所有登入方式共用的 session 簽發入口）──────

    @Nested
    @DisplayName("completeLogin()")
    class CompleteLogin {

        @Test
        @DisplayName("DEF-296：非 ACTIVE 帳號（例如停權）→ E-1004，不簽發任何 token、不登記 refresh token、不更新登入時間")
        void completeLogin_inactiveAccount_throwsE1004WithoutIssuingTokens() {
            User suspended = buildActiveUser();
            suspended.setStatus("SUSPENDED");

            assertThatThrownBy(() -> authService.completeLogin(suspended))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1004);

            verify(jwtTokenService, never()).generateAccessToken(any(), any(), any(), any());
            verify(jwtTokenService, never()).generateRefreshToken(any());
            verify(refreshTokenService, never()).storeRefreshToken(any(), any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("DEF-296：簽發的 refresh token 必須登記到 RefreshTokenService，否則之後的換發一律被判為已撤銷")
        void completeLogin_registersIssuedRefreshToken() {
            User user = buildActiveUser();
            stubGeneratedTokens();

            AuthResponse response = authService.completeLogin(user);

            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            verify(refreshTokenService).storeRefreshToken(USER_ID, "refresh-token");
            assertThat(user.getLastLoginAt()).isNotNull();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("DEF-296：User.tenantId 為 null 時由 tenant_members 決定 JWT 租戶（店主拿到自己的店，不是 SYSTEM）")
        void completeLogin_resolvesTenantFromMembership() {
            User owner = buildActiveUser();
            owner.setRole(User.UserRole.STORE_OWNER);
            when(tenantMemberRepository.findByUserIdAndStatus(USER_ID, com.nextkey.ecommerce.domain.model.tenant.TenantMember.MemberStatus.ACTIVE)).thenReturn(java.util.List.of(
                    com.nextkey.ecommerce.domain.model.tenant.TenantMember.builder()
                            .tenantId(TENANT_ID).userId(USER_ID).build()));
            // 對任何 id 都回對應的租戶，讓「查錯租戶」反映在下面的斷言上，而不是 Mockito 的 stub 不符
            when(tenantRepository.findById(any())).thenAnswer(inv ->
                    Optional.of(Tenant.builder().id(inv.getArgument(0)).name("tenant").build()));
            stubGeneratedTokens();

            AuthResponse response = authService.completeLogin(owner);

            assertThat(response.getUser().getTenantId()).isEqualTo(TENANT_ID.toString());
            verify(jwtTokenService).generateAccessToken(USER_ID, "buyer@example.com", "STORE_OWNER", TENANT_ID.toString());
        }
    }

    // ── login ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("login_validCredentials_returnsAuthResponse")
        void login_validCredentials_returnsAuthResponse() {
            User user = buildActiveUser();
            LoginRequest request = LoginRequest.builder()
                    .email("buyer@example.com").password("correct-password").build();
            when(userRepository.findByEmailAndStatus("buyer@example.com", "ACTIVE"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
            stubGeneratedTokens();

            AuthResponse response = authService.login(request);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getUser().getEmail()).isEqualTo("buyer@example.com");
            verify(userRepository).save(user); // lastLoginAt 更新
        }

        @Test
        @DisplayName("login_userNotFound_throwsE1001")
        void login_userNotFound_throwsE1001() {
            LoginRequest request = LoginRequest.builder()
                    .email("nobody@example.com").password("x").build();
            when(userRepository.findByEmailAndStatus("nobody@example.com", "ACTIVE"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1001);
        }

        @Test
        @DisplayName("login_wrongPassword_throwsE1001")
        void login_wrongPassword_throwsE1001() {
            User user = buildActiveUser();
            LoginRequest request = LoginRequest.builder()
                    .email("buyer@example.com").password("wrong-password").build();
            when(userRepository.findByEmailAndStatus("buyer@example.com", "ACTIVE"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1001);
        }

        @Test
        @DisplayName("login_accountLocked_throwsE1004AndNeverChecksPassword（DEF-220：帳號鎖定須在密碼驗證前擋下）")
        void login_accountLocked_throwsE1004AndNeverChecksPassword() {
            LoginRequest request = LoginRequest.builder()
                    .email("buyer@example.com").password("whatever").build();
            when(loginAttemptService.countAttemptAndCheckLocked("buyer@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1004);

            verify(userRepository, never()).findByEmailAndStatus(any(), any());
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("login_wrongPassword_countsAttemptBeforeVerifyingPassword（DEF-293：先計入再驗密碼，否則併發猜測會全部通過鎖定檢查）")
        void login_wrongPassword_countsAttemptBeforeVerifyingPassword() {
            User user = buildActiveUser();
            LoginRequest request = LoginRequest.builder()
                    .email("buyer@example.com").password("wrong-password").build();
            when(userRepository.findByEmailAndStatus("buyer@example.com", "ACTIVE"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BusinessException.class);

            InOrder inOrder = inOrder(loginAttemptService, passwordEncoder);
            inOrder.verify(loginAttemptService).countAttemptAndCheckLocked("buyer@example.com");
            inOrder.verify(passwordEncoder).matches("wrong-password", "hashed-password");
            verify(loginAttemptService, never()).resetAttempts(any());
        }

        @Test
        @DisplayName("login_userNotFound_countsAttempt（DEF-220：不存在的 email 也計入，避免帳號列舉繞過鎖定）")
        void login_userNotFound_countsAttempt() {
            LoginRequest request = LoginRequest.builder()
                    .email("nobody@example.com").password("x").build();
            when(userRepository.findByEmailAndStatus("nobody@example.com", "ACTIVE"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BusinessException.class);

            verify(loginAttemptService).countAttemptAndCheckLocked("nobody@example.com");
        }

        @Test
        @DisplayName("login_validCredentials_resetsFailedAttempts（DEF-220）")
        void login_validCredentials_resetsFailedAttempts() {
            User user = buildActiveUser();
            LoginRequest request = LoginRequest.builder()
                    .email("buyer@example.com").password("correct-password").build();
            when(userRepository.findByEmailAndStatus("buyer@example.com", "ACTIVE"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
            stubGeneratedTokens();

            authService.login(request);

            verify(loginAttemptService).resetAttempts("buyer@example.com");
        }
    }

    // ── refreshToken ──────────────────────────────────────────────────

    @Nested
    @DisplayName("refreshToken()")
    class RefreshToken {

        @Test
        @DisplayName("refreshToken_validToken_returnsNewAuthResponse")
        void refreshToken_validToken_returnsNewAuthResponse() {
            User user = buildActiveUser();
            RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("valid-refresh").build();
            when(jwtTokenService.validateToken("valid-refresh")).thenReturn(true);
            when(jwtTokenService.isTokenExpired("valid-refresh")).thenReturn(false);
            when(jwtTokenService.getUserId("valid-refresh")).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(refreshTokenService.isRefreshTokenValid(USER_ID, "valid-refresh")).thenReturn(true);
            when(refreshTokenService.tryRotateRefreshToken(USER_ID, "valid-refresh")).thenReturn(true);
            stubGeneratedTokens();

            AuthResponse response = authService.refreshToken(request);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
        }

        @Test
        @DisplayName("refreshToken_invalidToken_throwsE1003")
        void refreshToken_invalidToken_throwsE1003() {
            RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("bad-token").build();
            when(jwtTokenService.validateToken("bad-token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1003);
        }

        @Test
        @DisplayName("refreshToken_expiredToken_throwsE1002")
        void refreshToken_expiredToken_throwsE1002() {
            RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("expired-token").build();
            when(jwtTokenService.validateToken("expired-token")).thenReturn(true);
            when(jwtTokenService.isTokenExpired("expired-token")).thenReturn(true);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1002);
        }

        @Test
        @DisplayName("refreshToken_revokedToken_throwsE1003")
        void refreshToken_revokedToken_throwsE1003() {
            User user = buildActiveUser();
            RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("revoked-token").build();
            when(jwtTokenService.validateToken("revoked-token")).thenReturn(true);
            when(jwtTokenService.isTokenExpired("revoked-token")).thenReturn(false);
            when(jwtTokenService.getUserId("revoked-token")).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(refreshTokenService.isRefreshTokenValid(USER_ID, "revoked-token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1003);
        }

        @Test
        @DisplayName("refreshToken_inactiveAccount_throwsE1004")
        void refreshToken_inactiveAccount_throwsE1004() {
            User inactiveUser = buildActiveUser();
            inactiveUser.setStatus("SUSPENDED");
            RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("valid-refresh").build();
            when(jwtTokenService.validateToken("valid-refresh")).thenReturn(true);
            when(jwtTokenService.isTokenExpired("valid-refresh")).thenReturn(false);
            when(jwtTokenService.getUserId("valid-refresh")).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(inactiveUser));

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1004);
        }

        @Test
        @DisplayName("refreshToken_validToken_rotatesOldRefreshToken（DEF-219：換發後舊 token 須立即失效）")
        void refreshToken_validToken_rotatesOldRefreshToken() {
            User user = buildActiveUser();
            RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("valid-refresh").build();
            when(jwtTokenService.validateToken("valid-refresh")).thenReturn(true);
            when(jwtTokenService.isTokenExpired("valid-refresh")).thenReturn(false);
            when(jwtTokenService.getUserId("valid-refresh")).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(refreshTokenService.isRefreshTokenValid(USER_ID, "valid-refresh")).thenReturn(true);
            when(refreshTokenService.tryRotateRefreshToken(USER_ID, "valid-refresh")).thenReturn(true);
            stubGeneratedTokens();

            authService.refreshToken(request);

            verify(refreshTokenService).tryRotateRefreshToken(USER_ID, "valid-refresh");
        }

        @Test
        @DisplayName("refreshToken_lostRotationRace_revokesAllSessionsAndThrowsE1003"
                + "（Sprint 213：通過有效檢查後輸掉輪替競爭，不可換發）")
        void refreshToken_lostRotationRace_revokesAllSessionsAndThrows() {
            User user = buildActiveUser();
            RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("raced-token").build();
            when(jwtTokenService.validateToken("raced-token")).thenReturn(true);
            when(jwtTokenService.isTokenExpired("raced-token")).thenReturn(false);
            when(jwtTokenService.getUserId("raced-token")).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(refreshTokenService.isRefreshTokenValid(USER_ID, "raced-token")).thenReturn(true);
            when(refreshTokenService.tryRotateRefreshToken(USER_ID, "raced-token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1003);

            verify(refreshTokenService).blacklistAllRefreshTokens(USER_ID);
            verify(jwtTokenService, never()).generateAccessToken(any(), any(), any(), any());
            verify(refreshTokenService, never()).storeRefreshToken(any(), any());
        }

        @Test
        @DisplayName("refreshToken_reusedToken_revokesAllSessionsAndThrowsE1003（DEF-219：重放偵測）")
        void refreshToken_reusedToken_revokesAllSessionsAndThrows() {
            User user = buildActiveUser();
            RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("reused-token").build();
            when(jwtTokenService.validateToken("reused-token")).thenReturn(true);
            when(jwtTokenService.isTokenExpired("reused-token")).thenReturn(false);
            when(jwtTokenService.getUserId("reused-token")).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(refreshTokenService.isRefreshTokenReused(USER_ID, "reused-token")).thenReturn(true);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1003);

            verify(refreshTokenService).blacklistAllRefreshTokens(USER_ID);
            verify(jwtTokenService, never()).generateAccessToken(any(), any(), any(), any());
            verify(refreshTokenService, never()).storeRefreshToken(any(), any());
        }
    }

    // ── logout ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("logout_withSpecificToken_blacklistsOnlyThatToken")
        void logout_withSpecificToken_blacklistsOnlyThatToken() {
            LogoutRequest request = LogoutRequest.builder().refreshToken("specific-token").build();

            authService.logout(USER_ID, request);

            verify(refreshTokenService).blacklistRefreshToken(USER_ID, "specific-token");
            verify(refreshTokenService, never()).blacklistAllRefreshTokens(any());
        }

        @Test
        @DisplayName("logout_withoutToken_blacklistsAllTokens")
        void logout_withoutToken_blacklistsAllTokens() {
            LogoutRequest request = LogoutRequest.builder().build();

            authService.logout(USER_ID, request);

            verify(refreshTokenService).blacklistAllRefreshTokens(USER_ID);
            verify(refreshTokenService, never()).blacklistRefreshToken(any(), any());
        }
    }

    // ── getCurrentUser ────────────────────────────────────────────────

    @Nested
    @DisplayName("getCurrentUser()")
    class GetCurrentUser {

        @Test
        @DisplayName("getCurrentUser_found_returnsUserInfoWithTenant")
        void getCurrentUser_found_returnsUserInfoWithTenant() {
            User user = buildActiveUser();
            user.setTenantId(TENANT_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(tenantRepository.findById(TENANT_ID))
                    .thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).name("Test Tenant").build()));

            UserInfoResponse response = authService.getCurrentUser(USER_ID);

            assertThat(response.getEmail()).isEqualTo("buyer@example.com");
            assertThat(response.getTenants()).extracting(UserInfoResponse.TenantInfo::getTenantName)
                    .containsExactly("Test Tenant");
        }

        @Test
        @DisplayName("getCurrentUser_noTenant_returnsEmptyTenantList")
        void getCurrentUser_noTenant_returnsEmptyTenantList() {
            User user = buildActiveUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            UserInfoResponse response = authService.getCurrentUser(USER_ID);

            assertThat(response.getTenants()).isEmpty();
        }

        @Test
        @DisplayName("getCurrentUser_notFound_throwsE1006")
        void getCurrentUser_notFound_throwsE1006() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getCurrentUser(USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.E_1006);
        }
    }

    // ── 有效角色（DEF-326）────────────────────────────────────────────

    /**
     * Sprint 240（DEF-326）：沒有店鋪的 SELLER／HOST 以買家身分簽發。權限完全由 JWT 的 role 宣告決定，所以簽發是唯一的判斷點；
     * 判斷依據是租戶、不是角色標籤，其他角色一律不變（尤其是本來就沒有店鋪租戶的 SUPER_ADMIN）。
     */
    @Nested
    @DisplayName("有效角色（DEF-326：沒有店鋪的 SELLER／HOST 是買家）")
    class EffectiveRole {

        private final UUID systemTenantId = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

        /** 對任何 id 都回對應的租戶，讓「查錯租戶」反映在斷言上，而不是 Mockito 的 stub 不符。 */
        private void stubEveryTenantExists() {
            when(tenantRepository.findById(any())).thenAnswer(inv ->
                    Optional.of(Tenant.builder().id(inv.getArgument(0)).name("tenant").build()));
        }

        private User userWithRole(final User.UserRole role) {
            User user = buildActiveUser();
            user.setRole(role);
            return user;
        }

        @ParameterizedTest
        @EnumSource(value = User.UserRole.class, names = {"SELLER", "HOST"})
        @DisplayName("沒有店鋪（租戶是系統租戶）的 SELLER／HOST 簽發為 BUYER：token 與回應都是，資料庫的角色不動")
        void completeLogin_storeLessSellerOrHost_isIssuedAsBuyer(final User.UserRole role) {
            User user = userWithRole(role);
            stubEveryTenantExists();
            stubGeneratedTokens();

            AuthResponse response = authService.completeLogin(user);

            verify(jwtTokenService).generateAccessToken(USER_ID, "buyer@example.com", "BUYER", systemTenantId.toString());
            assertThat(response.getUser().getRole()).isEqualTo("BUYER");
            assertThat(user.getRole()).as("users.role 不動，開店核准時才由 AdminService 改成 STORE_OWNER").isEqualTo(role);
        }

        @Test
        @DisplayName("租戶解析不到（資料列不存在，JWT 租戶是 null）的 SELLER 也是買家")
        void completeLogin_sellerWithoutAnyTenantRow_isIssuedAsBuyer() {
            User user = userWithRole(User.UserRole.SELLER);
            stubGeneratedTokens();

            AuthResponse response = authService.completeLogin(user);

            verify(jwtTokenService).generateAccessToken(USER_ID, "buyer@example.com", "BUYER", null);
            assertThat(response.getUser().getRole()).isEqualTo("BUYER");
        }

        @ParameterizedTest
        @EnumSource(value = User.UserRole.class, names = {"SELLER", "HOST"})
        @DisplayName("有真實店鋪租戶（User.tenantId）的 SELLER／HOST 角色照舊——判斷依據是租戶，不是角色標籤")
        void completeLogin_sellerOrHostWithAStore_keepsTheRole(final User.UserRole role) {
            User user = userWithRole(role);
            user.setTenantId(TENANT_ID);
            stubEveryTenantExists();
            stubGeneratedTokens();

            AuthResponse response = authService.completeLogin(user);

            verify(jwtTokenService).generateAccessToken(USER_ID, "buyer@example.com", role.name(), TENANT_ID.toString());
            assertThat(response.getUser().getRole()).isEqualTo(role.name());
        }

        @Test
        @DisplayName("店鋪成員（tenant_members 的有效成員，User.tenantId 為 null）身分的 SELLER 同樣照舊")
        void completeLogin_sellerWithActiveMembership_keepsTheRole() {
            User user = userWithRole(User.UserRole.SELLER);
            when(tenantMemberRepository.findByUserIdAndStatus(USER_ID, TenantMember.MemberStatus.ACTIVE))
                    .thenReturn(List.of(TenantMember.builder().tenantId(TENANT_ID).userId(USER_ID).build()));
            stubEveryTenantExists();
            stubGeneratedTokens();

            authService.completeLogin(user);

            verify(jwtTokenService).generateAccessToken(USER_ID, "buyer@example.com", "SELLER", TENANT_ID.toString());
        }

        @ParameterizedTest
        @EnumSource(value = User.UserRole.class, names = {"SELLER", "HOST"}, mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("其他角色一律不變，即使沒有店鋪租戶（平台管理員本來就在系統租戶，不能被降級）")
        void completeLogin_otherRoles_areNeverChanged(final User.UserRole role) {
            User user = userWithRole(role);
            stubEveryTenantExists();
            stubGeneratedTokens();

            authService.completeLogin(user);

            verify(jwtTokenService).generateAccessToken(USER_ID, "buyer@example.com", role.name(), systemTenantId.toString());
        }

        @Test
        @DisplayName("換發 token（refresh）走同一套：沒有店鋪的 SELLER 換發後仍是買家")
        void refreshToken_storeLessSeller_isIssuedAsBuyer() {
            User user = userWithRole(User.UserRole.SELLER);
            RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("valid-refresh").build();
            when(jwtTokenService.validateToken("valid-refresh")).thenReturn(true);
            when(jwtTokenService.isTokenExpired("valid-refresh")).thenReturn(false);
            when(jwtTokenService.getUserId("valid-refresh")).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(refreshTokenService.isRefreshTokenValid(USER_ID, "valid-refresh")).thenReturn(true);
            when(refreshTokenService.tryRotateRefreshToken(USER_ID, "valid-refresh")).thenReturn(true);
            stubEveryTenantExists();
            stubGeneratedTokens();

            AuthResponse response = authService.refreshToken(request);

            assertThat(response.getUser().getRole()).isEqualTo("BUYER");
            verify(jwtTokenService).generateAccessToken(USER_ID, "buyer@example.com", "BUYER", systemTenantId.toString());
        }

        @Test
        @DisplayName("GET /me 與簽發一致：沒有店鋪的 SELLER 回 BUYER")
        void getCurrentUser_storeLessSeller_reportsBuyer() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithRole(User.UserRole.SELLER)));
            stubEveryTenantExists();

            UserInfoResponse response = authService.getCurrentUser(USER_ID);

            assertThat(response.getUserType()).isEqualTo("BUYER");
            assertThat(response.getTenants()).isEmpty();
        }

        @Test
        @DisplayName("GET /me 與簽發一致：有店鋪的 SELLER 回 SELLER（userType 與 tenants[].role 都是）")
        void getCurrentUser_sellerWithAStore_reportsSeller() {
            User user = userWithRole(User.UserRole.SELLER);
            user.setTenantId(TENANT_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            stubEveryTenantExists();

            UserInfoResponse response = authService.getCurrentUser(USER_ID);

            assertThat(response.getUserType()).isEqualTo("SELLER");
            assertThat(response.getTenants()).extracting(UserInfoResponse.TenantInfo::getRole).containsExactly("SELLER");
        }
    }
}
