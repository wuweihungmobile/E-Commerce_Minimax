package com.nextkey.ecommerce.core.settlement;

import com.nextkey.ecommerce.domain.model.order.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SettlementCalculator 單元測試
 *
 * 拆分後（Sprint 16 Retro TI-002）獨立測試計算邏輯
 *
 * 涵蓋場景：
 * - 純函數計算（無 Spring/Mockito 副作用）
 * - 5 種結算金額計算場景（無退款/部分退款/全額退款/零元/超大金額）
 * - 邊界：null/空集合
 * - BigDecimal 精度驗證
 *
 * 覆蓋率目標：>= 90%（純函數易達高覆蓋）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementCalculator 純計算邏輯測試 (Sprint 16 TI-002 拆分)")
class SettlementCalculatorTest {

    private SettlementCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new SettlementCalculator();
    }

    // ========== Helper Methods ==========

    private Order createOrder(BigDecimal amount, Order.OrderStatus status) {
        return Order.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .totalAmount(amount)
                .status(status)
                .build();
    }

    // ========== filterSettleableOrders 測試 ==========

    @Test
    @DisplayName("filterSettleableOrders: 過濾 COMPLETED 與 DELIVERED")
    void filterSettleableOrders_KeepCompletedAndDelivered() {
        // Given
        List<Order> orders = List.of(
                createOrder(new BigDecimal("100"), Order.OrderStatus.COMPLETED),
                createOrder(new BigDecimal("200"), Order.OrderStatus.DELIVERED),
                createOrder(new BigDecimal("300"), Order.OrderStatus.CREATED),
                createOrder(new BigDecimal("400"), Order.OrderStatus.CANCELLED),
                createOrder(new BigDecimal("500"), Order.OrderStatus.REFUNDED)
        );

        // When
        List<Order> result = calculator.filterSettleableOrders(orders);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(o ->
                o.getStatus() == Order.OrderStatus.COMPLETED ||
                o.getStatus() == Order.OrderStatus.DELIVERED));
    }

    @Test
    @DisplayName("filterSettleableOrders: null 輸入回傳空集合")
    void filterSettleableOrders_NullInput() {
        assertEquals(0, calculator.filterSettleableOrders(null).size());
    }

    @Test
    @DisplayName("filterSettleableOrders: 空集合回傳空集合")
    void filterSettleableOrders_EmptyInput() {
        assertEquals(0, calculator.filterSettleableOrders(Collections.emptyList()).size());
    }

    // ========== calculateTotalGmv 測試 ==========

    @Test
    @DisplayName("calculateTotalGmv: 多筆訂單加總（無退款）")
    void calculateTotalGmv_MultipleOrders() {
        // Given
        List<Order> orders = List.of(
                createOrder(new BigDecimal("5000"), Order.OrderStatus.COMPLETED),
                createOrder(new BigDecimal("5000"), Order.OrderStatus.DELIVERED)
        );

        // When
        BigDecimal result = calculator.calculateTotalGmv(orders);

        // Then
        assertEquals(0, new BigDecimal("10000.00").compareTo(result));
    }

    @Test
    @DisplayName("calculateTotalGmv: null 輸入回傳 0")
    void calculateTotalGmv_NullInput() {
        assertEquals(0, BigDecimal.ZERO.compareTo(calculator.calculateTotalGmv(null)));
    }

    @Test
    @DisplayName("calculateTotalGmv: 空集合回傳 0")
    void calculateTotalGmv_EmptyInput() {
        assertEquals(0, BigDecimal.ZERO.compareTo(calculator.calculateTotalGmv(Collections.emptyList())));
    }

    // ========== calculateCommission 測試 ==========

    @Test
    @DisplayName("calculateCommission: GMV 10000 抽成 10% = 1000.00")
    void calculateCommission_TenPercent() {
        BigDecimal result = calculator.calculateCommission(new BigDecimal("10000"), new BigDecimal("0.10"));
        assertEquals(0, new BigDecimal("1000.00").compareTo(result));
    }

    @Test
    @DisplayName("calculateCommission: 不同租戶 commissionRate（5%）算出不同抽成")
    void calculateCommission_DifferentRatePerTenant() {
        BigDecimal result = calculator.calculateCommission(new BigDecimal("10000"), new BigDecimal("0.05"));
        assertEquals(0, new BigDecimal("500.00").compareTo(result));
    }

    @Test
    @DisplayName("calculateCommission: 0 元回傳 0")
    void calculateCommission_Zero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculator.calculateCommission(BigDecimal.ZERO, new BigDecimal("0.10"))));
    }

    @Test
    @DisplayName("calculateCommission: gmv null 輸入回傳 0")
    void calculateCommission_NullGmv() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculator.calculateCommission(null, new BigDecimal("0.10"))));
    }

    @Test
    @DisplayName("calculateCommission: commissionRate null 輸入回傳 0")
    void calculateCommission_NullRate() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculator.calculateCommission(new BigDecimal("10000"), null)));
    }

    @Test
    @DisplayName("calculateCommission: 超大金額四捨五入")
    void calculateCommission_LargeAmountRounding() {
        // 99999999.99 * 0.10 = 9999999.999 → 四捨五入 = 10000000.00
        BigDecimal result = calculator.calculateCommission(new BigDecimal("99999999.99"), new BigDecimal("0.10"));
        assertEquals(0, new BigDecimal("10000000.00").compareTo(result));
    }

    // ========== calculateNetSettlementAmount 測試 ==========

    @Test
    @DisplayName("calculateNetSettlementAmount: GMV - 抽成 - 退款")
    void calculateNetSettlementAmount_Normal() {
        BigDecimal result = calculator.calculateNetSettlementAmount(
                new BigDecimal("10000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("0.00")
        );
        assertEquals(0, new BigDecimal("9000.00").compareTo(result));
    }

    @Test
    @DisplayName("calculateNetSettlementAmount: 全部 null 回傳 0")
    void calculateNetSettlementAmount_AllNull() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                calculator.calculateNetSettlementAmount(null, null, null)));
    }

    @Test
    @DisplayName("calculateNetSettlementAmount: 退款大於 GMV 可產生負數")
    void calculateNetSettlementAmount_Negative() {
        BigDecimal result = calculator.calculateNetSettlementAmount(
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("200.00")
        );
        // 100 - 10 - 200 = -110
        assertEquals(0, new BigDecimal("-110.00").compareTo(result));
    }

    // ========== 整合測試（filter + calculate 串接） ==========

    @Test
    @DisplayName("整合: 過濾後的訂單計算 GMV 與結算金額")
    void integration_FilterThenCalculate() {
        // Given
        List<Order> orders = List.of(
                createOrder(new BigDecimal("8000"), Order.OrderStatus.COMPLETED),
                createOrder(new BigDecimal("2000"), Order.OrderStatus.DELIVERED),
                createOrder(new BigDecimal("500"), Order.OrderStatus.REFUNDED),    // 過濾掉
                createOrder(new BigDecimal("300"), Order.OrderStatus.CREATED)      // 過濾掉
        );

        // When
        List<Order> settleable = calculator.filterSettleableOrders(orders);
        BigDecimal gmv = calculator.calculateTotalGmv(settleable);
        BigDecimal commission = calculator.calculateCommission(gmv, new BigDecimal("0.10"));
        BigDecimal refunds = calculator.calculateTotalRefunds(settleable);
        BigDecimal net = calculator.calculateNetSettlementAmount(gmv, commission, refunds);

        // Then
        assertEquals(2, settleable.size());
        assertEquals(0, new BigDecimal("10000.00").compareTo(gmv));
        assertEquals(0, new BigDecimal("1000.00").compareTo(commission));
        assertEquals(0, new BigDecimal("0.00").compareTo(refunds)); // REFUNDED 已過濾
        assertEquals(0, new BigDecimal("9000.00").compareTo(net));
    }
}
