package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;

/**
 * SettlementAdjustmentService 單元測試（Sprint 86，PRD §6.2.1 跨結算週期退款處理機制）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementAdjustmentService 單元測試（Sprint 86）")
class SettlementAdjustmentServiceTest {

    @Mock
    private SettlementStatementRepository settlementStatementRepository;

    @Mock
    private SettlementAdjustmentRepository settlementAdjustmentRepository;

    private SettlementAdjustmentService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();
    private static final Instant ORDER_CREATED_AT = Instant.parse("2026-06-15T10:00:00Z");

    private SettlementAdjustmentService newService() {
        return new SettlementAdjustmentService(settlementStatementRepository, settlementAdjustmentRepository);
    }

    private SettlementStatement statementOf(SettlementStatus status) {
        return SettlementStatement.builder()
                .id(STATEMENT_ID)
                .tenantId(TENANT_ID)
                .status(status)
                .periodStart(LocalDate.of(2026, 6, 8))
                .periodEnd(LocalDate.of(2026, 6, 14))
                .totalRefunds(BigDecimal.ZERO)
                .netSettlementAmount(new BigDecimal("1000.00"))
                .build();
    }

    @Test
    @DisplayName("handleOrderRefund：找不到涵蓋期間的結算單時不做事")
    void handleOrderRefund_noStatementFound_doesNothing() {
        service = newService();
        when(settlementStatementRepository.findByTenantIdAndPeriodCovering(eq(TENANT_ID), any()))
                .thenReturn(Optional.empty());

        service.handleOrderRefund(TENANT_ID, ORDER_ID, ORDER_CREATED_AT, new BigDecimal("100"));

        verify(settlementStatementRepository, never()).save(any());
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleOrderRefund：refundAmount 為 0 或 null 不做事")
    void handleOrderRefund_zeroOrNullAmount_doesNothing() {
        service = newService();

        service.handleOrderRefund(TENANT_ID, ORDER_ID, ORDER_CREATED_AT, BigDecimal.ZERO);
        service.handleOrderRefund(TENANT_ID, ORDER_ID, ORDER_CREATED_AT, null);

        verify(settlementStatementRepository, never()).findByTenantIdAndPeriodCovering(any(), any());
    }

    @Test
    @DisplayName("handleOrderRefund：PENDING 結算單直接 delta 扣除，不產生調整單")
    void handleOrderRefund_pendingStatement_appliesDirectDeduction() {
        service = newService();
        SettlementStatement statement = statementOf(SettlementStatus.PENDING);
        when(settlementStatementRepository.findByTenantIdAndPeriodCovering(eq(TENANT_ID), any()))
                .thenReturn(Optional.of(statement));
        when(settlementStatementRepository.save(any(SettlementStatement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.handleOrderRefund(TENANT_ID, ORDER_ID, ORDER_CREATED_AT, new BigDecimal("100.00"));

        ArgumentCaptor<SettlementStatement> captor = ArgumentCaptor.forClass(SettlementStatement.class);
        verify(settlementStatementRepository).save(captor.capture());
        assertThat(captor.getValue().getTotalRefunds()).isEqualByComparingTo("100.00");
        assertThat(captor.getValue().getNetSettlementAmount()).isEqualByComparingTo("900.00");
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleOrderRefund：PENDING_REVIEW 結算單同樣直接 delta 扣除")
    void handleOrderRefund_pendingReviewStatement_appliesDirectDeduction() {
        service = newService();
        SettlementStatement statement = statementOf(SettlementStatus.PENDING_REVIEW);
        when(settlementStatementRepository.findByTenantIdAndPeriodCovering(eq(TENANT_ID), any()))
                .thenReturn(Optional.of(statement));
        when(settlementStatementRepository.save(any(SettlementStatement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.handleOrderRefund(TENANT_ID, ORDER_ID, ORDER_CREATED_AT, new BigDecimal("50.00"));

        verify(settlementStatementRepository).save(any(SettlementStatement.class));
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleOrderRefund：APPROVED 結算單產生 adjustment_statement，不動原結算單數字")
    void handleOrderRefund_approvedStatement_createsAdjustmentStatement() {
        service = newService();
        SettlementStatement statement = statementOf(SettlementStatus.APPROVED);
        when(settlementStatementRepository.findByTenantIdAndPeriodCovering(eq(TENANT_ID), any()))
                .thenReturn(Optional.of(statement));

        service.handleOrderRefund(TENANT_ID, ORDER_ID, ORDER_CREATED_AT, new BigDecimal("200.00"));

        ArgumentCaptor<SettlementAdjustment> captor = ArgumentCaptor.forClass(SettlementAdjustment.class);
        verify(settlementAdjustmentRepository).save(captor.capture());
        SettlementAdjustment adjustment = captor.getValue();
        assertThat(adjustment.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(adjustment.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(adjustment.getOriginalStatementId()).isEqualTo(STATEMENT_ID);
        assertThat(adjustment.getAdjustmentType()).isEqualTo("REFUND_DEDUCTION");
        assertThat(adjustment.getAmount()).isEqualByComparingTo("-200.00");
        assertThat(adjustment.getStatus()).isEqualTo(SettlementAdjustment.AdjustmentStatus.PENDING);
        verify(settlementStatementRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleOrderRefund：PAID 結算單產生 adjustment_statement")
    void handleOrderRefund_paidStatement_createsAdjustmentStatement() {
        service = newService();
        SettlementStatement statement = statementOf(SettlementStatus.PAID);
        when(settlementStatementRepository.findByTenantIdAndPeriodCovering(eq(TENANT_ID), any()))
                .thenReturn(Optional.of(statement));

        service.handleOrderRefund(TENANT_ID, ORDER_ID, ORDER_CREATED_AT, new BigDecimal("300.00"));

        verify(settlementAdjustmentRepository).save(any(SettlementAdjustment.class));
        verify(settlementStatementRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleOrderRefund：REJECTED/FAILED 終態結算單不做事")
    void handleOrderRefund_terminalStatuses_doNothing() {
        service = newService();
        for (SettlementStatus status : new SettlementStatus[] {SettlementStatus.REJECTED, SettlementStatus.FAILED}) {
            SettlementStatement statement = statementOf(status);
            when(settlementStatementRepository.findByTenantIdAndPeriodCovering(eq(TENANT_ID), any()))
                    .thenReturn(Optional.of(statement));

            service.handleOrderRefund(TENANT_ID, ORDER_ID, ORDER_CREATED_AT, new BigDecimal("10.00"));
        }

        verify(settlementStatementRepository, never()).save(any());
        verify(settlementAdjustmentRepository, never()).save(any());
    }
}
