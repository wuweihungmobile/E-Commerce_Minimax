package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.nextkey.ecommerce.domain.repository.BookingRepository;
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

    @Mock
    private BookingRepository bookingRepository;

    private SettlementAdjustmentService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();

    private SettlementAdjustmentService newService() {
        return new SettlementAdjustmentService(settlementStatementRepository, settlementAdjustmentRepository,
                orderRepository, bookingRepository);
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

    /** 這筆訂房已被 {@link #STATEMENT_ID} 這張結算單結算（Sprint 247，DEF-353）。 */
    private void bookingSettledBy(final SettlementStatement statement) {
        when(bookingRepository.findSettledStatementId(BOOKING_ID)).thenReturn(Optional.of(STATEMENT_ID));
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

    // ========== 直接扣款落空時的後備路徑（Sprint 322，DEF-446） ==========
    //
    // 直接扣款的 UPDATE 帶狀態條件（PENDING／PENDING_REVIEW）。讀狀態與 UPDATE 之間若被批准／駁回搶先推進，UPDATE 回 0 列，
    // 退款不能被丟掉（錢已退給買家）、也不能一律建調整單（已駁回的結算單，重新結算時由 Payment.refundedAmount 帶入退款，
    // 再建調整單會重複扣）——所以重讀目前狀態，依同一張狀態表處理。真實競態由 SettlementReviewRaceRealStackIntegrationTest 守住，
    // 這裡守的是 mock 看得到的分支：每種「重讀到的狀態」各自的處置，以及「成功扣款後不可再碰調整單」。

    /** 退款讀到的是 PENDING_REVIEW（要直接扣款），但 UPDATE 落空：{@code repoStatusNow} 是資料庫「現在」的狀態。 */
    private void directDeductionMissedBecauseStatementIsNow(final SettlementStatus repoStatusNow) {
        orderSettledBy(statementOf(SettlementStatus.PENDING_REVIEW));
        when(settlementStatementRepository.applyRefundDeduction(eq(STATEMENT_ID), any(BigDecimal.class))).thenReturn(0);
        when(settlementStatementRepository.findStatusById(STATEMENT_ID)).thenReturn(Optional.of(repoStatusNow));
    }

    @Test
    @DisplayName("DEF-446 handleOrderRefund：直接扣款落空、重讀到 APPROVED／PAID／FAILED → 各產生一筆訂單調整單（不可把退款丟掉）")
    void handleOrderRefund_directDeductionMissed_statementNowApprovedPaidOrFailed_createsOrderAdjustment() {
        service = newService();
        for (SettlementStatus now : new SettlementStatus[] {
                SettlementStatus.APPROVED, SettlementStatus.PAID, SettlementStatus.FAILED}) {
            directDeductionMissedBecauseStatementIsNow(now);

            service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("100.00"));
        }

        ArgumentCaptor<SettlementAdjustment> captor = ArgumentCaptor.forClass(SettlementAdjustment.class);
        verify(settlementAdjustmentRepository, times(3)).save(captor.capture());
        for (SettlementAdjustment adjustment : captor.getAllValues()) {
            assertThat(adjustment.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(adjustment.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(adjustment.getBookingId()).isNull();
            assertThat(adjustment.getOriginalStatementId()).isEqualTo(STATEMENT_ID);
            assertThat(adjustment.getAdjustmentType()).isEqualTo("REFUND_DEDUCTION");
            assertThat(adjustment.getAmount()).isEqualByComparingTo("-100.00");
            assertThat(adjustment.getStatus()).isEqualTo(SettlementAdjustment.AdjustmentStatus.PENDING);
        }
        verify(settlementStatementRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEF-446 handleOrderRefund：直接扣款落空、重讀到 REJECTED／REVERSAL_PENDING／REVERSED → 不做事（不可建調整單，否則重新結算時重複扣）")
    void handleOrderRefund_directDeductionMissed_statementNowVoided_doesNothing() {
        service = newService();
        for (SettlementStatus now : new SettlementStatus[] {
                SettlementStatus.REJECTED, SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED}) {
            directDeductionMissedBecauseStatementIsNow(now);

            service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("100.00"));
        }

        verify(settlementAdjustmentRepository, never()).save(any());
        verify(settlementStatementRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEF-446 handleOrderRefund：直接扣款落空、結算單已不存在 → 不做事、不丟例外（退款路徑不可因此回滾）")
    void handleOrderRefund_directDeductionMissed_statementGone_doesNothingAndDoesNotThrow() {
        service = newService();
        orderSettledBy(statementOf(SettlementStatus.PENDING));
        when(settlementStatementRepository.applyRefundDeduction(eq(STATEMENT_ID), any(BigDecimal.class))).thenReturn(0);
        when(settlementStatementRepository.findStatusById(STATEMENT_ID)).thenReturn(Optional.empty());

        service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("100.00"));

        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEF-446 handleOrderRefund：直接扣款落空但重讀仍是 PENDING／PENDING_REVIEW（不應發生）→ 不猜、不建調整單、不丟例外")
    void handleOrderRefund_directDeductionMissed_statementStillDirectlyDeductible_doesNotGuessOrThrow() {
        service = newService();
        for (SettlementStatus now : new SettlementStatus[] {SettlementStatus.PENDING, SettlementStatus.PENDING_REVIEW}) {
            directDeductionMissedBecauseStatementIsNow(now);

            service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("100.00"));
        }

        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEF-446 handleOrderRefund：直接扣款成功（影響 1 列）→ 不重讀狀態、不建調整單（否則批准在扣款之後提交會重複計入）")
    void handleOrderRefund_directDeductionApplied_doesNotReReadStatusNorCreateAdjustment() {
        service = newService();
        orderSettledBy(statementOf(SettlementStatus.PENDING_REVIEW));
        when(settlementStatementRepository.applyRefundDeduction(eq(STATEMENT_ID), any(BigDecimal.class))).thenReturn(1);
        // 即使「重讀」會看到已批准（扣款之後批准才提交），成功的扣款也不能再被當成需要調整單
        lenient().when(settlementStatementRepository.findStatusById(STATEMENT_ID))
                .thenReturn(Optional.of(SettlementStatus.APPROVED));

        service.handleOrderRefund(TENANT_ID, ORDER_ID, new BigDecimal("100.00"));

        verify(settlementStatementRepository, never()).findStatusById(any());
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    // ========== handleBookingRefund（Sprint 247，DEF-353；與上方 handleOrderRefund 對稱） ==========

    @Test
    @DisplayName("handleBookingRefund：訂房尚未被任何結算單結算時不做事")
    void handleBookingRefund_bookingNotSettledYet_doesNothing() {
        service = newService();
        when(bookingRepository.findSettledStatementId(BOOKING_ID)).thenReturn(Optional.empty());

        service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("100"));

        verify(settlementStatementRepository, never()).applyRefundDeduction(any(), any());
        verify(settlementStatementRepository, never()).save(any());
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleBookingRefund：結算單屬於其他租戶時不做事（防禦性租戶隔離）")
    void handleBookingRefund_statementOfOtherTenant_doesNothing() {
        service = newService();
        SettlementStatement otherTenantStatement = statementOf(SettlementStatus.PENDING);
        otherTenantStatement.setTenantId(UUID.randomUUID());
        bookingSettledBy(otherTenantStatement);

        service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("100"));

        verify(settlementStatementRepository, never()).applyRefundDeduction(any(), any());
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleBookingRefund：refundAmount 為 0 或 null 不做事，也不查詢結算單")
    void handleBookingRefund_zeroOrNullAmount_doesNothing() {
        service = newService();

        service.handleBookingRefund(TENANT_ID, BOOKING_ID, BigDecimal.ZERO);
        service.handleBookingRefund(TENANT_ID, BOOKING_ID, null);

        verify(bookingRepository, never()).findSettledStatementId(any());
    }

    @Test
    @DisplayName("handleBookingRefund：PENDING 結算單直接 delta 扣除，不產生調整單")
    void handleBookingRefund_pendingStatement_appliesDirectDeduction() {
        service = newService();
        SettlementStatement statement = statementOf(SettlementStatus.PENDING);
        bookingSettledBy(statement);
        when(settlementStatementRepository.applyRefundDeduction(eq(statement.getId()), any(BigDecimal.class)))
                .thenReturn(1);

        service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("100.00"));

        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(settlementStatementRepository).applyRefundDeduction(eq(statement.getId()), amountCaptor.capture());
        assertThat(amountCaptor.getValue()).isEqualByComparingTo("100.00");
        verify(settlementStatementRepository, never()).save(any(SettlementStatement.class));
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleBookingRefund：PENDING_REVIEW 結算單同樣直接 delta 扣除")
    void handleBookingRefund_pendingReviewStatement_appliesDirectDeduction() {
        service = newService();
        SettlementStatement statement = statementOf(SettlementStatus.PENDING_REVIEW);
        bookingSettledBy(statement);
        when(settlementStatementRepository.applyRefundDeduction(eq(statement.getId()), any(BigDecimal.class)))
                .thenReturn(1);

        service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("50.00"));

        verify(settlementStatementRepository).applyRefundDeduction(eq(statement.getId()), any(BigDecimal.class));
        verify(settlementStatementRepository, never()).save(any(SettlementStatement.class));
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleBookingRefund：APPROVED 結算單產生 adjustment_statement（bookingId 而非 orderId），不動原結算單數字")
    void handleBookingRefund_approvedStatement_createsAdjustmentStatement() {
        service = newService();
        SettlementStatement statement = statementOf(SettlementStatus.APPROVED);
        bookingSettledBy(statement);

        service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("200.00"));

        ArgumentCaptor<SettlementAdjustment> captor = ArgumentCaptor.forClass(SettlementAdjustment.class);
        verify(settlementAdjustmentRepository).save(captor.capture());
        SettlementAdjustment adjustment = captor.getValue();
        assertThat(adjustment.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(adjustment.getBookingId()).isEqualTo(BOOKING_ID);
        assertThat(adjustment.getOrderId()).isNull();
        assertThat(adjustment.getOriginalStatementId()).isEqualTo(STATEMENT_ID);
        assertThat(adjustment.getAdjustmentType()).isEqualTo("REFUND_DEDUCTION");
        assertThat(adjustment.getAmount()).isEqualByComparingTo("-200.00");
        assertThat(adjustment.getStatus()).isEqualTo(SettlementAdjustment.AdjustmentStatus.PENDING);
        verify(settlementStatementRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleBookingRefund：PAID 結算單產生 adjustment_statement")
    void handleBookingRefund_paidStatement_createsAdjustmentStatement() {
        service = newService();
        bookingSettledBy(statementOf(SettlementStatus.PAID));

        service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("300.00"));

        verify(settlementAdjustmentRepository).save(any(SettlementAdjustment.class));
        verify(settlementStatementRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleBookingRefund：FAILED 結算單（可重試撥款）→ 產生 adjustment_statement，不可被當終態忽略")
    void handleBookingRefund_failedStatement_createsAdjustmentStatement() {
        service = newService();
        bookingSettledBy(statementOf(SettlementStatus.FAILED));

        service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("10.00"));

        ArgumentCaptor<SettlementAdjustment> captor = ArgumentCaptor.forClass(SettlementAdjustment.class);
        verify(settlementAdjustmentRepository).save(captor.capture());
        assertThat(captor.getValue().getOriginalStatementId()).isEqualTo(STATEMENT_ID);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-10.00");
    }

    @Test
    @DisplayName("handleBookingRefund：REJECTED／REVERSAL_PENDING／REVERSED 結算單不做事")
    void handleBookingRefund_otherTerminalStatuses_doNothing() {
        service = newService();
        for (SettlementStatus status : new SettlementStatus[] {
                SettlementStatus.REJECTED, SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED}) {
            bookingSettledBy(statementOf(status));

            service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("10.00"));
        }

        verify(settlementStatementRepository, never()).save(any());
        verify(settlementAdjustmentRepository, never()).save(any());
    }

    // ========== 訂房版的後備路徑（Sprint 322，DEF-446）：調整單要掛在訂房上，不是訂單 ==========

    /** 訂房退款讀到的是 PENDING_REVIEW（要直接扣款），但 UPDATE 落空：{@code repoStatusNow} 是資料庫「現在」的狀態。 */
    private void bookingDirectDeductionMissedBecauseStatementIsNow(final SettlementStatus repoStatusNow) {
        bookingSettledBy(statementOf(SettlementStatus.PENDING_REVIEW));
        when(settlementStatementRepository.applyRefundDeduction(eq(STATEMENT_ID), any(BigDecimal.class))).thenReturn(0);
        when(settlementStatementRepository.findStatusById(STATEMENT_ID)).thenReturn(Optional.of(repoStatusNow));
    }

    @Test
    @DisplayName("DEF-446 handleBookingRefund：直接扣款落空、重讀到 APPROVED／PAID／FAILED → 各產生一筆訂房調整單（bookingId 有值、orderId 空）")
    void handleBookingRefund_directDeductionMissed_statementNowApprovedPaidOrFailed_createsBookingAdjustment() {
        service = newService();
        for (SettlementStatus now : new SettlementStatus[] {
                SettlementStatus.APPROVED, SettlementStatus.PAID, SettlementStatus.FAILED}) {
            bookingDirectDeductionMissedBecauseStatementIsNow(now);

            service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("100.00"));
        }

        ArgumentCaptor<SettlementAdjustment> captor = ArgumentCaptor.forClass(SettlementAdjustment.class);
        verify(settlementAdjustmentRepository, times(3)).save(captor.capture());
        for (SettlementAdjustment adjustment : captor.getAllValues()) {
            assertThat(adjustment.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(adjustment.getBookingId()).isEqualTo(BOOKING_ID);
            assertThat(adjustment.getOrderId()).isNull();
            assertThat(adjustment.getOriginalStatementId()).isEqualTo(STATEMENT_ID);
            assertThat(adjustment.getAmount()).isEqualByComparingTo("-100.00");
            assertThat(adjustment.getStatus()).isEqualTo(SettlementAdjustment.AdjustmentStatus.PENDING);
        }
    }

    @Test
    @DisplayName("DEF-446 handleBookingRefund：直接扣款落空、重讀到 REJECTED／REVERSAL_PENDING／REVERSED → 不做事")
    void handleBookingRefund_directDeductionMissed_statementNowVoided_doesNothing() {
        service = newService();
        for (SettlementStatus now : new SettlementStatus[] {
                SettlementStatus.REJECTED, SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED}) {
            bookingDirectDeductionMissedBecauseStatementIsNow(now);

            service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("100.00"));
        }

        verify(settlementAdjustmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEF-446 handleBookingRefund：直接扣款成功（影響 1 列）→ 不重讀狀態、不建調整單")
    void handleBookingRefund_directDeductionApplied_doesNotReReadStatusNorCreateAdjustment() {
        service = newService();
        bookingSettledBy(statementOf(SettlementStatus.PENDING));
        when(settlementStatementRepository.applyRefundDeduction(eq(STATEMENT_ID), any(BigDecimal.class))).thenReturn(1);
        lenient().when(settlementStatementRepository.findStatusById(STATEMENT_ID))
                .thenReturn(Optional.of(SettlementStatus.APPROVED));

        service.handleBookingRefund(TENANT_ID, BOOKING_ID, new BigDecimal("100.00"));

        verify(settlementStatementRepository, never()).findStatusById(any());
        verify(settlementAdjustmentRepository, never()).save(any());
    }
}
