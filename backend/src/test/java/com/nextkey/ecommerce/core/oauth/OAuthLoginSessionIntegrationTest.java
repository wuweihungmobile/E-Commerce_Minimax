package com.nextkey.ecommerce.core.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.nextkey.ecommerce.api.dto.AuthResponse;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.OAuthDto;
import com.nextkey.ecommerce.api.dto.RefreshTokenRequest;
import com.nextkey.ecommerce.core.auth.AccountSecurityService;
import com.nextkey.ecommerce.core.auth.AuthService;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.OAuthAccount;
import com.nextkey.ecommerce.domain.model.user.OAuthProvider;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.OAuthAccountRepository;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.redis.RedisConfig;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.infrastructure.security.LoginAttemptService;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * Sprint 215（DEF-296）：OAuth 登入簽發的 session 必須與密碼登入守同一組不變量。
 *
 * <p>{@code OAuthService} 原本自己複製了一份「產生 JWT」的程式碼，沒有跟上 {@code AuthService} 後來的修正：
 * 不檢查帳號狀態（停權帳號仍可登入）、refresh token 沒有登記到 Redis（換發必定失敗，15 分鐘後被登出）、
 * 租戶解析沒有 {@code tenant_members} 回退，且新建的 OAuth 使用者被寫死在 SYSTEM 租戶（與 DEF-244 之後的註冊不一致）。
 *
 * <p>既有 {@code OAuthServiceTest} 把 {@code JwtTokenService} 整個 mock 掉，所以「換發得了嗎」「JWT 裡是哪個租戶」
 * 從來沒被驗證過。這裡用真實的 {@code JwtTokenService}、真實的 {@code RefreshTokenService}（連 {@code make test-db-up}
 * 起的 Redis，序列化器與生產相同）與真實的 {@code AuthService}，只 mock 資料庫與對 provider 的 HTTP 呼叫。
 * 不啟動 Spring context：{@code IntegrationTestConfiguration} 會把 Redis 換成 mock。需求：執行前須先 {@code make test-db-up}。
 */
@DisplayName("IT-OAUTH-SESSION: OAuth 登入簽發的 session 與密碼登入守同一組不變量（真 Redis + 真 JWT）")
class OAuthLoginSessionIntegrationTest {

    private static final String JWT_SECRET = "testSecretKeyForJwtTokenGenerationThatIsAtLeast256BitsLongForTesting";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String REDIRECT_URI = "http://localhost:3000/oauth/callback/google";
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, Object> redisTemplate;

    private UserRepository userRepository;
    private OAuthAccountRepository oAuthAccountRepository;
    private TenantMemberRepository tenantMemberRepository;
    private RestTemplate restTemplate;
    private JwtTokenService jwtTokenService;
    private RefreshTokenService refreshTokenService;
    private AuthService authService;
    private OAuthService oAuthService;
    private UUID userId;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("localhost", 6379);
        redisConfig.setPassword(RedisPassword.of("redis-dev-password"));
        connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new RedisConfig().redisTemplate(connectionFactory);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userRepository = mock(UserRepository.class);
        oAuthAccountRepository = mock(OAuthAccountRepository.class);
        tenantMemberRepository = mock(TenantMemberRepository.class);
        when(tenantMemberRepository.findByUserId(any())).thenReturn(List.of());
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.findById(any())).thenAnswer(inv ->
                Optional.of(Tenant.builder().id(inv.getArgument(0)).name("tenant").build()));
        restTemplate = mock(RestTemplate.class);

        jwtTokenService = new JwtTokenService(JWT_SECRET, 900_000L, 2_592_000_000L);
        refreshTokenService = new RefreshTokenService(redisTemplate);
        authService = new AuthService(userRepository, tenantRepository, tenantMemberRepository,
                new BCryptPasswordEncoder(), jwtTokenService, refreshTokenService,
                mock(LoginAttemptService.class), mock(AccountSecurityService.class));
        oAuthService = new OAuthService(userRepository, oAuthAccountRepository, authService, restTemplate);
        ReflectionTestUtils.setField(oAuthService, "googleClientId", "google-client-id");
        ReflectionTestUtils.setField(oAuthService, "googleClientSecret", "google-client-secret");
        ReflectionTestUtils.setField(oAuthService, "allowedRedirectOriginsRaw", "");
    }

    @AfterEach
    void cleanUp() {
        refreshTokenService.blacklistAllRefreshTokens(userId);
    }

    @Test
    @DisplayName("對照組：密碼登入拿到的 refresh token 可以換發（證明測試鷹架本身沒問題）")
    void passwordLoginSessionCanBeRefreshed() {
        User user = activeUser(null);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("correct-password"));
        when(userRepository.findByEmailAndStatus(user.getEmail(), "ACTIVE")).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AuthResponse login = authService.login(loginRequest(user.getEmail(), "correct-password"));
        AuthResponse refreshed = authService.refreshToken(refreshRequest(login.getRefreshToken()));

        assertThat(refreshed.getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("OAuth 登入拿到的 refresh token 可以換發（否則前端在 access token 15 分鐘到期後必被登出）")
    void oauthLoginSessionCanBeRefreshed() {
        User user = activeUser(null);
        linkGoogleAccount("google-uid-refresh", user);
        googleReturns("google-uid-refresh", user.getEmail(), true);

        AuthResponse login = oAuthService.handleOAuthLogin(googleLoginRequest());
        AuthResponse refreshed = authService.refreshToken(refreshRequest(login.getRefreshToken()));

        assertThat(refreshed.getAccessToken()).isNotBlank();
    }

    @Test
    @DisplayName("停權帳號不可經由 OAuth 取得 session（密碼登入與換發都已擋下非 ACTIVE 帳號）")
    void suspendedUserCannotObtainSessionViaOAuth() {
        User user = activeUser(null);
        user.setStatus("SUSPENDED");
        linkGoogleAccount("google-uid-suspended", user);
        googleReturns("google-uid-suspended", user.getEmail(), true);

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(googleLoginRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1004);
        assertThat(redisTemplate.keys("refresh_token:" + userId + ":*"))
                .as("被拒絕的登入不可留下任何可換發的 refresh token")
                .isEmpty();
    }

    @Test
    @DisplayName("以密碼註冊、開店後連結 Google 的店主，OAuth 登入的 JWT 租戶是自己的店，不是 SYSTEM")
    void oauthLoginResolvesStoreTenantFromMembership() {
        UUID storeTenantId = UUID.randomUUID();
        User owner = activeUser(null);
        owner.setRole(User.UserRole.STORE_OWNER);
        when(tenantMemberRepository.findByUserId(userId)).thenReturn(List.of(membership(storeTenantId)));
        linkGoogleAccount("google-uid-owner", owner);
        googleReturns("google-uid-owner", owner.getEmail(), true);

        AuthResponse login = oAuthService.handleOAuthLogin(googleLoginRequest());

        assertThat(jwtTokenService.getTenantId(login.getAccessToken())).isEqualTo(storeTenantId.toString());
    }

    @Test
    @DisplayName("經 OAuth 新建的使用者不綁死 SYSTEM 租戶：開店核准後再次 OAuth 登入，JWT 租戶是自己的店")
    void newOAuthUserIsNotPinnedToSystemTenant() {
        String email = "oauth-new-" + userId + "@example.com";
        googleReturns("google-uid-new", email, true);
        when(oAuthAccountRepository.findByProviderAndProviderUserId("google", "google-uid-new"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(userId);
            }
            return u;
        });

        oAuthService.handleOAuthLogin(googleLoginRequest());

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(created.capture());
        User createdUser = created.getAllValues().get(0);
        assertThat(createdUser.getTenantId())
                .as("比照 register（DEF-244）：新帳號不帶任何租戶，由 tenant_members 決定")
                .isNull();

        // 模擬開店核准（AdminService.approveTenantApplication：只建 tenant_members、把角色改成 STORE_OWNER）
        UUID storeTenantId = UUID.randomUUID();
        createdUser.setRole(User.UserRole.STORE_OWNER);
        when(tenantMemberRepository.findByUserId(userId)).thenReturn(List.of(membership(storeTenantId)));
        linkGoogleAccount("google-uid-new", createdUser);

        AuthResponse secondLogin = oAuthService.handleOAuthLogin(googleLoginRequest());

        assertThat(jwtTokenService.getTenantId(secondLogin.getAccessToken()))
                .isEqualTo(storeTenantId.toString())
                .isNotEqualTo(SYSTEM_TENANT_ID.toString());
    }

    @Test
    @DisplayName("Google 回報 email 未驗證時，不可憑這個 email 自動連結到既有帳號（否則可冒用他人帳號）")
    void unverifiedGoogleEmailIsNotTrustedForAccountLinking() {
        User victim = activeUser(null);
        when(oAuthAccountRepository.findByProviderAndProviderUserId("google", "google-uid-attacker"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(victim.getEmail())).thenReturn(Optional.of(victim));
        googleReturns("google-uid-attacker", victim.getEmail(), false);

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(googleLoginRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9903);
        verify(oAuthAccountRepository, never()).save(any(OAuthAccount.class));
        assertThat(redisTemplate.keys("refresh_token:" + userId + ":*")).isEmpty();
    }

    private User activeUser(final UUID tenantId) {
        return User.builder()
                .id(userId)
                .email("user-" + userId + "@example.com")
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .tenantId(tenantId)
                .build();
    }

    private TenantMember membership(final UUID tenantId) {
        return TenantMember.builder()
                .tenantId(tenantId)
                .userId(userId)
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .build();
    }

    private void linkGoogleAccount(final String providerUserId, final User user) {
        when(oAuthAccountRepository.findByProviderAndProviderUserId("google", providerUserId))
                .thenReturn(Optional.of(OAuthAccount.builder()
                        .userId(user.getId()).provider("google").providerUserId(providerUserId).build()));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @SuppressWarnings("unchecked")
    private void googleReturns(final String providerUserId, final String email, final boolean verifiedEmail) {
        when(restTemplate.exchange(eq(GOOGLE_TOKEN_URL), eq(HttpMethod.POST), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(Map.of("access_token", "google-access-token"), HttpStatus.OK));
        when(restTemplate.exchange(eq(GOOGLE_USERINFO_URL), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(Map.of("id", providerUserId, "email", email,
                        "verified_email", verifiedEmail, "name", "OAuth User"), HttpStatus.OK));
    }

    private static OAuthDto.AuthRequest googleLoginRequest() {
        return OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GOOGLE).code("auth-code").redirectUri(REDIRECT_URI).build();
    }

    private static LoginRequest loginRequest(final String email, final String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private static RefreshTokenRequest refreshRequest(final String refreshToken) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);
        return request;
    }
}
