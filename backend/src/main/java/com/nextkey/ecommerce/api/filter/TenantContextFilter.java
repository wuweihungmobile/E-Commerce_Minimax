package com.nextkey.ecommerce.api.filter;

import java.io.IOException;
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
        String path = request.getServletPath();
        // Don't filter auth endpoints
        return path.startsWith("/v2/auth/");
    }
}
