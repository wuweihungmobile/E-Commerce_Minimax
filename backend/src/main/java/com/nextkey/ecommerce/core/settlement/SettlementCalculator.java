package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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

    /** 平台抽成比例 (Phase 1 預設 10%) */
    public static final BigDecimal COMMISSION_RATE = new BigDecimal("0.10");

    /** BigDecimal 小數位精度 */
    private static final int SCALE = 2;

    /** 四捨五入模式 */
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

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
                .filter(o -> o.getStatus() == Order.OrderStatus.COMPLETED ||
                        o.getStatus() == Order.OrderStatus.DELIVERED)
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
     * @param gmv 總 GMV
     * @return 抽成金額 = GMV × 10%（四捨五入至 2 位小數）
     */
    public BigDecimal calculateCommission(BigDecimal gmv) {
        if (gmv == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
        return gmv.multiply(COMMISSION_RATE).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 計算退款總額
     *
     * 註：考量到目前業務邏輯，REFUNDED 訂單會被 filterSettleableOrders
     * 過濾掉，因此本方法在當前流程中永遠返回 0。
     * 保留此方法以供未來流程調整（例如：先計算退款再過濾）。
     *
     * @param settleableOrders 已過濾的可結算訂單
     * @return 退款總額
     */
    public BigDecimal calculateTotalRefunds(List<Order> settleableOrders) {
        if (settleableOrders == null || settleableOrders.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
        return settleableOrders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.REFUNDED)
                .map(Order::getTotalAmount)
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
