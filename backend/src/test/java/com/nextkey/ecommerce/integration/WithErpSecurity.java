package com.nextkey.ecommerce.integration;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import com.nextkey.ecommerce.api.filter.UserPrincipal;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 自定義測試安全上下文註解
 * 支持多租戶環境下的 JWT 認證模擬
 *
 * 使用方式：
 * @WithErpSecurity(userId = "xxx", tenantId = "xxx", role = "STORE_OWNER")
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithErpSecurity.WithErpSecurityContextFactory.class)
public @interface WithErpSecurity {

    String userId() default "550e8400-e29b-41d4-a716-446655440001";

    String tenantId() default "550e8400-e29b-41d4-a716-446655440001";

    String email() default "test@example.com";

    String role() default "STORE_OWNER";

    /**
     * 額外授予的細粒度權限碼（如 "room:create"），與 role 衍生的
     * ROLE_xxx/xxx 授權並存（DEF-242/243：取代 @WithMockUser 以提供真實
     * UserPrincipal，同時保留既有測試對特定 authority 字串的依賴）。
     */
    String[] authorities() default {};

    /**
     * 工廠類 - 創建自定義 SecurityContext
     */
    class WithErpSecurityContextFactory implements WithSecurityContextFactory<WithErpSecurity> {

        @Override
        public SecurityContext createSecurityContext(WithErpSecurity annotation) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();

            // 解析 userId
            UUID userId = UUID.fromString(annotation.userId());
            UUID tenantId = UUID.fromString(annotation.tenantId());

            // 創建權限
            List<SimpleGrantedAuthority> authorities = new ArrayList<>(List.of(
                    new SimpleGrantedAuthority("ROLE_" + annotation.role()),
                    new SimpleGrantedAuthority(annotation.role())
            ));
            for (String extra : annotation.authorities()) {
                authorities.add(new SimpleGrantedAuthority(extra));
            }

            // 創建 UserPrincipal (與 JwtAuthenticationFilter 一致)
            UserPrincipal principal = new UserPrincipal(
                    userId,
                    annotation.email(),
                    annotation.role(),
                    tenantId.toString()
            );

            // 創建認證 token - 使用 UserPrincipal 作為 principal
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            authorities
                    );

            context.setAuthentication(authentication);

            // 設置 TenantContext
            com.nextkey.ecommerce.shared.tenant.TenantContext.setCurrentTenant(tenantId);
            com.nextkey.ecommerce.shared.tenant.TenantContext.setCurrentUser(userId);

            return context;
        }
    }
}