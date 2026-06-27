package com.nextkey.ecommerce.integration.util;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.api.filter.UserPrincipal;

/**
 * 整合測試用 SecurityContext 工具類。
 *
 * 統一以 {@link UserPrincipal} 設置 SecurityContext，
 * 確保 TenantContextFilter 能正確解析 tenantId / userId。
 *
 * <p>使用 {@code @WithMockUser} 會建立 String principal，
 * 導致 TenantContextFilter 無法解析租戶資訊（回傳 null），
 * 進而造成 BusinessException（404/422）。
 *
 * <p>典型用法：
 * <pre>{@code
 * @BeforeEach void setup() { TestSecurityContextHelper.setUserContext(USER_ID, TENANT_ID, "BUYER"); }
 * @AfterEach  void teardown() { TestSecurityContextHelper.clear(); }
 * }</pre>
 */
public final class TestSecurityContextHelper {

    private TestSecurityContextHelper() { }

    /**
     * 設置 UserPrincipal SecurityContext，authorities 為角色本身。
     *
     * @param userId   使用者 UUID
     * @param tenantId 租戶 UUID
     * @param role     角色字串（如 "BUYER", "SELLER"）
     */
    public static void setUserContext(UUID userId, UUID tenantId, String role) {
        setUserContext(userId, tenantId, role, role);
    }

    /**
     * 設置 UserPrincipal SecurityContext，可指定 authority。
     *
     * @param userId    使用者 UUID
     * @param tenantId  租戶 UUID
     * @param role      角色字串（如 "BUYER"）
     * @param authority Spring Security authority（如 "order:create"）
     */
    public static void setUserContext(UUID userId, UUID tenantId, String role, String authority) {
        setUserContext(userId, role + "@test.com", tenantId, role, List.of(authority));
    }

    /**
     * 完整參數版本，供需精確控制 email / 多 authority 的測試使用。
     */
    public static void setUserContext(UUID userId, String email, UUID tenantId,
                                      String role, List<String> authorities) {
        UserPrincipal principal = new UserPrincipal(userId, email, role, tenantId.toString());
        List<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, grantedAuthorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** 清除 SecurityContext（建議在 @AfterEach 呼叫）。 */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
