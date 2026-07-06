package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * TenantContextFilter ADMIN 跨租戶 X-Tenant-ID 授權驗證（DEF-038）。
 *
 * <p>多 Sprint 測試強化計劃（Sprint 66-79）發現：{@code resolveEffectiveTenantId} 將
 * {@code ADMIN} 與 {@code SUPER_ADMIN} 同等對待，只要請求帶入 {@code X-Tenant-ID} header
 * 即直接採信為 effectiveTenantId，未驗證是否等於該 ADMIN 使用者自己的 tenantId。但
 * {@code RolePermissionMapping} 明確定義 ADMIN 為「租戶內管理」角色，只有 SUPER_ADMIN
 * 才應具備平台級跨租戶能力。此缺口使任一租戶的 ADMIN 帳號可偽造 header 完全接管其他
 * 租戶資料，讓過去 12 個 DEF 修復所依賴的「isAdmin 放行」假設失效。
 */
@DisplayName("DEF-038: TenantContextFilter ADMIN 跨租戶 header 授權")
class TenantContextFilterTest {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    private final TenantContextFilter filter = new TenantContextFilter();

    private final FilterChain noopChain = (request, response) -> { };

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("ADMIN 帶入他人租戶的 X-Tenant-ID header 時，effectiveTenant 仍必須是自己的租戶")
    void adminCannotImpersonateAnotherTenantViaHeader() throws Exception {
        UUID ownTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();

        authenticateAs(adminUserId, ownTenantId.toString(), "ADMIN");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        request.addHeader(TENANT_HEADER, otherTenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID[] capturedTenant = new UUID[1];
        FilterChain capturingChain = (req, res) -> capturedTenant[0] = TenantContext.getCurrentTenant();

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedTenant[0])
                .as("ADMIN 的 effectiveTenantId 必須被強制綁定為自己的租戶，不可被 header 覆蓋為他人租戶")
                .isEqualTo(ownTenantId);
    }

    @Test
    @DisplayName("SUPER_ADMIN 帶入 X-Tenant-ID header 時，允許指定任意租戶（平台級管理需求）")
    void superAdminCanSpecifyAnyTenantViaHeader() throws Exception {
        UUID targetTenantId = UUID.randomUUID();
        UUID superAdminUserId = UUID.randomUUID();

        authenticateAs(superAdminUserId, null, "SUPER_ADMIN");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        request.addHeader(TENANT_HEADER, targetTenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID[] capturedTenant = new UUID[1];
        FilterChain capturingChain = (req, res) -> capturedTenant[0] = TenantContext.getCurrentTenant();

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedTenant[0]).isEqualTo(targetTenantId);
    }

    @Test
    @DisplayName("ADMIN 未帶 X-Tenant-ID header 時，維持使用自己的租戶")
    void adminWithoutHeaderUsesOwnTenant() throws Exception {
        UUID ownTenantId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();

        authenticateAs(adminUserId, ownTenantId.toString(), "ADMIN");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID[] capturedTenant = new UUID[1];
        FilterChain capturingChain = (req, res) -> capturedTenant[0] = TenantContext.getCurrentTenant();

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedTenant[0]).isEqualTo(ownTenantId);
    }

    private void authenticateAs(final UUID userId, final String tenantId, final String role) {
        UserPrincipal principal = new UserPrincipal(userId, "user@example.com", role, tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }
}
