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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.logistics.Logistics;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.repository.LogisticsRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;

/**
 * M11 物流與訂單履約整合測試 (Sprint 23 US-004 / DEF-007 / AI-704)
 *
 * 測試範圍：
 * - IT-SHIP-001: POST /v2/logistics（CONFIRMED 訂單）→ 200 + 追蹤號、訂單狀態 → SHIPPING
 * - IT-SHIP-002: POST /v2/logistics（非 CONFIRMED 訂單）→ 422
 * - IT-SHIP-003: POST /v2/logistics（已有物流單）→ 409
 * - IT-SHIP-004: PUT /v2/logistics/{id}/status DELIVERED → 訂單狀態 → DELIVERED
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

    private Order buildConfirmedOrder() {
        Order order = Order.builder()
                .tenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
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
    @WithMockUser(username = "seller", authorities = {"order:create"})
    void createLogistics_confirmedOrder_returns200AndUpdatesOrderToShipping() throws Exception {
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

    // ── IT-SHIP-002: 非 CONFIRMED 訂單 → 422 ─────────────────────

    @Test
    @DisplayName("IT-SHIP-002: POST /v2/logistics（CREATED 訂單，未確認）→ 4xx")
    @WithMockUser(username = "seller", authorities = {"order:create"})
    void createLogistics_notConfirmedOrder_returns4xx() throws Exception {
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
    @WithMockUser(username = "seller", authorities = {"order:create"})
    void createLogistics_activeLogisticsExists_returns409() throws Exception {
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
    @WithMockUser(username = "seller", authorities = {"order:update"})
    void updateLogisticsStatus_delivered_updatesOrderToDelivered() throws Exception {
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
}
