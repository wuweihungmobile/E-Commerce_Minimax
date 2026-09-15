package com.nextkey.ecommerce.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.logistics.Logistics;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.LogisticsRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.integration.util.TestSecurityContextHelper;

/**
 * M11 物流與訂單履約整合測試 (Sprint 23 US-004 / DEF-007 / AI-704)
 *
 * 測試範圍：
 * - IT-SHIP-001: POST /v2/logistics（CONFIRMED 訂單）→ 200 + 追蹤號、訂單狀態 → SHIPPING
 * - IT-SHIP-002: POST /v2/logistics（非 CONFIRMED 訂單）→ 422
 * - IT-SHIP-003: POST /v2/logistics（已有物流單）→ 409
 * - IT-SHIP-004: PUT /v2/logistics/{id}/status DELIVERED → 訂單狀態 → DELIVERED
 *
 * <p>Sprint 151（DEF-190 修復的副作用）：原本用 {@code @WithMockUser} 搭配「巧合」等於
 * {@code AppConstants.SYSTEM_TENANT_ID} 的訂單 tenantId 讓 {@code LogisticsService
 * .checkOrderTenant} 的 same-tenant 檢查通過——{@code @WithMockUser} 建立的是 Spring Security
 * 內建 {@code User} principal，非本專案的 {@code UserPrincipal}，{@code TenantContextFilter}
 * 因此無法解析出真正的 tenantId，一律 fallback 到系統租戶（見
 * {@code TestSecurityContextHelper} 既有 Javadoc 對此已有的警示）。DEF-190 修復後
 * {@code checkOrderTenant} 明確排除系統租戶，此僥倖巧合不再成立，IT-SHIP-001/004 直接 403，
 * IT-SHIP-002/003 則因 {@code .is4xxClientError()} 寬鬆斷言而「假綠燈」（403 也算 4xx，
 * 實際上根本沒驗到原本要測的 422/409 分支）。改用既有的 {@code TestSecurityContextHelper}
 * 建立真正的 {@code UserPrincipal}，租戶 ID 改用與系統租戶不同的專屬常數，恢復測試原意。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M11: M11 物流與訂單履約整合測試")
class M11LogisticsOrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private LogisticsRepository logisticsRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String BASE_URL = "/v2/logistics";
    private static final UUID ORDER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LOGISTICS_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    // DEF-190：刻意選一個與 AppConstants.SYSTEM_TENANT_ID（00000000-...-0001）不同的值，
    // 確保測試驗的是「真正的同租戶」而非巧合落在系統租戶 fallback。
    private static final UUID TENANT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SELLER_USER_ID = UUID.fromString("40000000-0000-0000-0000-000000000002");

    @AfterEach
    void clearSecurityContext() {
        TestSecurityContextHelper.clear();
    }

    private Order buildConfirmedOrder() {
        Order order = Order.builder()
                .tenantId(TENANT_ID)
                .userId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .shippingRecipientName("測試收件人")
                .shippingPhone("0912345678")
                .shippingAddress("台北市信義區信義路五段7號")
                .totalAmount(BigDecimal.valueOf(1000))
                .build();
        order.setId(ORDER_ID);
        order.setStatus(Order.OrderStatus.CONFIRMED);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }

    private Logistics buildPendingLogistics() {
        Logistics logistics = Logistics.builder()
                .orderId(ORDER_ID)
                .logisticsProvider(Logistics.LogisticsProvider.HCT)
                .trackingNumber("HCT-20260901-ABCD1234")
                .status(Logistics.LogisticsStatus.PENDING)
                .receiverName("測試收件人")
                .receiverPhone("0912345678")
                .shippingAddress("台北市信義區信義路五段7號")
                .logisticsData(new HashMap<>())
                .build();
        logistics.setId(LOGISTICS_ID);
        logistics.setCreatedAt(Instant.now());
        logistics.setUpdatedAt(Instant.now());
        return logistics;
    }

    // ── IT-SHIP-001: CONFIRMED 訂單建立物流 → 訂單狀態 SHIPPING ────

    @Test
    @DisplayName("IT-SHIP-001: POST /v2/logistics（CONFIRMED 訂單）→ 200 + 追蹤號，訂單變 SHIPPING")
    void createLogistics_confirmedOrder_returns200AndUpdatesOrderToShipping() throws Exception {
        // DEF-191（Sprint 152）：createLogistics 改用 order:update 授權，見 LogisticsController Javadoc。
        TestSecurityContextHelper.setUserContext(SELLER_USER_ID, TENANT_ID, "SELLER", "order:update");
        Order order = buildConfirmedOrder();
        Logistics savedLogistics = buildPendingLogistics();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(logisticsRepository.findByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        when(logisticsRepository.save(any(Logistics.class))).thenReturn(savedLogistics);
        // DEF-123：訂單狀態轉換改為 claim-before-external-call 的原子 CAS，
        // 取代原本的「setStatus(SHIPPING) → save()」。
        when(orderRepository.updateStatusIfCurrent(ORDER_ID, Order.OrderStatus.CONFIRMED, Order.OrderStatus.SHIPPING))
                .thenReturn(1);

        String body = """
            {"orderId": "%s", "logisticsProvider": "HCT"}
            """.formatted(ORDER_ID);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.logisticsId").isNotEmpty())
                .andExpect(jsonPath("$.data.trackingNumber").value("HCT-20260901-ABCD1234"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // 驗證訂單狀態已透過原子 CAS 更新為 SHIPPING
        verify(orderRepository).updateStatusIfCurrent(ORDER_ID, Order.OrderStatus.CONFIRMED, Order.OrderStatus.SHIPPING);
    }

    // ── DEF-191（Sprint 152）：鎖住授權需求已改為 order:update ──────

    @Test
    @DisplayName("DEF-191: 僅持有舊授權 order:create（無 order:update）→ 403，證明修法已生效")
    void createLogistics_onlyLegacyOrderCreateAuthority_returns403() throws Exception {
        // 修法前這組授權足以通過 @PreAuthorize；修法後 order:create 單獨已不足夠，
        // 必須是 order:update（SELLER 角色實際持有的權限，見 RolePermissionMappingTest
        // #seller_hasOrderUpdateButNotOrderCreate）。此測試證明「舊授權不再放行」，
        // 與 IT-SHIP-001（僅 order:update 即可放行）互為一體兩面的驗證。
        // 訂單/物流查詢比照 IT-SHIP-001 完整 mock，確保若授權意外放行，斷言看到的會是
        // 明確的 200（而非因未 mock 資料而巧合得到的 404），讓紅燈訊號不模稜兩可。
        TestSecurityContextHelper.setUserContext(SELLER_USER_ID, TENANT_ID, "SELLER", "order:create");
        Order order = buildConfirmedOrder();
        Logistics savedLogistics = buildPendingLogistics();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(logisticsRepository.findByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        when(logisticsRepository.save(any(Logistics.class))).thenReturn(savedLogistics);
        when(orderRepository.updateStatusIfCurrent(ORDER_ID, Order.OrderStatus.CONFIRMED, Order.OrderStatus.SHIPPING))
                .thenReturn(1);

        String body = """
            {"orderId": "%s", "logisticsProvider": "HCT"}
            """.formatted(ORDER_ID);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── IT-SHIP-002: 非 CONFIRMED 訂單 → 422 ─────────────────────

    @Test
    @DisplayName("IT-SHIP-002: POST /v2/logistics（CREATED 訂單，未確認）→ 4xx")
    void createLogistics_notConfirmedOrder_returns4xx() throws Exception {
        TestSecurityContextHelper.setUserContext(SELLER_USER_ID, TENANT_ID, "SELLER", "order:update");
        Order order = buildConfirmedOrder();
        order.setStatus(Order.OrderStatus.CREATED);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        String body = """
            {"orderId": "%s", "logisticsProvider": "HCT"}
            """.formatted(ORDER_ID);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    // ── IT-SHIP-003: 重複建立物流（已有 PENDING）→ 409 ────────────

    @Test
    @DisplayName("IT-SHIP-003: POST /v2/logistics（已有物流單）→ 409")
    void createLogistics_activeLogisticsExists_returns409() throws Exception {
        TestSecurityContextHelper.setUserContext(SELLER_USER_ID, TENANT_ID, "SELLER", "order:update");
        Order order = buildConfirmedOrder();
        Logistics existingLogistics = buildPendingLogistics();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(logisticsRepository.findByOrderId(ORDER_ID)).thenReturn(Collections.singletonList(existingLogistics));

        String body = """
            {"orderId": "%s", "logisticsProvider": "HCT"}
            """.formatted(ORDER_ID);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    // ── IT-SHIP-004: 物流送達 → 訂單狀態 DELIVERED ────────────────

    @Test
    @DisplayName("IT-SHIP-004: PUT /v2/logistics/{id}/status DELIVERED → 訂單狀態 → DELIVERED")
    void updateLogisticsStatus_delivered_updatesOrderToDelivered() throws Exception {
        TestSecurityContextHelper.setUserContext(SELLER_USER_ID, TENANT_ID, "SELLER", "order:update");
        Logistics logistics = buildPendingLogistics();
        Order order = buildConfirmedOrder();
        order.setStatus(Order.OrderStatus.SHIPPING);

        when(logisticsRepository.findById(LOGISTICS_ID)).thenReturn(Optional.of(logistics));
        when(logisticsRepository.save(any(Logistics.class))).thenReturn(logistics);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        // DEF-159：訂單狀態同步改為原子 CAS，取代原本的「setStatus(DELIVERED) → save()」。
        when(orderRepository.updateStatusIfCurrent(ORDER_ID, Order.OrderStatus.SHIPPING, Order.OrderStatus.DELIVERED))
                .thenReturn(1);

        mockMvc.perform(put(BASE_URL + "/" + LOGISTICS_ID + "/status")
                        .param("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 驗證訂單狀態已透過原子 CAS 更新為 DELIVERED
        verify(orderRepository).updateStatusIfCurrent(ORDER_ID, Order.OrderStatus.SHIPPING, Order.OrderStatus.DELIVERED);
    }

    // ── Sprint 162: 強型別 enum 欄位的 JSON 反序列化非法值 → 400（而非 500） ─────
    //
    // 延續 Sprint 161 §4 誠實揭露：logisticsProvider 欄位本身已是 LogisticsProvider enum，
    // 非法字面值會在 Jackson 反序列化階段（進入 Controller 方法前）拋出
    // HttpMessageNotReadableException，先前全域例外處理器未攔截此類型，落入 catch-all 變成 500。

    @Test
    @DisplayName("Sprint 162: POST /v2/logistics logisticsProvider 帶非法列舉字面值 → 400（E-9000），而非 500")
    void createLogistics_invalidLogisticsProviderLiteral_returns400NotInternalServerError() throws Exception {
        TestSecurityContextHelper.setUserContext(SELLER_USER_ID, TENANT_ID, "SELLER", "order:update");

        String body = """
            {"orderId": "%s", "logisticsProvider": "NOT_A_REAL_PROVIDER"}
            """.formatted(ORDER_ID);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E-9000"));
    }
}
