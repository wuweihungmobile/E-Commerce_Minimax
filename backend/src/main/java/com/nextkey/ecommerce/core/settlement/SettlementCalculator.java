package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nextkey.ecommerce.domain.model.order.Order;

import lombok.extern.slf4j.Slf4j;

/**
 * 結算金額計算器
 *
 * 職責：純函數式的結算金額計算邏輯，無狀態、無副作用
 *
 * 拆分原因（Sprint 15 Retro TI-002）：
 * - SettlementService 331 行偏大
 * - 計算邏輯與業務邏輯耦合，難以單獨測試
 * - 拆分後可獨立驗證金額計算的正確性
 *
 * 設計原則：
 * - 所有方法為純函數（無 DB、無 Spring 依賴副作用）
 * - 輸入：訂單列表
 * - 輸出：BigDecimal 金額或過濾後的訂單列表
 */
@Slf4j
@Component
public class SettlementCalculator {

    /** BigDecimal 小數位精度 */
    private static final int SCALE = 2;

    /** 四捨五入模式 */
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * 可結算的訂單狀態（COMPLETED 或 DELIVERED）。單一來源：{@link #filterSettleableOrders} 與
     * 結算查詢（{@code OrderRepository.findUnsettledByTenantIdAndStatusInAndCreatedAtBefore}）共用，
     * 兩處不會對「什麼算可結算」有不同答案。
     */
    public static final List<Order.OrderStatus> SETTLEABLE_STATUSES =
            List.of(Order.OrderStatus.COMPLETED, Order.OrderStatus.DELIVERED);

    /**
     * 過濾出「可結算」訂單（COMPLETED 或 DELIVERED 狀態）
     *
     * @param orders 原始訂單列表
     * @return 可結算訂單列表（不含 REFUNDED 等）
     */
    public List<Order> filterSettleableOrders(List<Order> orders) {
        if (orders == null) {
            return List.of();
        }
        return orders.stream()
                .filter(o -> SETTLEABLE_STATUSES.contains(o.getStatus()))
                .toList();
    }

    /**
     * 計算總 GMV（不含 REFUNDED 訂單）
     *
     * @param settleableOrders 已過濾的可結算訂單
     * @return GMV 總和（2 位小數）
     */
    public BigDecimal calculateTotalGmv(List<Order> settleableOrders) {
        if (settleableOrders == null || settleableOrders.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
        return settleableOrders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.REFUNDED)
                .map(Order::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 計算平台抽成金額
     *
     * Sprint 80（AI-2416）：改用租戶自訂 {@code Tenant.commissionRate}，取代先前硬編碼 10%，
     * 使報表抽成口徑與 Phase D-2 實際 transfer 分潤金額一致（同一來源）。
     *
     * @param gmv 總 GMV
     * @param commissionRate 該租戶的抽成比例（{@code Tenant.commissionRate}，如 0.05 代表 5%）
     * @return 抽成金額 = GMV × commissionRate（四捨五入至 2 位小數）
     */
    public BigDecimal calculateCommission(BigDecimal gmv, BigDecimal commissionRate) {
        if (gmv == null || commissionRate == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
        return gmv.multiply(commissionRate).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 計算退款總額（Sprint 86 修正，PRD §6.2.1）
     *
     * <p>修正前：對已過濾為 COMPLETED/DELIVERED 的訂單再篩選 status==REFUNDED，
     * 但 {@link #filterSettleableOrders} 已排除 REFUNDED 訂單，故舊版邏輯恆為 0——
     * 全額退款訂單本已被 GMV 排除（不需額外扣除），但**部分退款**訂單的
     * {@code Order.status} 不變（仍是 COMPLETED/DELIVERED），其已退款金額從未被扣除。
     *
     * @param settleableOrders 已過濾的可結算訂單
     * @param refundedAmountByOrderId 訂單 ID → 該訂單已退款金額（{@code Payment.refundedAmount}）
     * @return 退款總額
     */
    public BigDecimal calculateTotalRefunds(List<Order> settleableOrders, Map<UUID, BigDecimal> refundedAmountByOrderId) {
        if (settleableOrders == null || settleableOrders.isEmpty() || refundedAmountByOrderId == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
        return settleableOrders.stream()
                .map(o -> refundedAmountByOrderId.getOrDefault(o.getId(), BigDecimal.ZERO))
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 計算商家結算金額
     *
     * 公式：結算金額 = GMV - 抽成 - 退款
     *
     * @param gmv 總 GMV
     * @param commission 抽成金額
     * @param refunds 退款金額
     * @return 商家實際結算金額（2 位小數）
     */
    public BigDecimal calculateNetSettlementAmount(BigDecimal gmv, BigDecimal commission, BigDecimal refunds) {
        BigDecimal safeGmv = gmv != null ? gmv : BigDecimal.ZERO;
        BigDecimal safeCommission = commission != null ? commission : BigDecimal.ZERO;
        BigDecimal safeRefunds = refunds != null ? refunds : BigDecimal.ZERO;
        return safeGmv.subtract(safeCommission).subtract(safeRefunds).setScale(SCALE, ROUNDING_MODE);
    }
}
