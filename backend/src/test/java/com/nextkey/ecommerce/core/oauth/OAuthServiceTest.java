package com.nextkey.ecommerce.core.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.nextkey.ecommerce.api.dto.AuthResponse;
import com.nextkey.ecommerce.api.dto.OAuthDto;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.OAuthAccount;
import com.nextkey.ecommerce.domain.model.user.OAuthProvider;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.OAuthAccountRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * OAuthService 單元測試（Sprint 153，item 13）。
 *
 * <p>背景：{@link OAuthService#exchangeCodeForUserInfo} 原為 Sprint 78 記錄的 stub（無條件拋
 * {@link UnsupportedOperationException}，PRD 定性 P3「待商業需求觸發」），經使用者拍板改為實作真正的
 * Authorization Code 交換邏輯（GOOGLE/GITHUB，透過 {@link RestTemplate} 呼叫各 provider 真實
 * token/userinfo endpoint）。本測試類別取代 Sprint 78 版本原本聚焦於「fail-closed 行為」的測試，
 * 改為驗證：(1) 未設定 client 憑證時明確回 {@link ErrorCode#E_1096} 而非把空字串送給 provider；
 * (2) redirect_uri 白名單驗證（{@link ErrorCode#E_1097}）；(3) 兩個 provider 的成功交換流程含既有
 * {@code findOrCreateOAuthUser} 邏輯（新用戶建立/既有 email 自動連結/既有 OAuth 帳戶查找）；
 * (4) GitHub 特有的 email 為 null 時查 {@code /user/emails} 找 primary+verified 信箱；
 * (5) provider API 失敗／回應缺欄位時包成 {@link ErrorCode#E_9903}；
 * (6) {@code linkOAuthAccount} 的 provider_user_id 衝突改回傳結構化的
 * {@code BusinessException(E_1008)}（該錯誤碼 Sprint 78 stub 時代已預留卻從未真正拋出過），
 * 不再是不會被 {@code GlobalExceptionHandler} 攔截的 {@code IllegalStateException}。
 *
 * <p>OAuth 特有的安全考量中，state/CSRF 防護採前端 sessionStorage 產生+比對的標準 SPA 作法
 * （見 {@code frontend/src/services/oauth.ts}），後端無須也未持有 state，故不在本測試範圍內。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthService 單元測試（Sprint 153）")
class OAuthServiceTest {

    private static final String REDIRECT_URI = "http://localhost:3000/oauth/callback/google";
    private static final String GITHUB_REDIRECT_URI = "http://localhost:3000/oauth/callback/github";

    @Mock private UserRepository userRepository;
    @Mock private OAuthAccountRepository oAuthAccountRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private RestTemplate restTemplate;

    @InjectMocks
    private OAuthService oAuthService;

    @BeforeEach
    void configureCredentials() {
        ReflectionTestUtils.setField(oAuthService, "googleClientId", "google-client-id");
        ReflectionTestUtils.setField(oAuthService, "googleClientSecret", "google-client-secret");
        ReflectionTestUtils.setField(oAuthService, "githubClientId", "github-client-id");
        ReflectionTestUtils.setField(oAuthService, "githubClientSecret", "github-client-secret");
        // 預設不啟用 redirect_uri 白名單（空字串＝未設定），個別測試視需要覆寫
        ReflectionTestUtils.setField(oAuthService, "allowedRedirectOriginsRaw", "");
    }

    private void mockExchange(final String url, final HttpMethod method, final Map<String, Object> body) {
        when(restTemplate.exchange(eq(url), eq(method), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
    }

    private void mockExchangeFailure(final String url, final HttpMethod method) {
        when(restTemplate.exchange(eq(url), eq(method), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("connection refused"));
    }

    // ========== 未設定 client 憑證 ==========

    @Test
    @DisplayName("handleOAuthLogin：GOOGLE 未設定 client-id/secret → E_1096，不呼叫 RestTemplate 或任何 Repository")
    void handleOAuthLogin_googleNotConfigured_throwsE1096() {
        ReflectionTestUtils.setField(oAuthService, "googleClientId", "");
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GOOGLE).code("code").redirectUri(REDIRECT_URI).build();

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1096);

        verifyNoInteractions(restTemplate, userRepository, oAuthAccountRepository, tenantRepository, jwtTokenService);
    }

    @Test
    @DisplayName("handleOAuthLogin：GITHUB 未設定 client-secret → E_1096")
    void handleOAuthLogin_githubNotConfigured_throwsE1096() {
        ReflectionTestUtils.setField(oAuthService, "githubClientSecret", "");
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GITHUB).code("code").redirectUri(GITHUB_REDIRECT_URI).build();

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1096);

        verifyNoInteractions(restTemplate, userRepository, oAuthAccountRepository, tenantRepository, jwtTokenService);
    }

    // ========== redirect_uri 白名單 ==========

    @Test
    @DisplayName("handleOAuthLogin：已設定白名單且 redirect_uri origin 不在清單內 → E_1097，不呼叫 RestTemplate")
    void handleOAuthLogin_redirectUriOriginNotAllowed_throwsE1097() {
        ReflectionTestUtils.setField(oAuthService, "allowedRedirectOriginsRaw", "https://app.nextkey.example.com");
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GOOGLE).code("code")
                .redirectUri("https://attacker.example.com/oauth/callback/google").build();

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1097);

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("handleOAuthLogin：已設定白名單且 redirect_uri 缺漏 → E_1097")
    void handleOAuthLogin_missingRedirectUriWithAllowlistConfigured_throwsE1097() {
        ReflectionTestUtils.setField(oAuthService, "allowedRedirectOriginsRaw", "http://localhost:3000");
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GOOGLE).code("code").redirectUri(null).build();

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1097);
    }

    // ========== GOOGLE 成功流程 ==========

    @Test
    @DisplayName("handleOAuthLogin：GOOGLE 全新使用者 → 交換 token + 取得 userinfo + 建立新使用者 + 回傳 JWT")
    void handleOAuthLogin_googleNewUser_createsUserAndReturnsTokens() {
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GOOGLE).code("auth-code").redirectUri(REDIRECT_URI).build();

        mockExchange("https://oauth2.googleapis.com/token", HttpMethod.POST,
                Map.of("access_token", "google-access-token"));
        mockExchange("https://www.googleapis.com/oauth2/v2/userinfo", HttpMethod.GET,
                Map.of("id", "google-uid-1", "email", "newuser@example.com", "name", "New User",
                        "picture", "https://example.com/avatar.png"));

        when(oAuthAccountRepository.findByProviderAndProviderUserId("google", "google-uid-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());

        UUID newUserId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(newUserId);
            return u;
        });

        UUID systemTenantId = UUID.fromString(com.nextkey.ecommerce.shared.constants.AppConstants.SYSTEM_TENANT_ID);
        when(tenantRepository.findById(systemTenantId))
                .thenReturn(Optional.of(Tenant.builder().id(systemTenantId).name("System").build()));
        when(jwtTokenService.generateAccessToken(eq(newUserId), eq("newuser@example.com"), eq("BUYER"), any()))
                .thenReturn("access-jwt");
        when(jwtTokenService.generateRefreshToken(newUserId)).thenReturn("refresh-jwt");
        when(jwtTokenService.getAccessTokenExpiration()).thenReturn(3600L);

        AuthResponse response = oAuthService.handleOAuthLogin(request);

        assertThat(response.getAccessToken()).isEqualTo("access-jwt");
        assertThat(response.getUser().getEmail()).isEqualTo("newuser@example.com");
        verify(oAuthAccountRepository).save(any(OAuthAccount.class));
    }

    @Test
    @DisplayName("handleOAuthLogin：GOOGLE 已有 OAuth 帳戶關聯 → 直接回傳既有使用者，不建立新使用者")
    void handleOAuthLogin_googleExistingOAuthAccount_returnsExistingUser() {
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GOOGLE).code("auth-code").redirectUri(REDIRECT_URI).build();

        mockExchange("https://oauth2.googleapis.com/token", HttpMethod.POST,
                Map.of("access_token", "google-access-token"));
        mockExchange("https://www.googleapis.com/oauth2/v2/userinfo", HttpMethod.GET,
                Map.of("id", "google-uid-existing", "email", "existing@example.com", "name", "Existing"));

        UUID existingUserId = UUID.randomUUID();
        when(oAuthAccountRepository.findByProviderAndProviderUserId("google", "google-uid-existing"))
                .thenReturn(Optional.of(OAuthAccount.builder().userId(existingUserId).provider("google")
                        .providerUserId("google-uid-existing").build()));

        User existingUser = User.builder().id(existingUserId).email("existing@example.com")
                .role(User.UserRole.BUYER).build();
        when(userRepository.findById(existingUserId)).thenReturn(Optional.of(existingUser));
        when(jwtTokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("access-jwt");
        when(jwtTokenService.generateRefreshToken(any())).thenReturn("refresh-jwt");
        when(jwtTokenService.getAccessTokenExpiration()).thenReturn(3600L);

        AuthResponse response = oAuthService.handleOAuthLogin(request);

        assertThat(response.getUser().getEmail()).isEqualTo("existing@example.com");
        verify(userRepository, never()).save(any(User.class));
        verify(oAuthAccountRepository, never()).save(any(OAuthAccount.class));
    }

    // ========== GITHUB 成功流程 + email fallback ==========

    @Test
    @DisplayName("handleOAuthLogin：GITHUB /user 已回傳公開 email → 不呼叫 /user/emails")
    void handleOAuthLogin_githubPublicEmail_doesNotFetchEmailsEndpoint() {
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GITHUB).code("auth-code").redirectUri(GITHUB_REDIRECT_URI).build();

        mockExchange("https://github.com/login/oauth/access_token", HttpMethod.POST,
                Map.of("access_token", "github-access-token"));
        mockExchange("https://api.github.com/user", HttpMethod.GET,
                Map.of("id", 12345, "email", "public@example.com", "name", "GitHub User",
                        "avatar_url", "https://example.com/avatar.png"));

        when(oAuthAccountRepository.findByProviderAndProviderUserId("github", "12345")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("public@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtTokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("access-jwt");
        when(jwtTokenService.generateRefreshToken(any())).thenReturn("refresh-jwt");
        when(jwtTokenService.getAccessTokenExpiration()).thenReturn(3600L);

        AuthResponse response = oAuthService.handleOAuthLogin(request);

        assertThat(response.getUser().getEmail()).isEqualTo("public@example.com");
        verify(restTemplate, never()).exchange(eq("https://api.github.com/user/emails"),
                any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("handleOAuthLogin：GITHUB /user 的 email 為 null → 查 /user/emails 取 primary+verified 信箱")
    void handleOAuthLogin_githubNullEmail_fetchesPrimaryVerifiedEmail() {
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GITHUB).code("auth-code").redirectUri(GITHUB_REDIRECT_URI).build();

        Map<String, Object> userInfoWithoutEmail = new java.util.HashMap<>();
        userInfoWithoutEmail.put("id", 999);
        userInfoWithoutEmail.put("email", null);
        userInfoWithoutEmail.put("name", "Private Email User");

        mockExchange("https://github.com/login/oauth/access_token", HttpMethod.POST,
                Map.of("access_token", "github-access-token"));
        when(restTemplate.exchange(eq("https://api.github.com/user"), eq(HttpMethod.GET),
                any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(userInfoWithoutEmail, HttpStatus.OK));

        List<Map<String, Object>> emails = List.of(
                Map.of("email", "secondary@example.com", "primary", false, "verified", true),
                Map.of("email", "primary@example.com", "primary", true, "verified", true));
        when(restTemplate.exchange(eq("https://api.github.com/user/emails"), eq(HttpMethod.GET),
                any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(emails, HttpStatus.OK));

        when(oAuthAccountRepository.findByProviderAndProviderUserId("github", "999")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("primary@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtTokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("access-jwt");
        when(jwtTokenService.generateRefreshToken(any())).thenReturn("refresh-jwt");
        when(jwtTokenService.getAccessTokenExpiration()).thenReturn(3600L);

        AuthResponse response = oAuthService.handleOAuthLogin(request);

        assertThat(response.getUser().getEmail()).isEqualTo("primary@example.com");
    }

    @Test
    @DisplayName("handleOAuthLogin：GITHUB 無 email 也查不到 primary+verified 信箱 → E_9903")
    void handleOAuthLogin_githubNoVerifiedEmail_throwsE9903() {
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GITHUB).code("auth-code").redirectUri(GITHUB_REDIRECT_URI).build();

        Map<String, Object> userInfoWithoutEmail = new java.util.HashMap<>();
        userInfoWithoutEmail.put("id", 1);
        userInfoWithoutEmail.put("email", null);

        mockExchange("https://github.com/login/oauth/access_token", HttpMethod.POST,
                Map.of("access_token", "github-access-token"));
        when(restTemplate.exchange(eq("https://api.github.com/user"), eq(HttpMethod.GET),
                any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(userInfoWithoutEmail, HttpStatus.OK));
        when(restTemplate.exchange(eq("https://api.github.com/user/emails"), eq(HttpMethod.GET),
                any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(List.of(Map.of("email", "unverified@example.com",
                        "primary", true, "verified", false)), HttpStatus.OK));

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9903);

        verifyNoInteractions(userRepository, oAuthAccountRepository);
    }

    // ========== provider API 失敗 ==========

    @Test
    @DisplayName("handleOAuthLogin：GOOGLE token endpoint 連線失敗 → 包成 E_9903，不觸碰任何 Repository")
    void handleOAuthLogin_googleTokenEndpointFails_throwsE9903() {
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GOOGLE).code("auth-code").redirectUri(REDIRECT_URI).build();

        mockExchangeFailure("https://oauth2.googleapis.com/token", HttpMethod.POST);

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9903);

        verifyNoInteractions(userRepository, oAuthAccountRepository, tenantRepository, jwtTokenService);
    }

    @Test
    @DisplayName("handleOAuthLogin：GOOGLE token 回應缺 access_token 欄位 → E_9903")
    void handleOAuthLogin_googleTokenResponseMissingAccessToken_throwsE9903() {
        OAuthDto.AuthRequest request = OAuthDto.AuthRequest.builder()
                .provider(OAuthProvider.GOOGLE).code("auth-code").redirectUri(REDIRECT_URI).build();

        mockExchange("https://oauth2.googleapis.com/token", HttpMethod.POST, Map.of("error", "invalid_grant"));

        assertThatThrownBy(() -> oAuthService.handleOAuthLogin(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_9903);
    }

    // ========== linkOAuthAccount ==========

    @Test
    @DisplayName("linkOAuthAccount：provider_user_id 已被其他使用者綁定 → BusinessException(E_1008)，不寫入 OAuthAccount"
            + "（Sprint 78 stub 時代已預留 E_1008 卻從未真正拋出，取代原本未分類的 IllegalStateException）")
    void linkOAuthAccount_alreadyLinkedToAnotherUser_throwsE1008() {
        UUID userId = UUID.randomUUID();
        OAuthDto.LinkRequest request = OAuthDto.LinkRequest.builder()
                .provider(OAuthProvider.GOOGLE).code("auth-code").redirectUri(REDIRECT_URI).build();

        mockExchange("https://oauth2.googleapis.com/token", HttpMethod.POST,
                Map.of("access_token", "google-access-token"));
        mockExchange("https://www.googleapis.com/oauth2/v2/userinfo", HttpMethod.GET,
                Map.of("id", "google-uid-taken", "email", "taken@example.com"));
        when(oAuthAccountRepository.existsByProviderAndProviderUserId("google", "google-uid-taken"))
                .thenReturn(true);

        assertThatThrownBy(() -> oAuthService.linkOAuthAccount(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.E_1008);

        verify(oAuthAccountRepository, never()).save(any(OAuthAccount.class));
    }

    @Test
    @DisplayName("linkOAuthAccount：合法輸入 → 以呼叫端提供的 userId（非 request body 欄位）建立 OAuth 帳戶關聯"
            + "（擁有權設計：userId 來自 OAuthController 已認證的 UserPrincipal，非 request body，"
            + "不存在代他人連結 OAuth 帳號的 IDOR 風險）")
    void linkOAuthAccount_success_savesWithCallerProvidedUserId() {
        UUID userId = UUID.randomUUID();
        OAuthDto.LinkRequest request = OAuthDto.LinkRequest.builder()
                .provider(OAuthProvider.GITHUB).code("auth-code").redirectUri(GITHUB_REDIRECT_URI).build();

        mockExchange("https://github.com/login/oauth/access_token", HttpMethod.POST,
                Map.of("access_token", "github-access-token"));
        mockExchange("https://api.github.com/user", HttpMethod.GET,
                Map.of("id", 555, "email", "linkme@example.com"));
        when(oAuthAccountRepository.existsByProviderAndProviderUserId("github", "555")).thenReturn(false);

        oAuthService.linkOAuthAccount(userId, request);

        org.mockito.ArgumentCaptor<OAuthAccount> captor = org.mockito.ArgumentCaptor.forClass(OAuthAccount.class);
        verify(oAuthAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("555");
    }
}
