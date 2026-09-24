package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;

/**
 * SettlementAdjustmentService 單元測試（Sprint 86，PRD §6.2.1 跨結算週期退款處理機制）。
 *
 * <p>Sprint 195（DEF-273）：結算單改由「訂單被哪張結算單結算」（{@code orders.settled_statement_id}）定位，
 * 不再用下單日期猜測；真實資料庫的定位正確性由 {@code SettlementExactlyOnceIntegrationTest} 守住。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementAdjustmentService 單元測試（Sprint 86／195）")
class SettlementAdjustmentServiceTest {

    @Mock
    private SettlementStatementRepository settlementStatementRepository;

    @Mock
    private SettlementAdjustmentRepository settlementAdjustmentRepository;

    @Mock
    private OrderRepository orderRepository;

    private SettlementAdjustmentService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();

    private SettlementAdjustmentService newService() {
        return new SettlementAdjustmentService(settlementStatementRepository, settlementAdjustmentRepository,
                orderRepository);
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

    /** 這筆訂單已被 {@link #STATEMENT_ID} 這張結算單結算。 */
    private void orderSettledBy(final SettlementStatement statement) {
        when(orderRepository.findSettledStatementId(ORDER_ID)).thenReturn(Optional.of(STATEMENT_ID));
        when(settlementStatementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
    }

    @Test
    @DisplayName("handleOrderRefund：訂單尚未被任何結算單結算時不做事（退款會在它被結算時由 Payment.refundedAmount 帶入）")
    void handleOrderRefund_orderNotSettledYet_doesNothing() {
        service = newService();
        when(orderRepository.findSettledStatementId(ORDER_ID)).thenReturn(Optional.empty());

        service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("100"));

        verify(settlementStatementRepository, never()).applyRefundDeduction(any(), any());
        verify(settlementStatementRepository, never()).save(any());
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleOrderRefund：結算單屬於其他租戶時不做事（防禦性租戶隔離）")
    void handleOrderRefund_statementOfOtherTenant_doesNothing() {
        service = newService();
        SettlementStatement otherTenantStatement = statementOf(SettlementStatus.PENDING);
        otherTenantStatement.setTenantId(UUID.randomUUID());
        orderSettledBy(otherTenantStatement);

        service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("100"));

        verify(settlementStatementRepository, never()).applyRefundDeduction(any(), any());
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleOrderRefund：refundAmount 為 0 或 null 不做事，也不查詢結算單")
    void handleOrderRefund_zeroOrNullAmount_doesNothing() {
        service = newService();

        service.handleOrderRefund(TENANT_ID, ORDER_ID, BigDecimal.ZERO);
        service.handleOrderRefund(TENANT_ID, ORDER_ID, null);

        verify(orderRepository, never()).findSettledStatementId(any());
    }

    @Test
    @DisplayName("handleOrderRefund：PENDING 結算單直接 delta 扣除，不產生調整單")
    void handleOrderRefund_pendingStatement_appliesDirectDeduction() {
        service = newService();
        SettlementStatement statement = statementOf(SettlementStatus.PENDING);
        orderSettledBy(statement);
        when(settlementStatementRepository.applyRefundDeduction(eq(statement.getId()), any(BigDecimal.class)))
                .thenReturn(1);

        service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("100.00"));

        // Sprint 105（DEF-053）：斷言的是「以正確的 delta 呼叫了原子敘述」，而非
        // 修改前的「記憶體物件上的數字對不對」。後者在缺陷存在時**照樣全綠**——
        // 併發正確性無法用 mock 掉 repository 的單執行緒測試證明，
        // 那由 M07SettlementRefundConcurrencyIntegrationTest 壓真實 DB 負責。
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(settlementStatementRepository).applyRefundDeduction(eq(statement.getId()), amountCaptor.capture());
        assertThat(amountCaptor.getValue()).isEqualByComparingTo("100.00");
        verify(settlementStatementRepository, never()).save(any(SettlementStatement.class));
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleOrderRefund：PENDING_REVIEW 結算單同樣直接 delta 扣除")
    void handleOrderRefund_pendingReviewStatement_appliesDirectDeduction() {
        service = newService();
        SettlementStatement statement = statementOf(SettlementStatus.PENDING_REVIEW);
        orderSettledBy(statement);
        when(settlementStatementRepository.applyRefundDeduction(eq(statement.getId()), any(BigDecimal.class)))
                .thenReturn(1);

        service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("50.00"));

        verify(settlementStatementRepository).applyRefundDeduction(eq(statement.getId()), any(BigDecimal.class));
        verify(settlementStatementRepository, never()).save(any(SettlementStatement.class));
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleOrderRefund：APPROVED 結算單產生 adjustment_statement，不動原結算單數字")
    void handleOrderRefund_approvedStatement_createsAdjustmentStatement() {
        service = newService();
        SettlementStatement statement = statementOf(SettlementStatus.APPROVED);
        orderSettledBy(statement);

        service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("200.00"));

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
        orderSettledBy(statementOf(SettlementStatus.PAID));

        service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("300.00"));

        verify(settlementAdjustmentRepository).save(any(SettlementAdjustment.class));
        verify(settlementStatementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sprint 195: FAILED 結算單（可用 retryFailedTransfer 重試撥款）→ 產生 adjustment_statement，不可被當終態忽略")
    void handleOrderRefund_failedStatement_createsAdjustmentStatement() {
        // FAILED 不是終態：管理端重試撥款會把它改回 APPROVED 並以結算單當下的淨額撥款。退款若被忽略，
        // 重試撥款時賣家會拿到已退款訂單的全額（少收錢）。與 APPROVED 同屬「款項將撥/已撥」，比照產生調整單，
        // 由下一期結算單折入扣除。
        service = newService();
        orderSettledBy(statementOf(SettlementStatus.FAILED));

        service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("10.00"));

        ArgumentCaptor<SettlementAdjustment> captor = ArgumentCaptor.forClass(SettlementAdjustment.class);
        verify(settlementAdjustmentRepository).save(captor.capture());
        assertThat(captor.getValue().getOriginalStatementId()).isEqualTo(STATEMENT_ID);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-10.00");
    }

    @Test
    @DisplayName("handleOrderRefund：REJECTED／REVERSAL_PENDING／REVERSED 結算單不做事")
    void handleOrderRefund_otherTerminalStatuses_doNothing() {
        service = newService();
        for (SettlementStatus status : new SettlementStatus[] {
                SettlementStatus.REJECTED, SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED}) {
            orderSettledBy(statementOf(status));

            service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("10.00"));
        }

        verify(settlementStatementRepository, never()).save(any());
        verify(settlementAdjustmentRepository, never()).save(any());
    }
}
