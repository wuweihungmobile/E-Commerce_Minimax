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

    /**
     * Sprint 153（DEF-192）：舊版 {@code shouldNotFilter} 以 {@code getServletPath().startsWith(
     * "/v2/auth/")} 判斷，對整個 {@code /v2/auth/*} 前綴一律跳過本 filter，意圖只排除
     * register/login/refresh 等未驗證端點，卻連同 Sprint 94（AI-2428）新增的兩個「已驗證」端點
     * （{@code GET /v2/auth/me/data-export}、{@code DELETE /v2/auth/me}）也一併跳過，導致這兩個
     * 端點的 {@code UserPrivacyService} 永遠讀不到 {@code TenantContext.getCurrentUser()}（恆為
     * null），實際呼叫回 404「找不到使用者」（E-1006）。
     *
     * <p>此缺陷在既有 {@code AuthControllerE2ETest}（{@code @SpringBootTest} 內嵌容器，
     * {@code integration-test} profile 未設定 context-path）中長期未被發現——該測試環境下
     * {@code request.getServletPath()} 實際回傳空字串，讓舊版判斷形同沒有生效、filter 照常執行，
     * 「意外綠燈」掩蓋了正式環境（{@code application.yml} 設有 {@code server.servlet.context-path:
     * /api}）真實封裝後執行會 100% 重現的缺陷（以 Playwright 對 {@code make validate-e2e} 才被
     * 揭露）。修法改用明確白名單 + {@code getRequestURI()/getContextPath()}（見
     * {@link TenantContextFilter#UNAUTHENTICATED_AUTH_PATHS} 完整說明），不受 context-path
     * 設定與否影響，本測試用真實路徑字串驗證，不依賴容器對 servlet path 的解析巧合。
     */
    @Test
    @DisplayName("Sprint 153（DEF-192）：已驗證使用者呼叫 /v2/auth/me/data-export，"
            + "TenantContext.getCurrentUser() 仍須正確設定（舊版 shouldNotFilter 會誤跳過此已驗證端點）")
    void authenticatedRequestToAuthDataExportPathStillPopulatesTenantContext() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        authenticateAs(userId, tenantId.toString(), "BUYER");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/auth/me/data-export");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID[] capturedUser = new UUID[1];
        FilterChain capturingChain = (req, res) -> capturedUser[0] = TenantContext.getCurrentUser();

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedUser[0])
                .as("已驗證的 data-export 請求必須能取得 TenantContext.getCurrentUser()，"
                        + "否則 UserPrivacyService.exportMyData 會誤判為找不到使用者")
                .isEqualTo(userId);
    }

    @Test
    @DisplayName("Sprint 153（DEF-192）：已驗證使用者呼叫 DELETE /v2/auth/me，"
            + "TenantContext.getCurrentUser() 仍須正確設定")
    void authenticatedRequestToDeleteMePathStillPopulatesTenantContext() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        authenticateAs(userId, tenantId.toString(), "BUYER");

        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/v2/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID[] capturedUser = new UUID[1];
        FilterChain capturingChain = (req, res) -> capturedUser[0] = TenantContext.getCurrentUser();

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedUser[0])
                .as("已驗證的自助刪除帳戶請求必須能取得 TenantContext.getCurrentUser()，"
                        + "否則 UserPrivacyService.deleteMyAccount 會誤判為找不到使用者")
                .isEqualTo(userId);
    }

    @Test
    @DisplayName("Sprint 153（DEF-192）：未驗證請求呼叫 /v2/auth/login 仍維持完全跳過本 filter"
            + "（TenantContext 完全不被觸碰，避免與其他租戶共用 RateLimitFilter 的同一個限流桶）")
    void unauthenticatedRequestToLoginPathStillSkipsFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID[] capturedUser = new UUID[1];
        UUID[] capturedTenant = new UUID[1];
        boolean[] chainInvoked = new boolean[1];
        FilterChain capturingChain = (req, res) -> {
            chainInvoked[0] = true;
            capturedUser[0] = TenantContext.getCurrentUser();
            capturedTenant[0] = TenantContext.getCurrentTenant();
        };

        filter.doFilter(request, response, capturingChain);

        assertThat(chainInvoked[0]).as("shouldNotFilter=true 仍必須放行到下一個 filter").isTrue();
        assertThat(capturedUser[0]).isNull();
        assertThat(capturedTenant[0])
                .as("被跳過的請求，TenantContext 完全不被設定（不是 system tenant，是 null）——"
                        + "與 authenticatedRequestTo*PathStillPopulatesTenantContext 兩案例互為對照組")
                .isNull();
    }

    @Test
    @DisplayName("Sprint 153（item 13）：未驗證請求呼叫 /v2/auth/oauth/login 同樣完全跳過本 filter"
            + "（與一般 login 同理，登入前不應具備租戶身分，也不應與其他租戶共用限流桶）")
    void unauthenticatedRequestToOAuthLoginPathSkipsFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/oauth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean[] chainInvoked = new boolean[1];
        UUID[] capturedTenant = new UUID[1];
        FilterChain capturingChain = (req, res) -> {
            chainInvoked[0] = true;
            capturedTenant[0] = TenantContext.getCurrentTenant();
        };

        filter.doFilter(request, response, capturingChain);

        assertThat(chainInvoked[0]).isTrue();
        assertThat(capturedTenant[0]).isNull();
    }

    @Test
    @DisplayName("Sprint 153（item 13）：已驗證使用者呼叫 /v2/auth/oauth/link 不跳過本 filter"
            + "（需要已驗證使用者，與 data-export/deleteMyAccount 同理應正確取得 TenantContext）")
    void authenticatedRequestToOAuthLinkPathPopulatesTenantContext() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        authenticateAs(userId, tenantId.toString(), "BUYER");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/auth/oauth/link");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UUID[] capturedUser = new UUID[1];
        FilterChain capturingChain = (req, res) -> capturedUser[0] = TenantContext.getCurrentUser();

        filter.doFilter(request, response, capturingChain);

        assertThat(capturedUser[0]).isEqualTo(userId);
    }

    /**
     * Sprint 166（DEF-217）：{@code resolveEffectiveTenantId} 的 SUPER_ADMIN 分支對
     * {@code X-Tenant-ID} header 直接呼叫 {@code UUID.fromString(requestedTenantId)}（第 126 行），
     * 未包 try/catch。此 filter 執行於 DispatcherServlet **之前**，丟出的 {@code IllegalArgumentException}
     * 無法被 {@code GlobalExceptionHandler}（{@code @RestControllerAdvice}）攔截——與 Sprint 162/163
     * 的 {@code HttpMessageNotReadableException}/{@code MethodArgumentTypeMismatchException}
     * 不同層級，那兩者發生於 DispatcherServlet 分派過程中，此處發生於 Servlet Filter 鏈，
     * 全域例外處理器結構性地攔不到。
     */
    @Test
    @DisplayName("Sprint 166（DEF-217）：SUPER_ADMIN 帶入非法格式的 X-Tenant-ID header 時，"
            + "filter 不可讓未攔截例外往外拋，且必須回應 400 而非讓請求繼續往下傳遞")
    void superAdminInvalidTenantHeaderFormat_doesNotThrowAndShortCircuits() throws Exception {
        UUID superAdminUserId = UUID.randomUUID();
        authenticateAs(superAdminUserId, null, "SUPER_ADMIN");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/orders");
        request.addHeader(TENANT_HEADER, "not-a-real-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean[] chainInvoked = new boolean[1];
        FilterChain capturingChain = (req, res) -> chainInvoked[0] = true;

        filter.doFilter(request, response, capturingChain);

        assertThat(chainInvoked[0])
                .as("非法 X-Tenant-ID 必須在 filter 內就被擋下，不可放行到下一層（避免 TenantContext 帶著"
                        + "未設定/錯誤的租戶繼續往下）")
                .isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("E-9000");
    }
}
