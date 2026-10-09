package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nextkey.ecommerce.domain.model.order.Booking;
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

    /**
     * 可結算的訂房狀態（Sprint 247，DEF-353）：{@code COMPLETED}（退房完成），或 {@code CANCELLED}
     * 且 {@code refundStatus = NONE}。與 {@code BookingRepository.findUnsettledEligibleByTenantIdAndCreatedAtBefore}
     * 同一份規則的防線，但這裡**不**重複查詢層「存在成功付款」的條件（那是跨表 EXISTS，不屬於純函數職責）——
     * 本方法只過濾掉查詢條件本不該放進來的狀態組合，不保證「從未收款」的 CANCELLED/NONE 訂房已被排除。
     */
    public List<Booking> filterSettleableBookings(List<Booking> bookings) {
        if (bookings == null) {
            return List.of();
        }
        return bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED
                        || (b.getStatus() == Booking.BookingStatus.CANCELLED
                                && b.getRefundStatus() == Booking.RefundStatus.NONE))
                .toList();
    }

    /**
     * 計算訂房總 GMV（Sprint 247，DEF-353）。方法名不與 {@link #calculateTotalGmv(List)} 共用——
     * {@code List<Order>}／{@code List<Booking>} 經泛型型別抹除後簽章相同，Java 不允許以此多載。
     *
     * <p>與訂單版不同之處：訂房沒有對應 {@code Order.OrderStatus.REFUNDED} 的終態可過濾——退款與否記在
     * {@link Booking#getRefundStatus()}，已被 {@link #filterSettleableBookings}（或查詢層）排除退款中/
     * 已退款的訂房，故此處不需再過濾一次。
     */
    public BigDecimal calculateTotalBookingGmv(List<Booking> settleableBookings) {
        if (settleableBookings == null || settleableBookings.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
        return settleableBookings.stream()
                .map(Booking::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * 計算訂房退款總額（Sprint 247，DEF-353）。方法名理由同 {@link #calculateTotalBookingGmv}（泛型型別抹除）。
     * 語意同 {@link #calculateTotalRefunds(List, Map)}：可結算訂房的 {@code refundStatus} 恆為 {@code NONE}，
     * 此欄位目前應恆為 0——保留與訂單對稱的計算路徑，供日後訂房出現部分退款情境（例如完成後的售後）時不需更動呼叫端。
     */
    public BigDecimal calculateTotalBookingRefunds(List<Booking> settleableBookings, Map<UUID, BigDecimal> refundedAmountByBookingId) {
        if (settleableBookings == null || settleableBookings.isEmpty() || refundedAmountByBookingId == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
        return settleableBookings.stream()
                .map(b -> refundedAmountByBookingId.getOrDefault(b.getId(), BigDecimal.ZERO))
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, ROUNDING_MODE);
    }
}
