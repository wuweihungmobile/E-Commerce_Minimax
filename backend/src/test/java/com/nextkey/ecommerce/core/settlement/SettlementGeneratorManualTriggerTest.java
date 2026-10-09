package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * SettlementGenerator: 手動觸發/補產結算單（DEF-287，Sprint 208）。
 *
 * <p>本方法只驗證新增的入口本身（參數驗證、DTO 轉換），不重複驗證
 * {@link SettlementGenerator#generateStatementForTenant} 已有測試覆蓋的冪等與併發防護
 * （見 {@link SettlementGeneratorClaimTest}）——本方法對它們一視同仁，不新增也不繞過。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SettlementGenerator: 手動觸發/補產結算單（DEF-287）")
class SettlementGeneratorManualTriggerTest {

    @Mock private SettlementStatementRepository settlementRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SettlementAdjustmentRepository adjustmentRepository;
    @Mock private SettlementMapper mapper;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();

    private SettlementGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SettlementGenerator(settlementRepository, tenantRepository, orderRepository,
                bookingRepository, paymentRepository, adjustmentRepository, new SettlementCalculator(), mapper);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT_ID).status(Tenant.TenantStatus.ACTIVE).name("t").commissionRate(0.05).build()));
        when(adjustmentRepository.findByTenantIdAndStatus(any(), any())).thenReturn(List.of());
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> {
            SettlementStatement s = inv.getArgument(0);
            s.setId(STATEMENT_ID);
            return s;
        });
    }

    @Test
    @DisplayName("UT-SETTLE-GEN-001: periodEnd 早於 periodStart → E_9008，不查租戶也不產生結算單")
    void periodEndBeforeStart_throwsE9008() {
        assertThatThrownBy(() -> generator.generateStatementManually(
                TENANT_ID, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 4)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.E_9008);
        verify(tenantRepository, never()).findById(any());
        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-SETTLE-GEN-002: 正常參數 → 委派 generateStatementForTenant 並回傳 mapper 轉換後的 DTO")
    void validParams_delegatesAndReturnsMappedResponse() {
        Order order = Order.builder().id(UUID.randomUUID()).status(Order.OrderStatus.COMPLETED)
                .totalAmount(new BigDecimal("100.00")).build();
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any())).thenReturn(List.of());
        when(orderRepository.findUnsettledByTenantIdAndStatusInAndCreatedAtBefore(any(), any(), any()))
                .thenReturn(List.of(order));
        when(orderRepository.markSettled(any(), any())).thenReturn(1);
        SettlementStatementResponse expectedResponse =
                SettlementStatementResponse.builder().id(STATEMENT_ID).build();
        when(mapper.toStatementResponse(any(SettlementStatement.class))).thenReturn(expectedResponse);

        SettlementStatementResponse response = generator.generateStatementManually(
                TENANT_ID, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10));

        assertThat(response).isSameAs(expectedResponse);
        verify(orderRepository).markSettled(any(), org.mockito.ArgumentMatchers.eq(STATEMENT_ID));
    }

    @Test
    @DisplayName("UT-SETTLE-GEN-003: 同租戶＋期間已有結算單 → 直接回傳既有的（冪等），不重新認領訂單")
    void periodAlreadySettled_returnsExistingWithoutReclaiming() {
        SettlementStatement existing = SettlementStatement.builder().id(STATEMENT_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build()).build();
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any()))
                .thenReturn(List.of(existing));
        SettlementStatementResponse expectedResponse =
                SettlementStatementResponse.builder().id(STATEMENT_ID).build();
        when(mapper.toStatementResponse(existing)).thenReturn(expectedResponse);

        SettlementStatementResponse response = generator.generateStatementManually(
                TENANT_ID, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10));

        assertThat(response).isSameAs(expectedResponse);
        verify(orderRepository, never()).markSettled(any(), any());
        verify(orderRepository, never()).findUnsettledByTenantIdAndStatusInAndCreatedAtBefore(any(), any(), any());
    }
}
