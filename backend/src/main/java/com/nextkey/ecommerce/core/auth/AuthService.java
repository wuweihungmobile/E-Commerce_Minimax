package com.nextkey.ecommerce.core.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import com.nextkey.ecommerce.shared.tenant.TenantContext;

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
    private final LoginAttemptService loginAttemptService;
    private final AccountSecurityService accountSecurityService;

    @Transactional
    public RegisterResponse register(final RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.E_1005, "Email already registered");
        }

        // Resolve userType (default: BUYER)
        User.UserRole role = resolveUserRole(request.getUserType());

        // Create new user
        // DEF-244：先前還會依 request.getTenantId() 寫入 User.tenantId 並在 tenant_members
        // 建立 STORE_OWNER 成員紀錄，讓完全公開、無需登入的註冊端點可被用來奪取任一店鋪的
        // 真實管理權限。註冊一律不建立任何租戶關聯，僅能透過既有的 TenantApplicationRequest
        // 審核流程或店主邀請流程取得。
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(role)
                .status("ACTIVE")
                .emailVerified(false)
                .metadata(new HashMap<>())
                .build();

        user = userRepository.save(user);

        log.info("New user registered: {} ({})", user.getEmail(), user.getRole());

        sendVerificationMailAfterCommit(user.getId());

        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .userType(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * 註冊成功後寄 Email 驗證信（Sprint 204，FRD AC-M03-007-1）。交易提交<b>之後</b>才寄：若提交失敗（例如兩個請求同時註冊同一個
     * Email、後者在提交時撞唯一約束），不會對一個從未存在的帳號寄出連結。寄信失敗不影響註冊，見
     * {@link AccountSecurityService#sendEmailVerificationQuietly}。
     */
    private void sendVerificationMailAfterCommit(final UUID userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            accountSecurityService.sendEmailVerificationQuietly(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                accountSecurityService.sendEmailVerificationQuietly(userId);
            }
        });
    }

    /**
     * DEF-244：STORE_OWNER/STORE_STAFF 故意不在此列——兩者只能分別透過既有的
     * {@code TenantApplicationRequest} 審核流程、店主邀請流程取得，不可由公開註冊端點直接授予
     * （{@link RegisterRequest#getUserType()} 的 {@code @Pattern} 已在 DTO 層擋下這兩個值，
     * 此處的 {@code default} 分支僅為缺乏 Bean Validation 保護的其他呼叫端提供防禦性後備）。
     */
    private User.UserRole resolveUserRole(String userType) {
        if (userType == null) {
            return User.UserRole.BUYER;
        }
        return switch ( userType) {
            case "SELLER" -> User.UserRole.SELLER;
            case "HOST" -> User.UserRole.HOST;
            case "BUYER" -> User.UserRole.BUYER;
            default -> User.UserRole.BUYER;
        };
    }

    @Transactional
    public AuthResponse login(final LoginRequest request) {
        String email = request.getEmail();

        // DEF-220：帳號暫時鎖定檢查須在查詢/比對密碼之前，鎖定期間內一律拒絕，
        // 不對密碼做 bcrypt 比對，避免鎖定機制本身可被繞過。
        // DEF-293：「計入本次嘗試」與「判斷是否鎖定」必須是同一個原子步驟且在驗密碼之前——
        // 若改回「先檢查、驗完才計入」，同時到達的請求會在任何一個計入之前全部通過檢查（中間隔著一次 bcrypt），
        // 門檻就只擋得住依序的猜測。因此驗證失敗後不需要（也不可以）再計入一次；成功才歸零。
        if (loginAttemptService.countAttemptAndCheckLocked(email)) {
            throw new BusinessException(ErrorCode.E_1004, "Too many failed login attempts");
        }

        User user = userRepository.findByEmailAndStatus(email, "ACTIVE")
                .orElseThrow(() -> {
                    log.warn("Failed login attempt for email: {} (no active account)", email);
                    return new BusinessException(ErrorCode.E_1001);
                });

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email: {} (wrong password)", email);
            throw new BusinessException(ErrorCode.E_1001);
        }

        loginAttemptService.resetAttempts(email);

        log.info("User logged in: {}", user.getEmail());

        return completeLogin(user);
    }

    /**
     * 身分已驗證（密碼或 OAuth provider）之後簽發 session 的唯一入口（DEF-296）。
     *
     * <p>{@code OAuthService} 原本自己複製了一份 JWT 簽發，沒有跟上這裡後來的修正：不檢查帳號狀態（停權帳號照樣登入）、
     * refresh token 沒有登記（換發必定失敗）、租戶解析沒有 {@code tenant_members} 回退。新增登入方式時一律呼叫本方法，
     * 不要再自己產生 token。
     */
    @Transactional
    public AuthResponse completeLogin(final User user) {
        // 密碼登入已由 findByEmailAndStatus 篩掉非 ACTIVE 帳號（回 E-1001，不透露帳號是否存在）；
        // 這裡是其他登入方式的防線，比照 refreshToken() 回 E-1004。
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.E_1004, "Account not active");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return generateAuthResponse(user, resolveTenantForUser(user));
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

        // DEF-219：rotation——本次用來換發的舊 token 立即失效，避免同一 token 可在效期內被重複使用。
        // Sprint 213：上面的「是否有效」檢查與這裡的輪替是兩個獨立時間點，同時到達的請求會全部通過檢查；
        // 輪替本身是原子的「有效→已使用」，輸掉的請求代表同一個 token 被同時使用兩次——與先後兩次使用
        // 的重放是同一種外洩訊號，比照上面撤銷全部 session。
        if (!refreshTokenService.tryRotateRefreshToken(userId, refreshToken)) {
            refreshTokenService.blacklistAllRefreshTokens(userId);
            log.warn("Concurrent refresh with the same token for user: {}, all sessions revoked", userId);
            throw new BusinessException(ErrorCode.E_1003, "Refresh token reuse detected; all sessions revoked");
        }

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
        // Sprint 235（DEF-329）：只採有效（ACTIVE）成員。受邀未接受（INVITED）與已移除（REMOVED）的人不得帶店鋪租戶登入——
        // 原本不看狀態，被移除的店員登入／換發 token 仍帶店鋪租戶，只是受邀的買家也一樣。
        var members = tenantMemberRepository.findByUserIdAndStatus(user.getId(), TenantMember.MemberStatus.ACTIVE);
        if (!members.isEmpty()) {
            return tenantRepository.findById(members.get(0).getTenantId()).orElse(null);
        }
        return tenantRepository.findById(UUID.fromString(AppConstants.SYSTEM_TENANT_ID)).orElse(null);
    }

    /**
     * 使用者實際以什麼角色運作（Sprint 240，DEF-326）：沒有店鋪的 SELLER／HOST 是買家。
     *
     * <p>自助註冊可以直接得到 SELLER／HOST，沒有審核、不建租戶（開店核准後的角色是 STORE_OWNER，見 {@code AdminService}），
     * 所以正式環境的 SELLER／HOST 幾乎都是「還沒開店的人」，租戶落在系統租戶佔位值。但這兩個角色持有商品、房源、定價、
     * 運費模板、CMS、貼文的寫入權限，而各擁有權檢查是「資源的租戶 == 呼叫者的租戶」——於是所有這類使用者與平台自營資料
     * 彼此互通（可改運費模板、改線上 CMS 頁面、往買家目錄上架商品……）。使用者拍板：未歸屬任何店鋪者不得寫入，與 PRD
     * 「需先申請開店」一致。
     *
     * <p>權限完全由 JWT 的 role 宣告決定（{@code JwtAuthenticationFilter}），而 access token 只在
     * {@link #generateAuthResponse} 一處簽發，所以在簽發時推導有效角色，就同時關掉所有店家層端點，不必逐一修擁有權檢查。
     * 判斷依據是<b>租戶</b>而不是角色標籤：有真實店鋪租戶的 SELLER／HOST（舊資料、店鋪成員）照常運作。資料庫的
     * {@code users.role} 不動，開店核准時才由 AdminService 改成 STORE_OWNER。已簽發的 access token 在有效期內
     * （15 分鐘）不受影響，與其他租戶或角色的變更相同。
     */
    private static User.UserRole effectiveRole(final User user, final Tenant tenant) {
        User.UserRole role = user.getRole();
        boolean hasStore = TenantContext.isStoreTenant(tenant != null ? tenant.getId() : null);
        return isSellerOrHost(role) && !hasStore ? User.UserRole.BUYER : role;
    }

    private static boolean isSellerOrHost(final User.UserRole role) {
        return role == User.UserRole.SELLER || role == User.UserRole.HOST;
    }

    private AuthResponse generateAuthResponse(final User user, final Tenant tenant) {
        String tenantId = tenant != null ? tenant.getId().toString() : null;
        User.UserRole role = effectiveRole(user, tenant);

        String accessToken = jwtTokenService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                role.name(),
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
                        .role(role.name())
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

        // 與簽發 token 時同一套有效角色（Sprint 240，DEF-326）：回應的角色不能與實際權限不一致。
        // 只有 SELLER／HOST 的有效角色取決於租戶，其他角色不必多查。
        User.UserRole role = isSellerOrHost(user.getRole())
                ? effectiveRole(user, resolveTenantForUser(user))
                : user.getRole();

        // 獲取用戶的租戶資訊
        List<UserInfoResponse.TenantInfo> tenants = new ArrayList<>();
        if (user.getTenantId() != null) {
            Tenant tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
            if (tenant != null) {
                tenants.add(UserInfoResponse.TenantInfo.builder()
                        .tenantId(tenant.getId())
                        .tenantName(tenant.getName())
                        .role(role.name())
                        .build());
            }
        }

        return UserInfoResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .userType(role.name())
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
