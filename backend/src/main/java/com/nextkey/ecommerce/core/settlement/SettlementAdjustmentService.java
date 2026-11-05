package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨結算週期退款處理（PRD §6.2.1，Sprint 86；Sprint 247 DEF-353 擴及訂房）
 *
 * <p>由 {@code PaymentStateService.refundOrderPayment}／{@code refundBookingPaymentAsSystem}／
 * {@code refundBookingPaymentManually} 退款成功後呼叫。結算單以「訂單／訂房被哪張結算單結算」
 * （{@code orders.settled_statement_id}／{@code bookings.settled_statement_id}）定位；依該結算單目前狀態決定處理方式：
 * <ul>
 *   <li>{@code PENDING}/{@code PENDING_REVIEW}（尚未核准撥款）：直接對該結算單做 delta 更新（Sprint 322 DEF-446：
 *       UPDATE 帶狀態條件；讀狀態與 UPDATE 之間若被批准／駁回搶先推進，改依<strong>重新讀到的目前狀態</strong>照本表分流）</li>
 *   <li>{@code APPROVED}/{@code PAID}/{@code FAILED}（已核准、已撥款，或撥款失敗待重試——款項將撥/已撥）：
 *       產生 {@code adjustment_statements}，於下一結算週期由 {@link SettlementGenerator} 一併折入</li>
 *   <li>{@code REJECTED}/{@code REVERSAL_PENDING}/{@code REVERSED}，或訂單/訂房尚未被任何結算單結算：不做事
 *       （被駁回結算單的訂單/訂房已釋放、之後重新結算時由 {@code Payment.refundedAmount} 帶入退款）</li>
 * </ul>
 *
 * <p>Sprint 327（DEF-452）：兩個入口加入呼叫端（退款）的交易，與退款<strong>同一個交易原子提交</strong>，呼叫端不吞例外。
 * 這裡任何例外（資料庫錯誤、資料異常）都會讓整個退款回滾：例外一穿出本類別的交易代理，共用的交易就被標成 rollback-only，
 * 呼叫端即使 catch 住，提交時仍會丟 {@code UnexpectedRollbackException}（實測見
 * {@code RefundSettlementAdjustmentFailureRealStackIntegrationTest}）。回滾後重試是安全的，見 {@code PaymentStateService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementAdjustmentService {

    private final SettlementStatementRepository settlementStatementRepository;
    private final SettlementAdjustmentRepository settlementAdjustmentRepository;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public void handleOrderRefund(final UUID tenantId, final UUID orderId, final BigDecimal refundAmount) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // DEF-273：以「這筆訂單被哪張結算單結算」定位（orders.settled_statement_id），而不是用下單日期猜測——
        // 週間下單、下週才送達的訂單由下一期結算，下單日期落入的那一期根本沒含這筆，用日期會扣錯結算單，
        // 且對尚未結算的訂單也會誤扣。未結算的訂單不動任何結算單：退款會在它被結算時由 Payment.refundedAmount 帶入。
        Optional<SettlementStatement> statementOpt = orderRepository.findSettledStatementId(orderId)
                .flatMap(settlementStatementRepository::findById)
                .filter(s -> tenantId.equals(s.getTenantId()));

        if (statementOpt.isEmpty()) {
            log.debug("Order not settled yet, refund will be captured when it is settled: "
                    + "tenantId={}, orderId={}", tenantId, orderId);
            return;
        }

        SettlementStatement statement = statementOpt.get();
        switch (statement.getStatus()) {
            case PENDING, PENDING_REVIEW -> applyDirectDeduction(statement, refundAmount,
                    status -> createAdjustmentStatement(tenantId, orderId, statement, status, refundAmount));
            // FAILED 不是終態：retryFailedTransfer 會把它改回 APPROVED 並以結算單當下的淨額撥款。
            // 退款若被忽略，重試撥款時賣家會拿到已退款訂單的全額——與 APPROVED 同屬「款項將撥/已撥」，
            // 比照產生調整單，由下一期結算單折入扣除。
            case APPROVED, PAID, FAILED ->
                    createAdjustmentStatement(tenantId, orderId, statement, statement.getStatus(), refundAmount);
            default -> log.debug("Settlement statement in terminal status {} needs no refund adjustment: "
                    + "statementId={}, orderId={}", statement.getStatus(), statement.getId(), orderId);
        }
    }

    /**
     * 以單一原子敘述套用退款扣除（Sprint 105，DEF-053）。
     *
     * <p>刻意**不呼叫任何 setter**：{@code statement} 仍在本交易的持久化上下文中，
     * 只要碰了 setter，Hibernate 的髒檢查就會在交易提交時把「記憶體中的舊值 + 本次修改」
     * 整列寫回，覆蓋掉原生 UPDATE 的結果——競態原封不動，等於白修。
     *
     * <p>Sprint 322（DEF-446）：UPDATE 帶狀態條件（{@code PENDING}／{@code PENDING_REVIEW}），影響 0 列有兩種原因——結算單不存在，
     * 或呼叫端「讀狀態」之後、UPDATE 生效之前，批准／駁回搶先推進了狀態。後者不能把退款丟掉（錢已經退給買家了），
     * 也不能一律建調整單（結算單若已駁回，訂單已釋放、重新結算時由 {@code Payment.refundedAmount} 帶入退款，再建調整單會重複扣）：
     * 所以<strong>重新讀取目前狀態</strong>（{@code findStatusById}，純量查詢，不受持久化上下文快取影響），
     * 依與主分流相同的表處理；調整單怎麼建（訂單版或訂房版）由呼叫端以 {@code createAdjustment} 傳入。
     *
     * <p>這條路徑<strong>絕不丟例外</strong>：{@code handleOrderRefund}／{@code handleBookingRefund} 加入呼叫端（退款）的交易，
     * 例外穿出會把共用交易標成 rollback-only，已經成功退給 Stripe 的款項在本地的付款狀態就會連帶回滾。
     */
    private void applyDirectDeduction(final SettlementStatement statement, final BigDecimal refundAmount,
            final Consumer<SettlementStatus> createAdjustment) {
        int affected = settlementStatementRepository.applyRefundDeduction(statement.getId(), refundAmount);
        if (affected > 0) {
            log.info("Applied direct refund deduction to PENDING settlement statement: statementId={}, "
                    + "refundAmount={}", statement.getId(), refundAmount);
            return;
        }

        Optional<SettlementStatus> current = settlementStatementRepository.findStatusById(statement.getId());
        if (current.isEmpty()) {
            log.warn("Refund deduction affected no settlement statement (concurrently deleted?): "
                    + "statementId={}, refundAmount={}", statement.getId(), refundAmount);
            return;
        }
        switch (current.get()) {
            case APPROVED, PAID, FAILED -> {
                log.info("Settlement statement moved to {} before the refund deduction took effect, "
                        + "falling back to an adjustment: statementId={}, refundAmount={}",
                        current.get(), statement.getId(), refundAmount);
                createAdjustment.accept(current.get());
            }
            // 狀態機沒有「退回」直接扣款範圍的轉換，所以不應該發生；真的發生時不猜、不丟例外，留下可查的錯誤紀錄
            case PENDING, PENDING_REVIEW -> log.error("Refund deduction affected no row although the settlement "
                    + "statement is still {} (unexpected, refund NOT applied): statementId={}, refundAmount={}",
                    current.get(), statement.getId(), refundAmount);
            default -> log.debug("Settlement statement moved to terminal status {} before the refund deduction "
                    + "took effect, needs no refund adjustment: statementId={}, refundAmount={}",
                    current.get(), statement.getId(), refundAmount);
        }
    }

    private void createAdjustmentStatement(final UUID tenantId, final UUID orderId,
            final SettlementStatement statement, final SettlementStatus status, final BigDecimal refundAmount) {
        SettlementAdjustment adjustment = SettlementAdjustment.builder()
                .tenantId(tenantId)
                .orderId(orderId)
                .originalStatementId(statement.getId())
                .adjustmentType("REFUND_DEDUCTION")
                .amount(refundAmount.negate())
                .status(SettlementAdjustment.AdjustmentStatus.PENDING)
                .build();
        settlementAdjustmentRepository.save(adjustment);
        log.info("Created adjustment statement for refund on {} settlement statement: statementId={}, orderId={}, "
                + "refundAmount={}", status, statement.getId(), orderId, refundAmount);
    }

    /**
     * 訂房版跨結算週期退款處理（Sprint 247，DEF-353）。由 {@code PaymentStateService.refundBookingPaymentAsSystem}
     * （Q14／no-show 排程退款）與 {@code refundBookingPaymentManually}（DEF-354 管理員人工退款）呼叫，
     * 邏輯與 {@link #handleOrderRefund} 對稱——見該方法與類別註解的狀態分流理由。
     */
    @Transactional
    public void handleBookingRefund(final UUID tenantId, final UUID bookingId, final BigDecimal refundAmount) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Optional<SettlementStatement> statementOpt = bookingRepository.findSettledStatementId(bookingId)
                .flatMap(settlementStatementRepository::findById)
                .filter(s -> tenantId.equals(s.getTenantId()));

        if (statementOpt.isEmpty()) {
            log.debug("Booking not settled yet, refund will be captured when it is settled: "
                    + "tenantId={}, bookingId={}", tenantId, bookingId);
            return;
        }

        SettlementStatement statement = statementOpt.get();
        switch (statement.getStatus()) {
            case PENDING, PENDING_REVIEW -> applyDirectDeduction(statement, refundAmount,
                    status -> createBookingAdjustmentStatement(tenantId, bookingId, statement, status, refundAmount));
            case APPROVED, PAID, FAILED -> createBookingAdjustmentStatement(tenantId, bookingId, statement,
                    statement.getStatus(), refundAmount);
            default -> log.debug("Settlement statement in terminal status {} needs no refund adjustment: "
                    + "statementId={}, bookingId={}", statement.getStatus(), statement.getId(), bookingId);
        }
    }

    private void createBookingAdjustmentStatement(final UUID tenantId, final UUID bookingId,
            final SettlementStatement statement, final SettlementStatus status, final BigDecimal refundAmount) {
        SettlementAdjustment adjustment = SettlementAdjustment.builder()
                .tenantId(tenantId)
                .bookingId(bookingId)
                .originalStatementId(statement.getId())
                .adjustmentType("REFUND_DEDUCTION")
                .amount(refundAmount.negate())
                .status(SettlementAdjustment.AdjustmentStatus.PENDING)
                .build();
        settlementAdjustmentRepository.save(adjustment);
        log.info("Created adjustment statement for refund on {} settlement statement: statementId={}, bookingId={}, "
                + "refundAmount={}", status, statement.getId(), bookingId, refundAmount);
    }
}
