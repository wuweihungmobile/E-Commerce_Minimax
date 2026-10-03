package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M13 商家工作台 — 儀表板 API 整合測試 (Sprint 22 US-003；Sprint 240 DEF-326 改為以店鋪租戶的呼叫者測試)
 *
 * 測試範圍：
 * - IT-DASH-001: 店主（STORE_OWNER）與有店鋪的 SELLER 查詢自己店鋪的儀表板 → 200 + DashboardResponse
 * - IT-DASH-002: BUYER → 403
 * - IT-DASH-003: 無 JWT → 401
 * - IT-DASH-004: 系統租戶的呼叫者（換發前簽發、仍帶 SELLER 角色的舊 token）→ 403，且完全不查詢
 *
 * <p>Sprint 240 之前，IT-DASH-001 用的是系統租戶（沒有店鋪的 SELLER 讀到所有一般消費者訂單的統計）——那正是 DEF-326 的缺陷，
 * 測試把它當成正常行為固定下來。呼叫者必須是真正的 {@link UserPrincipal}：{@code TenantContextFilter} 只認它，
 * {@code @WithMockUser} 的主體會被當成匿名而落到系統租戶。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M13: M13 商家工作台儀表板 API 整合測試")
class M13SellerDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private ListingRepository listingRepository;

    private static final String BASE_URL = "/v2/seller/dashboard";
    private static final UUID STORE_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** 以真正的 UserPrincipal 驗證身分（租戶來自 principal，與 JwtAuthenticationFilter 建立的一致）。 */
    private static RequestPostProcessor callerOf(final String role, final UUID tenantId) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), role.toLowerCase() + "@example.com", role,
                tenantId.toString());
        return SecurityMockMvcRequestPostProcessors.authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    // ── IT-DASH-001: 店鋪的呼叫者 → 200 + DashboardResponse ────────

    @ParameterizedTest
    @ValueSource(strings = {"STORE_OWNER", "SELLER"})
    @DisplayName("IT-DASH-001: 店主與有店鋪的 SELLER 查詢自己店鋪的儀表板 → 200 + 完整統計資料")
    void getDashboard_asStoreCaller_returns200WithStats(final String role) throws Exception {
        when(orderRepository.countByTenantIdAndCreatedAtAfter(eq(STORE_TENANT_ID), any(Instant.class)))
                .thenReturn(5L)
                .thenReturn(20L);
        when(orderRepository.sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(
                eq(STORE_TENANT_ID), eq(Order.OrderStatus.COMPLETED), any(Instant.class)))
                .thenReturn(BigDecimal.valueOf(15000.00));
        when(listingRepository.countByTenantIdAndStatus(eq(STORE_TENANT_ID), any()))
                .thenReturn(8L);
        when(orderRepository.countByTenantIdAndStatusIn(eq(STORE_TENANT_ID), any()))
                .thenReturn(3L);
        when(orderRepository.findTopByTenantIdOrderByCreatedAtDesc(eq(STORE_TENANT_ID), any(Pageable.class)))
                .thenReturn(List.of());

        mockMvc.perform(get(BASE_URL).with(callerOf(role, STORE_TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCount7d").value(5))
                .andExpect(jsonPath("$.data.orderCount30d").value(20))
                .andExpect(jsonPath("$.data.revenue30d").value(15000.00))
                .andExpect(jsonPath("$.data.activeListingCount").value(8))
                .andExpect(jsonPath("$.data.pendingOrderCount").value(3));
    }

    // ── IT-DASH-002: BUYER → 403 ──────────────────────────────────

    @Test
    @DisplayName("IT-DASH-002: BUYER（無 SELLER／STORE_OWNER 角色）→ 403")
    @WithMockUser(username = "buyer", authorities = {"order:create"})
    void getDashboard_asBuyer_returns403() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isForbidden());
    }

    // ── IT-DASH-003: 無 JWT → 401 ─────────────────────────────────

    @Test
    @DisplayName("IT-DASH-003: 無 JWT → 401")
    void getDashboard_noJwt_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    // ── IT-DASH-004: 系統租戶的呼叫者 → 403，且不查詢 ──────────────

    @ParameterizedTest
    @ValueSource(strings = {"STORE_OWNER", "SELLER"})
    @DisplayName("IT-DASH-004: DEF-326——系統租戶的呼叫者（沒有店鋪）→ 403 E-1007，且完全不查詢（不得讀到所有一般消費者訂單的統計）")
    void getDashboard_asSystemTenantCaller_isForbiddenWithoutQuerying(final String role) throws Exception {
        mockMvc.perform(get(BASE_URL).with(callerOf(role, SYSTEM_TENANT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("E-1007"));

        verifyNoInteractions(orderRepository, listingRepository);
    }
}
