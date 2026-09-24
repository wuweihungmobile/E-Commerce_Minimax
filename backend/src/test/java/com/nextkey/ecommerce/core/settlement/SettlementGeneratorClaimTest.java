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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;

/**
 * DEF-273：結算單認領訂單的併發防護。
 *
 * <p>兩個節點同時跑排程（或排程與人工重跑）時，都可能在對方認領之前讀到同一批「尚未結算」的訂單。
 * 認領是 {@code UPDATE ... WHERE settled_statement_id IS NULL}：輸的一方認領筆數會少於讀到的筆數，
 * 此時必須回滾整張結算單——否則同一筆訂單會被兩張結算單各結算一次（重複撥款）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SettlementGenerator: 認領訂單的併發防護（DEF-273）")
class SettlementGeneratorClaimTest {

    @Mock private SettlementStatementRepository settlementRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SettlementAdjustmentRepository adjustmentRepository;
    @Mock private SettlementMapper mapper;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();
    private final Order orderA = order();
    private final Order orderB = order();

    private SettlementGenerator generator;

    private static Order order() {
        return Order.builder().id(UUID.randomUUID()).status(Order.OrderStatus.COMPLETED)
                .totalAmount(new BigDecimal("100.00")).build();
    }

    @BeforeEach
    void setUp() {
        generator = new SettlementGenerator(settlementRepository, tenantRepository, orderRepository,
                paymentRepository, adjustmentRepository, new SettlementCalculator(), mapper);
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any())).thenReturn(List.of());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT_ID).status(Tenant.TenantStatus.ACTIVE).name("t").commissionRate(0.05).build()));
        when(orderRepository.findUnsettledByTenantIdAndStatusInAndCreatedAtBefore(any(), any(), any()))
                .thenReturn(List.of(orderA, orderB));
        when(adjustmentRepository.findByTenantIdAndStatus(any(), any())).thenReturn(List.of());
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> {
            SettlementStatement s = inv.getArgument(0);
            s.setId(STATEMENT_ID);
            return s;
        });
    }

    @Test
    @DisplayName("認領筆數少於讀到的筆數（被另一個結算搶先）→ 拋例外回滾整張結算單，不留下重複結算")
    void claimShortfall_throwsToRollbackWholeStatement() {
        when(orderRepository.markSettled(any(), any())).thenReturn(1); // 讀到 2 筆，只認領到 1 筆

        assertThatThrownBy(() -> generator.generateStatementForTenant(
                TENANT_ID, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claimed 1 of 2 orders");
    }

    @Test
    @DisplayName("認領成功：以「讀到的全部訂單 id」與「本結算單 id」原子標記，且結算單彙總這批訂單")
    void claimSuccess_marksExactlyTheSettledOrdersWithThisStatement() {
        when(orderRepository.markSettled(any(), any())).thenReturn(2);

        SettlementStatement statement = generator.generateStatementForTenant(
                TENANT_ID, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> ids = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).markSettled(ids.capture(), org.mockito.ArgumentMatchers.eq(STATEMENT_ID));
        assertThat(ids.getValue()).containsExactlyInAnyOrder(orderA.getId(), orderB.getId());
        assertThat(statement.getTotalOrders()).isEqualTo(2);
        assertThat(statement.getTotalGmv()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("沒有可結算的訂單 → 不呼叫認領（不對空清單下 UPDATE ... IN ()）")
    void noOrders_doesNotClaim() {
        when(orderRepository.findUnsettledByTenantIdAndStatusInAndCreatedAtBefore(any(), any(), any()))
                .thenReturn(List.of());

        SettlementStatement statement = generator.generateStatementForTenant(
                TENANT_ID, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10));

        assertThat(statement.getTotalOrders()).isZero();
        verify(orderRepository, never()).markSettled(any(), any());
    }
}
