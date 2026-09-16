package com.nextkey.ecommerce.core.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.AuthResponse;
import com.nextkey.ecommerce.api.dto.LoginRequest;
import com.nextkey.ecommerce.api.dto.LogoutRequest;
import com.nextkey.ecommerce.api.dto.RefreshTokenRequest;
import com.nextkey.ecommerce.api.dto.RegisterRequest;
import com.nextkey.ecommerce.api.dto.RegisterResponse;
import com.nextkey.ecommerce.api.dto.UserInfoResponse;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;
import com.nextkey.ecommerce.infrastructure.security.RefreshTokenService;
import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public RegisterResponse register(final RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.E_1005, "Email already registered");
        }

        // Resolve userType (default: BUYER)
        User.UserRole role = resolveUserRole(request.getUserType());

        // Create new user
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(role)
                .status("ACTIVE")
                .emailVerified(false)
                .tenantId(request.getTenantId())
                .metadata(new HashMap<>())
                .build();

        user = userRepository.save(user);

        // If tenantId is provided, create TenantMember association
        if (request.getTenantId() != null) {
            @SuppressWarnings("unused")
            Tenant tenant = tenantRepository.findById(request.getTenantId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

            com.nextkey.ecommerce.domain.model.tenant.TenantMember member =
                    com.nextkey.ecommerce.domain.model.tenant.TenantMember.builder()
                            .tenantId(request.getTenantId())
                            .userId(user.getId())
                            .storeRole(com.nextkey.ecommerce.domain.model.tenant.TenantMember.StoreRole.STORE_OWNER)
                            .build();
            tenantMemberRepository.save(member);
            log.info("User {} associated with tenant {}", user.getEmail(), request.getTenantId());
        }

        log.info("New user registered: {} ({})", user.getEmail(), user.getRole());

        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .userType(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private User.UserRole resolveUserRole(String userType) {
        if (userType == null) {
            return User.UserRole.BUYER;
        }
        return switch ( userType) {
            case "STORE_OWNER" -> User.UserRole.STORE_OWNER;
            case "STORE_STAFF" -> User.UserRole.STORE_STAFF;
            case "SELLER" -> User.UserRole.SELLER;
            case "HOST" -> User.UserRole.HOST;
            case "BUYER" -> User.UserRole.BUYER;
            default -> User.UserRole.BUYER;
        };
    }

    @Transactional
    public AuthResponse login(final LoginRequest request) {
        User user = userRepository.findByEmailAndStatus(request.getEmail(), "ACTIVE")
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1001));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.E_1001);
        }

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        Tenant tenant = resolveTenantForUser(user);

        log.info("User logged in: {}", user.getEmail());

        return generateAuthResponse(user, tenant);
    }

    @Transactional
    public AuthResponse refreshToken(final RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenService.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.E_1003, "Invalid refresh token");
        }

        if (jwtTokenService.isTokenExpired(refreshToken)) {
            throw new BusinessException(ErrorCode.E_1002, "Refresh token expired");
        }

        UUID userId = jwtTokenService.getUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.E_1004, "Account not active");
        }

        // DEF-219：refresh token rotation 重放偵測——同一個 token 若已被換發過卻再次出現，
        // 視為外洩訊號，撤銷該使用者名下所有 refresh token，強制全裝置重新登入
        if (refreshTokenService.isRefreshTokenReused(userId, refreshToken)) {
            refreshTokenService.blacklistAllRefreshTokens(userId);
            log.warn("Refresh token reuse detected for user: {}, all sessions revoked", userId);
            throw new BusinessException(ErrorCode.E_1003, "Refresh token reuse detected; all sessions revoked");
        }

        // 檢查 refresh token 是否在黑名單中
        if (!refreshTokenService.isRefreshTokenValid(userId, refreshToken)) {
            throw new BusinessException(ErrorCode.E_1003, "Refresh token has been revoked");
        }

        Tenant tenant = resolveTenantForUser(user);

        // DEF-219：rotation——本次用來換發的舊 token 立即失效，避免同一 token 可在效期內被重複使用
        refreshTokenService.rotateRefreshToken(userId, refreshToken);

        log.info("Token refreshed for user: {}", user.getEmail());

        return generateAuthResponse(user, tenant);
    }

    /**
     * 解析使用者所屬租戶：優先採 {@code user.tenantId}，缺失時查詢 {@code tenant_members} 關聯
     * （比照 login()/refreshToken() 應共用同一套邏輯，PRD §7.4.1 兩者皆為合法的角色/租戶授權來源
     * ——先前 refreshToken() 未比照 login() 查詢 tenant_members，會誤退化為 SYSTEM_TENANT_ID）。
     * 皆查無資料時退回 SYSTEM_TENANT_ID（如平台管理員無租戶歸屬）。
     */
    private Tenant resolveTenantForUser(final User user) {
        if (user.getTenantId() != null) {
            return tenantRepository.findById(user.getTenantId()).orElse(null);
        }
        var members = tenantMemberRepository.findByUserId(user.getId());
        if (!members.isEmpty()) {
            return tenantRepository.findById(members.get(0).getTenantId()).orElse(null);
        }
        return tenantRepository.findById(UUID.fromString(AppConstants.SYSTEM_TENANT_ID)).orElse(null);
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
        refreshTokenService.storeRefreshToken(user.getId(), refreshToken);

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
     * 會員登出
     * 將 Refresh Token 加入黑名單
     * @param userId 當前用戶 ID
     * @param request 登出請求（包含 refreshToken）
     */
    @Transactional
    public void logout(final UUID userId, final LogoutRequest request) {
        if (request.getRefreshToken() != null && !request.getRefreshToken().isEmpty()) {
            // 只失效指定的 refresh token
            refreshTokenService.blacklistRefreshToken(userId, request.getRefreshToken());
            log.info("User logged out, token blacklisted: {}", userId);
        } else {
            // 失效所有 refresh tokens
            refreshTokenService.blacklistAllRefreshTokens(userId);
            log.info("User logged out, all tokens blacklisted: {}", userId);
        }
    }

    /**
     * 取得當前用戶資訊
     * @param userId 當前用戶 ID
     * @return 用戶資訊
     */
    @Transactional(readOnly = true)
    public UserInfoResponse getCurrentUser(final UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006, "User not found"));

        // 獲取用戶的租戶資訊
        List<UserInfoResponse.TenantInfo> tenants = new ArrayList<>();
        if (user.getTenantId() != null) {
            Tenant tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
            if (tenant != null) {
                tenants.add(UserInfoResponse.TenantInfo.builder()
                        .tenantId(tenant.getId())
                        .tenantName(tenant.getName())
                        .role(user.getRole().name())
                        .build());
            }
        }

        return UserInfoResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .userType(user.getRole().name())
                .status(user.getStatus())
                .emailVerified(user.getEmailVerified())
                .profile(UserInfoResponse.Profile.builder()
                        .displayName(user.getFullName())
                        .phone(user.getPhone())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .tenants(tenants)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
