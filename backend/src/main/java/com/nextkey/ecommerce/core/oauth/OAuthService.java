package com.nextkey.ecommerce.core.oauth;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
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
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sprint 153（item 13）：{@code exchangeCodeForUserInfo} 原為無條件拋 {@link UnsupportedOperationException}
 * 的 stub（Sprint 78 記錄，PRD 定性 P3，待商業需求觸發），經使用者拍板改為實作真正的 Authorization Code
 * 交換邏輯（GOOGLE/GITHUB）。client-id/secret 透過 {@code application.yml} 的 {@code oauth.*}
 * 設定（環境變數注入）取得，本輪不含真實憑證——未設定時回 {@link ErrorCode#E_1096}，不會把空字串
 * 當真的憑證送給 provider（避免產生一個「看起來有打 API 但其實是用空字串認證」的更難診斷的失敗模式）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USERINFO_URL = "https://api.github.com/user";
    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenService jwtTokenService;
    private final RestTemplate restTemplate;

    @Value("${oauth.google.client-id:}")
    private String googleClientId;
    @Value("${oauth.google.client-secret:}")
    private String googleClientSecret;
    @Value("${oauth.github.client-id:}")
    private String githubClientId;
    @Value("${oauth.github.client-secret:}")
    private String githubClientSecret;
    /**
     * 只比對 origin（scheme+host+port），不比對路徑——前端 callback 路徑可能隨版本調整，
     * 真正的安全邊界是「不可用任意網域接收 authorization code」，比照 CORS allowlist 的作法。
     */
    @Value("${oauth.allowed-redirect-origins:}")
    private String allowedRedirectOriginsRaw;

    /**
     * 處理 OAuth 登入/註冊
     */
    @Transactional
    public AuthResponse handleOAuthLogin(final OAuthDto.AuthRequest request) {
        // 1. 交換 access token
        OAuthUserInfo userInfo = exchangeCodeForUserInfo(request.getProvider(), request.getCode(), request.getRedirectUri());

        // 2. 查找或創建用戶
        User user = findOrCreateOAuthUser(request.getProvider(), userInfo);

        // 3. 獲取租戶
        Tenant tenant = user.getTenantId() != null
                ? tenantRepository.findById(user.getTenantId()).orElse(null)
                : tenantRepository.findById(UUID.fromString(AppConstants.SYSTEM_TENANT_ID)).orElse(null);

        // 4. 生成 JWT token
        log.info("OAuth login successful for user: {} via {}", user.getEmail(), request.getProvider());
        return generateAuthResponse(user, tenant);
    }

    /**
     * 將 OAuth 帳戶連結到現有用戶
     */
    @Transactional
    public void linkOAuthAccount(final UUID userId, final OAuthDto.LinkRequest request) {
        // 1. 交換 access token 獲取用戶資訊
        OAuthUserInfo userInfo = exchangeCodeForUserInfo(request.getProvider(), request.getCode(), request.getRedirectUri());

        // 2. 檢查是否已有其他帳戶使用這個 provider_user_id
        // Sprint 153：改用 BusinessException(E_1008)，該錯誤碼於 Sprint 78 stub 時代即已預留
        // 卻從未真正拋出過（見 ErrorCode 該行註解）；原本的 IllegalStateException 不會被
        // GlobalExceptionHandler 的 @ExceptionHandler(BusinessException.class) 攔截，會以未分類
        // 例外落到通用 500 處理，前端拿不到結構化的 ApiResponse 錯誤訊息。
        if (oAuthAccountRepository.existsByProviderAndProviderUserId(
                request.getProvider().getProviderId(), userInfo.getProviderUserId())) {
            throw new BusinessException(ErrorCode.E_1008);
        }

        // 3. 創建 OAuth 帳戶關聯
        OAuthAccount oAuthAccount = OAuthAccount.builder()
                .userId(userId)
                .provider(request.getProvider().getProviderId())
                .providerUserId(userInfo.getProviderUserId())
                .build();

        oAuthAccountRepository.save(oAuthAccount);
        log.info("Linked OAuth account for user {}: {}", userId, request.getProvider());
    }

    /**
     * 從 OAuth provider 交換授權碼以獲取用戶資訊（Sprint 153，item 13：取代先前無條件拋例外的 stub）。
     */
    private OAuthUserInfo exchangeCodeForUserInfo(final OAuthProvider provider, final String code, final String redirectUri) {
        validateRedirectUri(redirectUri);

        log.info("Exchanging OAuth code for user info from provider: {}", provider);

        return switch (provider) {
            case GOOGLE -> exchangeGoogleCode(code, redirectUri);
            case GITHUB -> exchangeGithubCode(code, redirectUri);
        };
    }

    /**
     * redirect_uri 白名單驗證（Sprint 78 已記錄的既知安全考量，本輪一併補上）：只比對 origin，
     * 防止 authorization code 被交換到攻擊者控制的網域（開放重導向/code 攔截風險）。
     * {@code oauth.allowed-redirect-origins} 未設定時視為未啟用此保護放行——與其他選填第三方整合
     * （如 Stripe webhook signature）同樣的「空設定＝暫不啟用」慣例，而非預設拒絕所有請求。
     */
    private void validateRedirectUri(final String redirectUri) {
        if (!StringUtils.hasText(allowedRedirectOriginsRaw)) {
            return;
        }
        if (!StringUtils.hasText(redirectUri)) {
            throw new BusinessException(ErrorCode.E_1097, "redirectUri is required");
        }

        Set<String> allowedOrigins = Arrays.stream(allowedRedirectOriginsRaw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        String origin;
        try {
            URI uri = URI.create(redirectUri);
            origin = uri.getScheme() + "://" + uri.getAuthority();
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.E_1097, "malformed redirectUri: " + redirectUri);
        }

        if (!allowedOrigins.contains(origin)) {
            log.warn("Rejected OAuth redirect_uri with disallowed origin: {}", origin);
            throw new BusinessException(ErrorCode.E_1097, "origin not allowed: " + origin);
        }
    }

    private OAuthUserInfo exchangeGoogleCode(final String code, final String redirectUri) {
        if (!StringUtils.hasText(googleClientId) || !StringUtils.hasText(googleClientSecret)) {
            throw new BusinessException(ErrorCode.E_1096, "Google OAuth client not configured");
        }

        MultiValueMap<String, String> tokenRequestBody = new LinkedMultiValueMap<>();
        tokenRequestBody.add("code", code);
        tokenRequestBody.add("client_id", googleClientId);
        tokenRequestBody.add("client_secret", googleClientSecret);
        tokenRequestBody.add("redirect_uri", redirectUri);
        tokenRequestBody.add("grant_type", "authorization_code");

        Map<String, Object> tokenResponse = postForm(GOOGLE_TOKEN_URL, tokenRequestBody, OAuthProvider.GOOGLE);
        String accessToken = requireStringField(tokenResponse, "access_token", OAuthProvider.GOOGLE);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        Map<String, Object> userInfo = getJson(GOOGLE_USERINFO_URL, headers, OAuthProvider.GOOGLE);

        return OAuthUserInfo.builder()
                .provider(OAuthProvider.GOOGLE.getProviderId())
                .providerUserId(requireStringField(userInfo, "id", OAuthProvider.GOOGLE))
                .email(stringField(userInfo, "email"))
                .name(stringField(userInfo, "name"))
                .pictureUrl(stringField(userInfo, "picture"))
                .build();
    }

    private OAuthUserInfo exchangeGithubCode(final String code, final String redirectUri) {
        if (!StringUtils.hasText(githubClientId) || !StringUtils.hasText(githubClientSecret)) {
            throw new BusinessException(ErrorCode.E_1096, "GitHub OAuth client not configured");
        }

        MultiValueMap<String, String> tokenRequestBody = new LinkedMultiValueMap<>();
        tokenRequestBody.add("code", code);
        tokenRequestBody.add("client_id", githubClientId);
        tokenRequestBody.add("client_secret", githubClientSecret);
        tokenRequestBody.add("redirect_uri", redirectUri);

        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> tokenResponse = postForm(GITHUB_TOKEN_URL, tokenRequestBody, tokenHeaders, OAuthProvider.GITHUB);
        String accessToken = requireStringField(tokenResponse, "access_token", OAuthProvider.GITHUB);

        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.set(HttpHeaders.AUTHORIZATION, "token " + accessToken);
        Map<String, Object> userInfo = getJson(GITHUB_USERINFO_URL, userHeaders, OAuthProvider.GITHUB);

        // GitHub 的 /user 端點在使用者未公開 email 時回傳 null，需另外呼叫 /user/emails 找
        // primary+verified 的信箱——User.email 為必填唯一欄位，不能讓 null 流進 findOrCreateOAuthUser。
        String email = stringField(userInfo, "email");
        if (!StringUtils.hasText(email)) {
            email = fetchGithubPrimaryEmail(userHeaders);
        }

        return OAuthUserInfo.builder()
                .provider(OAuthProvider.GITHUB.getProviderId())
                .providerUserId(String.valueOf(requireField(userInfo, "id", OAuthProvider.GITHUB)))
                .email(email)
                .name(stringField(userInfo, "name"))
                .pictureUrl(stringField(userInfo, "avatar_url"))
                .build();
    }

    private String fetchGithubPrimaryEmail(final HttpHeaders userHeaders) {
        List<Map<String, Object>> emails = getJsonList(GITHUB_EMAILS_URL, userHeaders, OAuthProvider.GITHUB);
        return emails.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                .map(e -> stringField(e, "email"))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.E_9903,
                        "GitHub account has no verified primary email"));
    }

    private Map<String, Object> postForm(final String url, final MultiValueMap<String, String> body,
            final OAuthProvider provider) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return postForm(url, body, headers, provider);
    }

    private Map<String, Object> postForm(final String url, final MultiValueMap<String, String> body,
            final HttpHeaders headers, final OAuthProvider provider) {
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() { });
            return requireBody(response, provider, url);
        } catch (RestClientException ex) {
            log.error("OAuth token exchange failed: provider={}, url={}", provider, url, ex);
            throw new BusinessException(ErrorCode.E_9903, provider.getDisplayName() + " token exchange failed", ex);
        }
    }

    private Map<String, Object> getJson(final String url, final HttpHeaders headers, final OAuthProvider provider) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() { });
            return requireBody(response, provider, url);
        } catch (RestClientException ex) {
            log.error("OAuth userinfo fetch failed: provider={}, url={}", provider, url, ex);
            throw new BusinessException(ErrorCode.E_9903, provider.getDisplayName() + " userinfo fetch failed", ex);
        }
    }

    private List<Map<String, Object>> getJsonList(final String url, final HttpHeaders headers, final OAuthProvider provider) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() { });
            List<Map<String, Object>> emailList = response.getBody();
            if (emailList == null) {
                throw new BusinessException(ErrorCode.E_9903, provider.getDisplayName() + " returned empty response: " + url);
            }
            return emailList;
        } catch (RestClientException ex) {
            log.error("OAuth email list fetch failed: provider={}, url={}", provider, url, ex);
            throw new BusinessException(ErrorCode.E_9903, provider.getDisplayName() + " email list fetch failed", ex);
        }
    }

    private Map<String, Object> requireBody(final ResponseEntity<Map<String, Object>> response,
            final OAuthProvider provider, final String url) {
        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new BusinessException(ErrorCode.E_9903, provider.getDisplayName() + " returned empty response: " + url);
        }
        return body;
    }

    private Object requireField(final Map<String, Object> body, final String field, final OAuthProvider provider) {
        Object value = body.get(field);
        if (value == null) {
            throw new BusinessException(ErrorCode.E_9903,
                    provider.getDisplayName() + " response missing required field: " + field);
        }
        return value;
    }

    private String requireStringField(final Map<String, Object> body, final String field, final OAuthProvider provider) {
        return String.valueOf(requireField(body, field, provider));
    }

    private String stringField(final Map<String, Object> body, final String field) {
        Object value = body.get(field);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 查找或創建 OAuth 用戶
     */
    private User findOrCreateOAuthUser(final OAuthProvider provider, final OAuthUserInfo userInfo) {
        // 1. 檢查是否已有 OAuth 帳戶關聯
        Optional<OAuthAccount> existingAccount = oAuthAccountRepository.findByProviderAndProviderUserId(
                provider.getProviderId(), userInfo.getProviderUserId());

        if (existingAccount.isPresent()) {
            // 已有關聯，返回對應用戶
            return userRepository.findById(existingAccount.get().getUserId())
                    .orElseThrow(() -> new IllegalStateException("OAuth account user not found"));
        }

        // 2. 檢查 email 是否已存在
        Optional<User> existingUser = userRepository.findByEmail(userInfo.getEmail());
        if (existingUser.isPresent()) {
            // 用戶已存在，創建 OAuth 關聯
            OAuthAccount newAccount = OAuthAccount.builder()
                    .userId(existingUser.get().getId())
                    .provider(provider.getProviderId())
                    .providerUserId(userInfo.getProviderUserId())
                    .build();
            oAuthAccountRepository.save(newAccount);
            return existingUser.get();
        }

        // 3. 創建新用戶
        UUID systemTenantId = UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
        User newUser = User.builder()
                .email(userInfo.getEmail())
                .fullName(userInfo.getName())
                .avatarUrl(userInfo.getPictureUrl())
                .role(User.UserRole.BUYER)
                .status("ACTIVE")
                .emailVerified(true) // OAuth provider 已驗證 email
                .tenantId(systemTenantId)
                .metadata(new HashMap<>())
                .build();
        newUser = userRepository.save(newUser);

        // 4. 創建 OAuth 帳戶關聯
        OAuthAccount newAccount = OAuthAccount.builder()
                .userId(newUser.getId())
                .provider(provider.getProviderId())
                .providerUserId(userInfo.getProviderUserId())
                .build();
        oAuthAccountRepository.save(newAccount);

        log.info("Created new OAuth user: {}", newUser.getEmail());
        return newUser;
    }

    private AuthResponse generateAuthResponse(final User user, final Tenant tenant) {
        String tenantId = tenant != null ? tenant.getId().toString() : null;

        String accessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                tenantId
        );

        String refreshToken = jwtTokenService.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getAccessTokenExpiration())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole().name())
                        .tenantId(tenantId)
                        .build())
                .build();
    }

    /**
     * 內部類：OAuth 用戶資訊
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OAuthUserInfo {
        private String provider;
        private String providerUserId;
        private String email;
        private String name;
        private String pictureUrl;
    }
}