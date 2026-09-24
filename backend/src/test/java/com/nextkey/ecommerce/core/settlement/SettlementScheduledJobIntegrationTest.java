package com.nextkey.ecommerce.core.settlement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;

/**
 * SettlementGenerator Scheduled Job 實測驗證
 *
 * 目的（Sprint 16 US-003 / Retro AI-003）：
 * - 由於沒有 Staging 環境，建立本地測試替代方案
 * - 透過 Mockito 模擬 Tenant、Order、SettlementStatement Repository
 * - 驗證 Scheduled Job 邏輯：
 *   1. 多租戶場景：每個 ACTIVE tenant 都會生成結算單
 *   2. 冪等性：已存在結算單不重複建立
 *   3. 異常場景：無訂單租戶跳過
 *   4. 金額計算正確性
 *
 * 覆蓋率目標：核心業務路徑 100%
 *
 * @author Sprint 16 (US-003)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementGenerator Scheduled Job 實測驗證 (Sprint 16 US-003)")
class SettlementScheduledJobIntegrationTest {

    @Mock
    private SettlementStatementRepository settlementRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SettlementAdjustmentRepository adjustmentRepository;

    @Mock
    private SettlementCalculator calculator;

    @Mock
    private SettlementMapper mapper;

    private SettlementGenerator settlementGenerator;

    // ========== 場景 1: 多租戶 + 完整訂單 ==========

    @Test
    @DisplayName("AC-001/002/003: 多租戶 + 每個租戶 3 筆訂單 = 各生成 1 個結算單")
    void multiTenantWithOrders_GeneratesStatementsForAllActiveTenants() {
        // Given: 3 個 ACTIVE tenants，每個 3 筆訂單
        Tenant tenant1 = createActiveTenant("tenant-1");
        Tenant tenant2 = createActiveTenant("tenant-2");
        Tenant tenant3 = createActiveTenant("tenant-3");

        // 重新建立 generator 確保 mock 是 fresh
        settlementGenerator = new SettlementGenerator(
                settlementRepository, tenantRepository, orderRepository, paymentRepository,
                adjustmentRepository, calculator, mapper);

        when(tenantRepository.findByStatus(TenantStatus.ACTIVE))
                .thenReturn(List.of(tenant1, tenant2, tenant3));

        // 模擬每個租戶沒有已存在的結算單
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any()))
                .thenReturn(List.of());

        // 模擬每個租戶的 tenant repository findById
        when(tenantRepository.findById(tenant1.getId())).thenReturn(Optional.of(tenant1));
        when(tenantRepository.findById(tenant2.getId())).thenReturn(Optional.of(tenant2));
        when(tenantRepository.findById(tenant3.getId())).thenReturn(Optional.of(tenant3));

        // 模擬每個租戶的訂單（3 筆 COMPLETED）
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any()))
                .thenReturn(createCompletedOrders(3, "10000"));

        // 模擬金額計算
        when(calculator.filterSettleableOrders(any()))
                .thenAnswer(inv -> createCompletedOrders(3, "10000"));
        when(calculator.calculateTotalGmv(any())).thenReturn(new BigDecimal("30000.00"));
        when(calculator.calculateCommission(any(), any())).thenReturn(new BigDecimal("3000.00"));
        when(calculator.calculateTotalRefunds(any(), any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateNetSettlementAmount(any(), any(), any()))
                .thenReturn(new BigDecimal("27000.00"));

        // 模擬 save 返回
        when(settlementRepository.save(any(SettlementStatement.class)))
                .thenAnswer(inv -> {
                    SettlementStatement s = inv.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });

        // When: 觸發週一自動生成
        settlementGenerator.generateWeeklyStatements();

        // Then: 應該為 3 個 tenant 各建立 1 個結算單
        ArgumentCaptor<SettlementStatement> captor = ArgumentCaptor.forClass(SettlementStatement.class);
        verify(settlementRepository, times(3)).save(captor.capture());

        List<SettlementStatement> savedStatements = captor.getAllValues();
        for (SettlementStatement statement : savedStatements) {
            assertEquals(SettlementStatus.PENDING, statement.getStatus());
            assertEquals(new BigDecimal("30000.00"), statement.getTotalGmv());
            assertEquals(new BigDecimal("3000.00"), statement.getCommissionAmount());
            assertEquals(new BigDecimal("27000.00"), statement.getNetSettlementAmount());
            assertEquals(3, statement.getTotalOrders());
        }
    }

    // ========== 場景 2: 冪等性 - 重複執行不重複建立 ==========

    @Test
    @DisplayName("AC-003.2 / 冪等性: 重複執行不會建立重複的結算單")
    void duplicateExecution_Idempotent() {
        // Given: 1 個 tenant，已有 PENDING 結算單
        Tenant tenant = createActiveTenant("tenant-1");
        settlementGenerator = new SettlementGenerator(
                settlementRepository, tenantRepository, orderRepository, paymentRepository,
                adjustmentRepository, calculator, mapper);

        when(tenantRepository.findByStatus(TenantStatus.ACTIVE))
                .thenReturn(List.of(tenant));

        // 模擬：已有該期間的結算單
        SettlementStatement existingStatement = SettlementStatement.builder()
                .id(UUID.randomUUID())
                .tenantId(tenant.getId())
                .periodStart(LocalDate.now().minusDays(7))
                .periodEnd(LocalDate.now().minusDays(1))
                .status(SettlementStatus.PENDING)
                .build();
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(
                eq(tenant.getId()), any(), any()))
                .thenReturn(List.of(existingStatement));

        // When
        settlementGenerator.generateWeeklyStatements();

        // Then: 不應該呼叫 save（冪等性）
        verify(settlementRepository, never()).save(any(SettlementStatement.class));
    }

    // ========== 場景 3: 異常場景 - 無訂單租戶 ==========

    @Test
    @DisplayName("AC-004.1: 無訂單的租戶：仍會建立 PENDING 結算單（金額為 0）")
    void tenantWithNoOrders_GeneratesZeroAmountStatement() {
        // Given: 1 個 tenant 沒有訂單
        Tenant tenant = createActiveTenant("tenant-1");
        settlementGenerator = new SettlementGenerator(
                settlementRepository, tenantRepository, orderRepository, paymentRepository,
                adjustmentRepository, calculator, mapper);

        when(tenantRepository.findByStatus(TenantStatus.ACTIVE))
                .thenReturn(List.of(tenant));
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any()))
                .thenReturn(List.of());
        when(calculator.filterSettleableOrders(any())).thenReturn(List.of());
        when(calculator.calculateTotalGmv(any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateCommission(any(), any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateTotalRefunds(any(), any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateNetSettlementAmount(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.save(any(SettlementStatement.class)))
                .thenAnswer(inv -> {
                    SettlementStatement s = inv.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });

        // When
        settlementGenerator.generateWeeklyStatements();

        // Then: 仍會建立結算單（金額為 0）— 符合當前業務邏輯
        verify(settlementRepository, times(1)).save(any(SettlementStatement.class));
    }

    // ========== 場景 4: 單租戶 - 完整金額計算 ==========

    @Test
    @DisplayName("AC-002.1: 單租戶 + 5 筆訂單 + 1 筆退款 = 正確金額")
    void singleTenant_WithRefund_CalculatesCorrectly() {
        // Given: 1 個 tenant，5 筆訂單
        Tenant tenant = createActiveTenant("tenant-1");
        settlementGenerator = new SettlementGenerator(
                settlementRepository, tenantRepository, orderRepository, paymentRepository,
                adjustmentRepository, calculator, mapper);

        when(tenantRepository.findByStatus(TenantStatus.ACTIVE))
                .thenReturn(List.of(tenant));
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any()))
                .thenReturn(List.of());

        List<Order> orders = createCompletedOrders(5, "10000");
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(orders);
        when(calculator.filterSettleableOrders(any())).thenReturn(orders);
        when(calculator.calculateTotalGmv(any())).thenReturn(new BigDecimal("50000.00"));
        when(calculator.calculateCommission(any(), any())).thenReturn(new BigDecimal("5000.00"));
        when(calculator.calculateTotalRefunds(any(), any())).thenReturn(new BigDecimal("2000.00"));
        when(calculator.calculateNetSettlementAmount(any(), any(), any()))
                .thenReturn(new BigDecimal("43000.00"));
        when(settlementRepository.save(any(SettlementStatement.class)))
                .thenAnswer(inv -> {
                    SettlementStatement s = inv.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });

        // When
        settlementGenerator.generateWeeklyStatements();

        // Then: 結算金額 = 50000 - 5000 - 2000 = 43000
        ArgumentCaptor<SettlementStatement> captor = ArgumentCaptor.forClass(SettlementStatement.class);
        verify(settlementRepository).save(captor.capture());
        SettlementStatement saved = captor.getValue();
        assertEquals(5, saved.getTotalOrders());
        assertEquals(new BigDecimal("43000.00"), saved.getNetSettlementAmount());
    }

    // ========== 場景 5: 異常 - Tenant 處理失敗不影響其他 Tenant ==========

    @Test
    @DisplayName("AC-異常: 某個 Tenant 處理失敗，其他 Tenant 仍正常處理")
    void oneTenantFails_OthersStillProcessed() {
        // Given: 2 個 tenants，第 1 個會拋例外
        Tenant tenant1 = createActiveTenant("tenant-1");
        Tenant tenant2 = createActiveTenant("tenant-2");
        settlementGenerator = new SettlementGenerator(
                settlementRepository, tenantRepository, orderRepository, paymentRepository,
                adjustmentRepository, calculator, mapper);

        when(tenantRepository.findByStatus(TenantStatus.ACTIVE))
                .thenReturn(List.of(tenant1, tenant2));

        // tenant-1 處理時 settlementRepository 拋例外
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(
                eq(tenant1.getId()), any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        // tenant-2 正常
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(
                eq(tenant2.getId()), any(), any()))
                .thenReturn(List.of());
        when(tenantRepository.findById(tenant2.getId())).thenReturn(Optional.of(tenant2));
        when(orderRepository.findByTenantIdAndCreatedAtInRange(eq(tenant2.getId()), any(), any()))
                .thenReturn(createCompletedOrders(2, "5000"));
        when(calculator.filterSettleableOrders(any())).thenAnswer(inv -> createCompletedOrders(2, "5000"));
        when(calculator.calculateTotalGmv(any())).thenReturn(new BigDecimal("10000.00"));
        when(calculator.calculateCommission(any(), any())).thenReturn(new BigDecimal("1000.00"));
        when(calculator.calculateTotalRefunds(any(), any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateNetSettlementAmount(any(), any(), any()))
                .thenReturn(new BigDecimal("9000.00"));
        when(settlementRepository.save(any(SettlementStatement.class)))
                .thenAnswer(inv -> {
                    SettlementStatement s = inv.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });

        // When
        assertDoesNotThrow(() -> settlementGenerator.generateWeeklyStatements());

        // Then: 僅 tenant-2 成功建立
        verify(settlementRepository, times(1)).save(any(SettlementStatement.class));
    }

    // ========== 場景 6: 結算單號格式 ==========

    @Test
    @DisplayName("AC-002.2: 結算單號格式正確：STL-{tenantId前8碼}-{yyyyMMdd}")
    void statementNumber_FormatIsCorrect() {
        // Given
        Tenant tenant = Tenant.builder()
                .id(UUID.fromString("12345678-aaaa-bbbb-cccc-123456789abc"))
                .status(TenantStatus.ACTIVE)
                .name("Test Tenant")
                .build();
        settlementGenerator = new SettlementGenerator(
                settlementRepository, tenantRepository, orderRepository, paymentRepository,
                adjustmentRepository, calculator, mapper);

        when(tenantRepository.findByStatus(TenantStatus.ACTIVE)).thenReturn(List.of(tenant));
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any()))
                .thenReturn(List.of());
        when(calculator.filterSettleableOrders(any())).thenReturn(List.of());
        when(calculator.calculateTotalGmv(any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateCommission(any(), any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateTotalRefunds(any(), any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateNetSettlementAmount(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.save(any(SettlementStatement.class)))
                .thenAnswer(inv -> {
                    SettlementStatement s = inv.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });

        // When
        settlementGenerator.generateWeeklyStatements();

        // Then
        ArgumentCaptor<SettlementStatement> captor = ArgumentCaptor.forClass(SettlementStatement.class);
        verify(settlementRepository).save(captor.capture());
        String statementNumber = captor.getValue().getStatementNumber();
        assertNotNull(statementNumber);
        assertTrue(statementNumber.startsWith("STL-12345678-"),
                "Statement number should start with STL-{tenantId前8碼}, actual: " + statementNumber);
        // 結算單號末段應為 yyyyMMdd 格式（8 碼數字）
        String datePart = statementNumber.substring(statementNumber.lastIndexOf('-') + 1);
        assertEquals(8, datePart.length(), "Date part should be 8 chars (yyyyMMdd)");
    }

    // ========== 場景 7（Sprint 86）: 折入前期 PENDING adjustment_statements ==========

    @Test
    @DisplayName("Sprint 86: generateStatementForTenant 折入前期 PENDING adjustment_statements，並標記為 APPLIED")
    void generateStatementForTenant_withPendingAdjustments_foldsIntoNetAmount() {
        Tenant tenant = createActiveTenant("tenant-1");
        settlementGenerator = new SettlementGenerator(
                settlementRepository, tenantRepository, orderRepository, paymentRepository,
                adjustmentRepository, calculator, mapper);

        UUID otherStatementId = UUID.randomUUID();
        com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment adjustment1 =
                com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenant.getId())
                        .originalStatementId(otherStatementId)
                        .amount(new BigDecimal("-100.00"))
                        .status(com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment.AdjustmentStatus.PENDING)
                        .build();
        com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment adjustment2 =
                com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenant.getId())
                        .originalStatementId(otherStatementId)
                        .amount(new BigDecimal("-50.00"))
                        .status(com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment.AdjustmentStatus.PENDING)
                        .build();

        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(List.of());
        when(calculator.filterSettleableOrders(any())).thenReturn(List.of());
        when(calculator.calculateTotalGmv(any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateCommission(any(), any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateTotalRefunds(any(), any())).thenReturn(BigDecimal.ZERO);
        when(calculator.calculateNetSettlementAmount(any(), any(), any())).thenReturn(new BigDecimal("1000.00"));
        when(adjustmentRepository.findByTenantIdAndStatus(tenant.getId(),
                com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment.AdjustmentStatus.PENDING))
                .thenReturn(List.of(adjustment1, adjustment2));
        when(settlementRepository.save(any(SettlementStatement.class)))
                .thenAnswer(inv -> {
                    SettlementStatement s = inv.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });

        SettlementStatement result = settlementGenerator.generateStatementForTenant(
                tenant.getId(), LocalDate.now().minusDays(7), LocalDate.now().minusDays(1));

        // 1000 - 100 - 50 = 850
        assertEquals(new BigDecimal("850.00"), result.getNetSettlementAmount());
        assertEquals(new BigDecimal("-150.00"), result.getAdjustmentAmount());

        ArgumentCaptor<List<com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment>> savedCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(adjustmentRepository).saveAll(savedCaptor.capture());
        for (com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment applied : savedCaptor.getValue()) {
            assertEquals(com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment.AdjustmentStatus.APPLIED,
                    applied.getStatus());
            assertEquals(result.getId(), applied.getAppliedStatementId());
        }
    }

    @Test
    @DisplayName("Sprint 86: generateStatementForTenant 依訂單查詢 Payment.refundedAmount 建立退款對照表")
    void generateStatementForTenant_buildsRefundedAmountMapFromPayments() {
        Tenant tenant = createActiveTenant("tenant-1");
        settlementGenerator = new SettlementGenerator(
                settlementRepository, tenantRepository, orderRepository, paymentRepository,
                adjustmentRepository, calculator, mapper);

        List<Order> orders = createCompletedOrders(2, "1000");
        Order refundedOrder = orders.get(0);
        Order untouchedOrder = orders.get(1);

        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.findByTenantIdAndCreatedAtInRange(any(), any(), any())).thenReturn(orders);
        when(calculator.filterSettleableOrders(any())).thenReturn(orders);
        when(calculator.calculateTotalGmv(any())).thenReturn(new BigDecimal("2000.00"));
        when(calculator.calculateCommission(any(), any())).thenReturn(new BigDecimal("200.00"));
        when(calculator.calculateTotalRefunds(any(), any())).thenReturn(new BigDecimal("300.00"));
        when(calculator.calculateNetSettlementAmount(any(), any(), any())).thenReturn(new BigDecimal("1500.00"));
        when(paymentRepository.findByOrderId(refundedOrder.getId())).thenReturn(Optional.of(
                com.nextkey.ecommerce.domain.model.payment.Payment.builder()
                        .refundedAmount(new BigDecimal("300.00"))
                        .build()));
        when(paymentRepository.findByOrderId(untouchedOrder.getId())).thenReturn(Optional.empty());
        when(settlementRepository.save(any(SettlementStatement.class)))
                .thenAnswer(inv -> {
                    SettlementStatement s = inv.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });

        settlementGenerator.generateStatementForTenant(
                tenant.getId(), LocalDate.now().minusDays(7), LocalDate.now().minusDays(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<UUID, BigDecimal>> mapCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(calculator).calculateTotalRefunds(eq(orders), mapCaptor.capture());
        assertEquals(new BigDecimal("300.00"), mapCaptor.getValue().get(refundedOrder.getId()));
        assertFalse(mapCaptor.getValue().containsKey(untouchedOrder.getId()));
    }

    // ========== Helper Methods ==========

    private Tenant createActiveTenant(String name) {
        return Tenant.builder()
                .id(UUID.randomUUID())
                .status(TenantStatus.ACTIVE)
                .name(name)
                .build();
    }

    private List<Order> createCompletedOrders(int count, String amount) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Order.builder()
                        .id(UUID.randomUUID())
                        .status(Order.OrderStatus.COMPLETED)
                        .totalAmount(new BigDecimal(amount))
                        .build())
                .toList();
    }
}
