package com.nextkey.ecommerce.api.filter;

import com.nextkey.ecommerce.shared.constants.AppConstants;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

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
        } catch (Exception ex) {
            log.error("[TenantContextFilter] Error processing request: {} {}", requestMethod, requestPath, ex);
            throw ex;
        } finally {
            log.debug("[TenantContextFilter] Clearing tenant context");
            TenantContext.clear();
        }
    }

    private UUID resolveEffectiveTenantId(UUID userId, String userTenantId, String requestedTenantId, String role) {
        // Super Admin must specify tenant via header
        if ("SUPER_ADMIN".equals(role) || "ADMIN".equals(role)) {
            if (StringUtils.hasText(requestedTenantId)) {
                return UUID.fromString(requestedTenantId);
            }
            // Admin with no specific tenant - use system tenant
            return UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
        }

        // Regular users use their assigned tenant
        if (StringUtils.hasText(userTenantId)) {
            return UUID.fromString(userTenantId);
        }

        // Default to system tenant for users without tenant
        return UUID.fromString(AppConstants.SYSTEM_TENANT_ID);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Don't filter auth endpoints
        return path.startsWith("/v2/auth/");
    }
}
