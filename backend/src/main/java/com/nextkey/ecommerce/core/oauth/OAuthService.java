package com.nextkey.ecommerce.core.oauth;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenService jwtTokenService;
    @SuppressWarnings("unused")
    private final RestTemplate restTemplate;

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
        if (oAuthAccountRepository.existsByProviderAndProviderUserId(
                request.getProvider().getProviderId(), userInfo.getProviderUserId())) {
            throw new IllegalStateException("This " + request.getProvider().getDisplayName() + " account is already linked to another user");
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
     * 從 OAuth provider 交換授權碼以獲取用戶資訊
     */
    private OAuthUserInfo exchangeCodeForUserInfo(final OAuthProvider provider, final String code, final String redirectUri) {
        // 根據不同 provider 調用對應的 API
        // 這裡使用 RestTemplate，实际项目中可以使用 OAuth2Client 或 WebClient
        // 为了简化，这里只是示例框架，实际实现需要根据各 provider 的 API 调整

        // Google OAuth API endpoint
        // POST https://oauth2.googleapis.com/token
        // GET https://www.googleapis.com/oauth2/v2/userinfo

        // GitHub OAuth API endpoint
        // POST https://github.com/login/oauth/access_token
        // GET https://api.github.com/user

        // 這裡需要根據 provider 配置不同的 client_id, client_secret
        // 实际实现中建议使用 @Value 注解读取配置

        log.info("Exchanging OAuth code for user info from provider: {}", provider);

        // 模擬實現 - 實際需要調用 OAuth provider API
        // 根據 code 交換 access_token，然後用 access_token 獲取用戶資訊
        // 這裡返回的只是一個框架，實際需要根據 provider API 調整

        // 範例：調用 Google userinfo API
        // String accessToken = getAccessTokenFromCode(provider, code, redirectUri);
        // return getUserInfoFromProvider(provider, accessToken);

        // 為了示範，這裡使用靜態方法返回 mock 數據
        // 實際實現時需要替換為真正的 API 調用
        throw new UnsupportedOperationException("OAuth integration requires proper API credentials. Please configure OAuth client settings.");
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