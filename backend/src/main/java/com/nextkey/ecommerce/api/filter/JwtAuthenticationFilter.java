package com.nextkey.ecommerce.api.filter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nextkey.ecommerce.domain.model.user.RolePermissionMapping;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.infrastructure.security.JwtTokenService;

import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final RolePermissionMapping rolePermissionMapping;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                  RolePermissionMapping rolePermissionMapping) {
        this.jwtTokenService = jwtTokenService;
        this.rolePermissionMapping = rolePermissionMapping;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenService.validateToken(jwt)) {
                UUID userId = jwtTokenService.getUserId(jwt);
                String email = jwtTokenService.getEmail(jwt);
                String role = jwtTokenService.getRole(jwt);
                String tenantId = jwtTokenService.getTenantId(jwt);

                UserPrincipal principal = new UserPrincipal(userId, email, role, tenantId);

                // Load authorities from role-permission mapping
                User.UserRole userRole = User.UserRole.valueOf(role);
                List<String> authorities = rolePermissionMapping.getAuthorities(userRole);
                List<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                grantedAuthorities
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated user: {} with role: {} and {} authorities",
                        email, role, grantedAuthorities.size());
            }
        } catch (IllegalArgumentException | ClassCastException | JwtException | NullPointerException ex) {
            // NullPointerException（DEF-222）：refresh token 沒有 role/email/tenantId claim，
            // 若被當 Bearer token 送來，User.UserRole.valueOf(null) 會拋出 NPE 而非 IAE。
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(final HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        String path = request.getServletPath();
        // Only skip JWT filter for public auth endpoints (register, login, refresh)
        // Other auth endpoints like logout and me require authentication
        return (path.equals("/v2/auth/register") ||
                path.equals("/v2/auth/login") ||
                path.equals("/v2/auth/refresh") ||
                path.startsWith("/actuator/"));
    }
}
