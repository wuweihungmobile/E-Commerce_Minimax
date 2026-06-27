package com.nextkey.ecommerce.integration;

import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M13 商家工作台 — 儀表板 API 整合測試 (Sprint 22 US-003)
 *
 * 測試範圍：
 * - IT-DASH-001: SELLER 查詢儀表板 → 200 + DashboardResponse
 * - IT-DASH-002: BUYER → 403
 * - IT-DASH-003: 無 JWT → 401
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
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // ── IT-DASH-001: SELLER → 200 + DashboardResponse ────────────

    @Test
    @DisplayName("IT-DASH-001: SELLER 查詢儀表板 → 200 + 完整統計資料")
    @WithMockUser(username = "seller", roles = {"SELLER"})
    void getDashboard_asSeller_returns200WithStats() throws Exception {
        when(orderRepository.countByTenantIdAndCreatedAtAfter(eq(TENANT_ID), any(Instant.class)))
                .thenReturn(5L)
                .thenReturn(20L);
        when(orderRepository.sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(
                eq(TENANT_ID), eq(Order.OrderStatus.COMPLETED), any(Instant.class)))
                .thenReturn(BigDecimal.valueOf(15000.00));
        when(listingRepository.countByTenantIdAndStatus(any(UUID.class), any()))
                .thenReturn(8L);
        when(orderRepository.countByTenantIdAndStatusIn(eq(TENANT_ID), any()))
                .thenReturn(3L);
        when(orderRepository.findTopByTenantIdOrderByCreatedAtDesc(eq(TENANT_ID), any(Pageable.class)))
                .thenReturn(List.of());

        mockMvc.perform(get(BASE_URL))
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
    @DisplayName("IT-DASH-002: BUYER（無 SELLER 角色）→ 403")
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
}
