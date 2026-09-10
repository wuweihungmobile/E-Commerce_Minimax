package com.nextkey.ecommerce.api.filter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    /**
     * Sprint 153（DEF-192）：真正無需驗證、故意不設定 TenantContext 的端點——登入前不應具備任何
     * 租戶身分。含 item 13 追加的 {@code /v2/auth/oauth/login}（同樣是未驗證的登入入口，理由與
     * 下方 register/login/refresh 三者一致；{@code /v2/auth/oauth/link} 需要已驗證使用者，
     * 不在此清單）。
     *
     * <p>舊版此處是 {@code path.startsWith("/v2/auth/")}，意圖只排除這三者，卻連同 Sprint 94
     * （AI-2428）新增的兩個**已驗證**端點（{@code GET /v2/auth/me/data-export}、
     * {@code DELETE /v2/auth/me}）也一併跳過——{@code UserPrivacyService.exportMyData}/
     * {@code deleteMyAccount} 依賴 {@link TenantContext#getCurrentUser()} 取得使用者 ID，
     * 被跳過後恆為 null，{@code userRepository.findByIdForUpdate(null)} 查無資料，
     * 實際呼叫回 404「找不到使用者」（E-1006）——PRD §1.5.1 會員資料權利兩項功能經由真實
     * HTTP 呼叫實質上完全無法使用。
     *
     * <p>此缺陷在 {@code AuthControllerE2ETest}（{@code @SpringBootTest} 內嵌容器，
     * {@code integration-test} profile 未設定 {@code server.servlet.context-path}）中長期未被
     * 發現——{@code request.getServletPath()} 在該環境下實際回傳空字串，舊版字串前綴判斷因而
     * 形同沒有生效，filter 照常執行、意外把兩個已驗證端點也正確處理了。但正式環境（見
     * {@code application.yml} 的 {@code server.servlet.context-path: /api}）啟動的真實封裝應用
     * 程式，{@code getServletPath()} 行為不同，舊版判斷會 100% 重現此缺陷——以 Playwright 對
     * {@code make validate-e2e}（host 執行真實封裝後端）的瀏覽器端 E2E 才被揭露（新增
     * {@code at-account-data-rights.spec.ts} 補上零前端呼叫點的匯出/刪除入口時發現）。
     *
     * <p>修法：改用明確的路徑白名單（僅 register/login/refresh），且改讀
     * {@code getRequestURI() - getContextPath()} 而非 {@code getServletPath()}——前者不受
     * context-path 設定與否影響，行為在任何環境下一致，不再依賴巧合。連帶影響：
     * {@code logout}／{@code GET /v2/auth/me}／{@code GET /v2/auth/me/data-export}／
     * {@code DELETE /v2/auth/me} 這四個**已驗證**端點，此後會與其他已驗證端點一樣受
     * {@link RateLimitFilter} 每租戶限流保護——先前因套用同一段過寬判斷而被意外排除，
     * 並非刻意的排除設計（{@code RateLimitFilter} 的排除說明原文是「{@code /v2/auth/**} 登入前」，
     * 語意上從未打算涵蓋登入後的端點）。
     */
    private static final Set<String> UNAUTHENTICATED_AUTH_PATHS =
            Set.of("/v2/auth/register", "/v2/auth/login", "/v2/auth/refresh", "/v2/auth/oauth/login");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestPath = request.getServletPath();
        String requestMethod = request.getMethod();
        String tenantHeader = request.getHeader(TENANT_HEADER);

        log.info("[TenantContextFilter] Incoming request: {} {}, X-Tenant-ID: {}", requestMethod, requestPath, tenantHeader);

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            log.debug("[TenantContextFilter] Authentication: {}, principal: {}",
                    authentication != null ? authentication.getClass().getSimpleName() : "null",
                    authentication != null ? authentication.getPrincipal() : "null");

            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
                UUID userId = principal.getUserId();
                String userTenantId = principal.getTenantId();
                String requestedTenantId = request.getHeader(TENANT_HEADER);
                String role = principal.getRole();

                log.info("[TenantContextFilter] Authenticated user: {}, tenantId: {}, role: {}, requestedTenantId: {}",
                        userId, userTenantId, role, requestedTenantId);

                // Set user context
                TenantContext.setCurrentUser(userId);

                // Determine which tenant to use
                UUID effectiveTenantId = resolveEffectiveTenantId(
                        userId,
                        userTenantId,
                        requestedTenantId,
                        role
                );

                TenantContext.setCurrentTenant(effectiveTenantId);

                log.info("[TenantContextFilter] Tenant context set - user: {}, effective tenant: {}", userId, effectiveTenantId);
            } else {
                // Anonymous or public request - use system tenant
                log.info("[TenantContextFilter] Anonymous/public request - using system tenant");
                TenantContext.setCurrentTenant(UUID.fromString(AppConstants.SYSTEM_TENANT_ID));
            }

            filterChain.doFilter(request, response);
        } catch (IOException | ServletException ex) {
            log.error("[TenantContextFilter] Error processing request: {} {}", requestMethod, requestPath, ex);
            throw ex;
        } finally {
            log.debug("[TenantContextFilter] Clearing tenant context");
            TenantContext.clear();
        }
    }

    private UUID resolveEffectiveTenantId(final UUID userId, final String userTenantId, final String requestedTenantId, final String role) {
        // DEF-038：只有 SUPER_ADMIN 是平台級角色，才允許用 header 指定任意租戶。
        // ADMIN 依 RolePermissionMapping 定義為「租戶內管理」角色，不可用 header 越權存取他人租戶，
        // 一律強制使用自己的 userTenantId（與一般使用者相同邏輯，見下方）。
        if ("SUPER_ADMIN".equals(role)) {
            if (StringUtils.hasText(requestedTenantId)) {
                return UUID.fromString(requestedTenantId);
            }
            // Admin with no specific tenant - use system tenant
            return UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
        }

        // Regular users (including tenant-scoped ADMIN) use their assigned tenant
        if (StringUtils.hasText(userTenantId)) {
            return UUID.fromString(userTenantId);
        }

        // Default to system tenant for users without tenant
        return UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        // getRequestURI()/getContextPath() 而非 getServletPath()：見 UNAUTHENTICATED_AUTH_PATHS
        // 上方 Javadoc，後者在不同環境（context-path 有無設定）下行為不一致，曾造成 DEF-192。
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return UNAUTHENTICATED_AUTH_PATHS.contains(path);
    }
}
