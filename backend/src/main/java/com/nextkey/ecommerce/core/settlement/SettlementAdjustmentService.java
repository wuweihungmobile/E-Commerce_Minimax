package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨結算週期退款處理（PRD §6.2.1，Sprint 86）
 *
 * <p>由 {@code PaymentStateService.refundOrderPayment} 退款成功後呼叫。結算單以「訂單被哪張結算單結算」
 * （{@code orders.settled_statement_id}）定位；依該結算單目前狀態決定處理方式：
 * <ul>
 *   <li>{@code PENDING}/{@code PENDING_REVIEW}（尚未核准撥款）：直接對該結算單做 delta 更新</li>
 *   <li>{@code APPROVED}/{@code PAID}/{@code FAILED}（已核准、已撥款，或撥款失敗待重試——款項將撥/已撥）：
 *       產生 {@code adjustment_statements}，於下一結算週期由 {@link SettlementGenerator} 一併折入</li>
 *   <li>{@code REJECTED}/{@code REVERSAL_PENDING}/{@code REVERSED}，或訂單尚未被任何結算單結算：不做事
 *       （被駁回結算單的訂單已釋放、之後重新結算時由 {@code Payment.refundedAmount} 帶入退款）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementAdjustmentService {

    private final SettlementStatementRepository settlementStatementRepository;
    private final SettlementAdjustmentRepository settlementAdjustmentRepository;
    private final OrderRepository orderRepository;

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
            case PENDING, PENDING_REVIEW -> applyDirectDeduction(statement, refundAmount);
            // FAILED 不是終態：retryFailedTransfer 會把它改回 APPROVED 並以結算單當下的淨額撥款。
            // 退款若被忽略，重試撥款時賣家會拿到已退款訂單的全額——與 APPROVED 同屬「款項將撥/已撥」，
            // 比照產生調整單，由下一期結算單折入扣除。
            case APPROVED, PAID, FAILED -> createAdjustmentStatement(tenantId, orderId, statement, refundAmount);
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
     */
    private void applyDirectDeduction(final SettlementStatement statement, final BigDecimal refundAmount) {
        int affected = settlementStatementRepository.applyRefundDeduction(statement.getId(), refundAmount);
        if (affected == 0) {
            log.warn("Refund deduction affected no settlement statement (concurrently deleted?): "
                    + "statementId={}, refundAmount={}", statement.getId(), refundAmount);
            return;
        }
        log.info("Applied direct refund deduction to PENDING settlement statement: statementId={}, refundAmount={}",
                statement.getId(), refundAmount);
    }

    private void createAdjustmentStatement(final UUID tenantId, final UUID orderId,
            final SettlementStatement statement, final BigDecimal refundAmount) {
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
                + "refundAmount={}", statement.getStatus(), statement.getId(), orderId, refundAmount);
    }
}
