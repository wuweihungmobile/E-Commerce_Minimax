package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.time.BusinessTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨結算週期退款處理（PRD §6.2.1，Sprint 86）
 *
 * <p>由 {@code PaymentStateService.refundOrderPayment} 退款成功後呼叫，依訂單所屬結算單
 * 目前狀態決定處理方式：
 * <ul>
 *   <li>{@code PENDING}/{@code PENDING_REVIEW}（尚未核准撥款）：直接對該結算單做 delta 更新</li>
 *   <li>{@code APPROVED}/{@code PAID}（已核准或已撥款，不可逆）：產生 {@code adjustment_statements}，
 *       於下一結算週期由 {@link SettlementGenerator} 一併折入</li>
 *   <li>{@code REJECTED}/{@code FAILED}（終態，金流未發生）或找不到對應結算單：不做事</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementAdjustmentService {

    private final SettlementStatementRepository settlementStatementRepository;
    private final SettlementAdjustmentRepository settlementAdjustmentRepository;

    @Transactional
    public void handleOrderRefund(final UUID tenantId, final UUID orderId, final java.time.Instant orderCreatedAt,
            final BigDecimal refundAmount) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // DEF-271：結算週以營運時區（UTC+8）切分，訂單歸屬的日期必須用同一個時區換算，
        // 否則正式容器（UTC）會把台灣週一 00:00～08:00 的訂單歸到上一週，與 SettlementGenerator 的期間對不上
        LocalDate orderDate = orderCreatedAt.atZone(BusinessTime.ZONE).toLocalDate();
        Optional<SettlementStatement> statementOpt = settlementStatementRepository
                .findByTenantIdAndPeriodCovering(tenantId, orderDate);

        if (statementOpt.isEmpty()) {
            log.debug("No settlement statement covers order yet, refund will be captured on next generation: "
                    + "tenantId={}, orderId={}", tenantId, orderId);
            return;
        }

        SettlementStatement statement = statementOpt.get();
        switch (statement.getStatus()) {
            case PENDING, PENDING_REVIEW -> applyDirectDeduction(statement, refundAmount);
            case APPROVED, PAID -> createAdjustmentStatement(tenantId, orderId, statement, refundAmount);
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
