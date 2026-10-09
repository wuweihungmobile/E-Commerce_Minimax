package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.shared.time.BusinessTime;
import com.nextkey.ecommerce.shared.util.PageableUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 結算單生成服務
 *
 * 職責：結算單的生成、查詢業務邏輯
 *
 * 拆分原因：
 * - 將生成/查詢邏輯與狀態機邏輯（Reviewer）分離
 * - 計算邏輯由 SettlementCalculator 提供
 * - Entity→DTO 轉換由 SettlementMapper 處理
 *
 * 設計：
 * - generateWeeklyStatements() 為 @Scheduled Job（週一 00:00 執行）
 * - generateStatementForTenant() 為單一租戶生成（含冪等性檢查）
 * - getStatementsByTenant/getStatementById 為查詢方法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementGenerator {

    private final SettlementStatementRepository settlementRepository;
    private final TenantRepository tenantRepository;
    private final OrderRepository orderRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementAdjustmentRepository adjustmentRepository;
    private final SettlementCalculator calculator;
    private final SettlementMapper mapper;

    /**
     * 每週一凌晨自動生成結算單
     * 結算上一週 (週一 00:00 至 週日 23:59) 的已完成訂單
     */
    // DEF-271（使用者 2026-09-24 拍板）：結算週以營運時區（UTC+8）切分。未指定 zone 時 Spring 以 JVM
    // 預設時區解讀 cron，正式容器（UTC）會變成「台灣週一 08:00」才觸發，且與開發機（台灣）切出不同的週期。
    @Scheduled(cron = "0 0 0 ? * MON", zone = BusinessTime.ZONE_ID)
    @Transactional
    public void generateWeeklyStatements() {
        log.info("Starting weekly settlement statement generation");

        LocalDate today = BusinessTime.today();
        // 上週一
        LocalDate lastWeekMonday = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        // 上週日
        LocalDate lastWeekSunday = lastWeekMonday.plusDays(6);

        List<Tenant> activeTenants = tenantRepository.findByStatus(Tenant.TenantStatus.ACTIVE);

        int generatedCount = 0;
        for (Tenant tenant : activeTenants) {
            try {
                generateStatementForTenant(tenant.getId(), lastWeekMonday, lastWeekSunday);
                generatedCount++;
            } catch (RuntimeException e) {
                // Batch 容錯：單一 tenant 失敗不中斷整個排程，確保其他 tenant 仍能完成結算
                log.error("Failed to generate settlement statement for tenant: {}", tenant.getId(), e);
            }
        }

        log.info("Weekly settlement statement generation completed. Generated {} statements", generatedCount);
    }

    /**
     * 為指定租戶生成結算單（含冪等性檢查）
     */
    @Transactional
    public SettlementStatement generateStatementForTenant(UUID tenantId, LocalDate periodStart, LocalDate periodEnd) {
        // 檢查是否已存在該期間的結算單
        List<SettlementStatement> existingStatements = settlementRepository.findByTenantIdAndPeriodStartBetween(
                tenantId, periodStart, periodEnd);

        if (!existingStatements.isEmpty()) {
            log.warn("Settlement statement already exists for tenant {} period {} to {}",
                    tenantId, periodStart, periodEnd);
            return existingStatements.get(0);
        }

        // DEF-273（使用者 2026-09-24 拍板）：納入「所有已完成且尚未被任何結算單認領」的訂單，不論哪週下單。
        // 舊寫法只撈「下單時間落在本期」的訂單且要求結算當下已完成——週間下單、下週才送達的訂單，
        // 之後任何一期都撈不到，永遠不會被結算。上界只排除「本期結束之後才建立」的訂單（DEF-270：半開區間，
        // 不用 atTime(23, 59, 59)，否則漏掉最後一秒）；時刻以營運時區換算（DEF-271／DEF-272）。
        List<Order> unsettledOrders = orderRepository.findUnsettledByTenantIdAndStatusInAndCreatedAtBefore(
                tenantId,
                SettlementCalculator.SETTLEABLE_STATUSES,
                BusinessTime.startOfDay(periodEnd.plusDays(1)));

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        // 過濾出可結算訂單（查詢已限狀態，此處是與 calculator 同一份規則的防線）
        List<Order> completedOrders = calculator.filterSettleableOrders(unsettledOrders);

        // Sprint 247（DEF-353，PRD §6.2.1）：訂房與訂單同一張結算單、同週期、同抽成一併結算。
        // 可結算條件（COMPLETED，或 CANCELLED+refundStatus=NONE+存在成功付款）已在查詢層判斷，
        // 此處的 filterSettleableBookings 只是與 calculator 同一份規則的防線（理由同訂單）。
        List<Booking> unsettledBookings = bookingRepository.findUnsettledEligibleByTenantIdAndCreatedAtBefore(
                tenantId,
                BusinessTime.startOfDay(periodEnd.plusDays(1)),
                Booking.BookingStatus.COMPLETED,
                Booking.BookingStatus.CANCELLED,
                Booking.RefundStatus.NONE,
                Payment.PaymentStatus.SUCCESS,
                Payment.PaymentStatus.PARTIALLY_REFUNDED);
        List<Booking> completedBookings = calculator.filterSettleableBookings(unsettledBookings);

        // 計算結算金額（Sprint 80 AI-2416：抽成比例改用租戶自訂 commissionRate，取代先前硬編碼 10%）
        BigDecimal orderGmv = calculator.calculateTotalGmv(completedOrders);
        BigDecimal bookingGmv = calculator.calculateTotalBookingGmv(completedBookings);
        BigDecimal totalGmv = orderGmv.add(bookingGmv);
        BigDecimal commissionRate = BigDecimal.valueOf(tenant.getCommissionRate());
        BigDecimal commissionAmount = calculator.calculateCommission(totalGmv, commissionRate);
        // Sprint 86：真正扣除已結算訂單的部分退款金額（PRD §6.2.1）
        Map<UUID, BigDecimal> refundedAmountByOrderId = buildRefundedAmountMap(completedOrders);
        BigDecimal orderRefunds = calculator.calculateTotalRefunds(completedOrders, refundedAmountByOrderId);
        // Sprint 247：訂房版退款（目前恆為 0，見 SettlementCalculator#calculateTotalBookingRefunds 的說明）
        Map<UUID, BigDecimal> refundedAmountByBookingId = buildRefundedAmountMapForBookings(completedBookings);
        BigDecimal bookingRefunds = calculator.calculateTotalBookingRefunds(completedBookings, refundedAmountByBookingId);
        BigDecimal totalRefunds = orderRefunds.add(bookingRefunds);
        BigDecimal netAmount = calculator.calculateNetSettlementAmount(totalGmv, commissionAmount, totalRefunds);

        // Sprint 86：折入前期已產生、尚未套用的跨週期退款調整單（PRD §6.2.1）
        List<SettlementAdjustment> pendingAdjustments = adjustmentRepository.findByTenantIdAndStatus(
                tenantId, SettlementAdjustment.AdjustmentStatus.PENDING);
        BigDecimal adjustmentAmount = pendingAdjustments.stream()
                .map(SettlementAdjustment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        netAmount = netAmount.add(adjustmentAmount);

        // 生成結算單號
        String statementNumber = generateStatementNumber(tenantId, periodStart);

        SettlementStatement statement = SettlementStatement.builder()
                .tenant(tenant)
                .statementNumber(statementNumber)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalOrders(completedOrders.size())
                .totalBookings(completedBookings.size())
                .totalGmv(totalGmv)
                .totalRefunds(totalRefunds)
                .commissionAmount(commissionAmount)
                .netSettlementAmount(netAmount)
                .adjustmentAmount(adjustmentAmount)
                .currency("TWD")
                .status(SettlementStatus.PENDING)
                .build();

        statement = settlementRepository.save(statement);

        // DEF-273：原子認領這批訂單（UPDATE ... WHERE settled_statement_id IS NULL）。認領筆數不足代表有訂單在
        // 讀取與認領之間被另一個結算搶先認領（例如兩個節點同時跑排程）——必須回滾整張結算單，否則同一筆訂單
        // 會被兩張結算單各結算一次。IllegalStateException 由 generateWeeklyStatements 逐租戶攔截並記錄，下次重試。
        if (!completedOrders.isEmpty()) {
            List<UUID> orderIds = completedOrders.stream().map(Order::getId).toList();
            int claimed = orderRepository.markSettled(orderIds, statement.getId());
            if (claimed != orderIds.size()) {
                throw new IllegalStateException("Settlement claimed " + claimed + " of " + orderIds.size()
                        + " orders for tenant " + tenantId + " (concurrent settlement?), rolling back statement "
                        + statement.getStatementNumber());
            }
        }

        // Sprint 247（DEF-353）：訂房的原子認領，與訂單同一套「認領數不足就拋例外回滾整張結算單」防護
        // ——兩個認領在同一交易內，任一邊回滾都會讓另一邊已標記的 settled_statement_id 一併復原。
        if (!completedBookings.isEmpty()) {
            List<UUID> bookingIds = completedBookings.stream().map(Booking::getId).toList();
            int claimedBookings = bookingRepository.markSettled(bookingIds, statement.getId());
            if (claimedBookings != bookingIds.size()) {
                throw new IllegalStateException("Settlement claimed " + claimedBookings + " of " + bookingIds.size()
                        + " bookings for tenant " + tenantId + " (concurrent settlement?), rolling back statement "
                        + statement.getStatementNumber());
            }
        }

        if (!pendingAdjustments.isEmpty()) {
            Instant now = Instant.now();
            for (SettlementAdjustment adjustment : pendingAdjustments) {
                adjustment.setStatus(SettlementAdjustment.AdjustmentStatus.APPLIED);
                adjustment.setAppliedStatementId(statement.getId());
                adjustment.setAppliedAt(now);
            }
            adjustmentRepository.saveAll(pendingAdjustments);
        }

        log.info("Generated settlement statement: id={}, tenant={}, amount={}, adjustmentAmount={}",
                statement.getId(), tenantId, netAmount, adjustmentAmount);

        return statement;
    }

    /**
     * 依訂單 ID 查詢對應 {@code Payment.refundedAmount}，建立退款金額對照表（Sprint 86）
     */
    private Map<UUID, BigDecimal> buildRefundedAmountMap(List<Order> orders) {
        Map<UUID, BigDecimal> refundedAmountByOrderId = new HashMap<>();
        for (Order order : orders) {
            paymentRepository.findEffectiveByOrderId(order.getId())
                    .map(Payment::getRefundedAmount)
                    .filter(java.util.Objects::nonNull)
                    .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                    .ifPresent(amount -> refundedAmountByOrderId.put(order.getId(), amount));
        }
        return refundedAmountByOrderId;
    }

    /**
     * 依訂房 ID 查詢對應 {@code Payment.refundedAmount}，建立退款金額對照表（Sprint 247，DEF-353；
     * 比照 {@link #buildRefundedAmountMap}）。可結算訂房目前恆為 {@code refundStatus=NONE}（無退款），
     * 故此 map 現況應恆為空；保留與訂單對稱的路徑，理由見 {@code SettlementCalculator#calculateTotalBookingRefunds}。
     */
    private Map<UUID, BigDecimal> buildRefundedAmountMapForBookings(List<Booking> bookings) {
        Map<UUID, BigDecimal> refundedAmountByBookingId = new HashMap<>();
        for (Booking booking : bookings) {
            paymentRepository.findEffectiveByBookingId(booking.getId())
                    .map(Payment::getRefundedAmount)
                    .filter(java.util.Objects::nonNull)
                    .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                    .ifPresent(amount -> refundedAmountByBookingId.put(booking.getId(), amount));
        }
        return refundedAmountByBookingId;
    }

    /**
     * 手動觸發/補產結算單（DEF-287，Sprint 208）：{@code settlement:generate} 專用（僅 SUPER_ADMIN）。
     *
     * <p>用途：(1) {@link #generateWeeklyStatements} 當週某租戶失敗或漏產時的補產；(2) 上線前以
     * Stripe 測試模式走查撥款時，不必等到下週一排程才有結算單可測。
     *
     * <p>刻意只加驗證與轉換，不重複實作防護：{@link #generateStatementForTenant} 既有的「同租戶＋
     * 期間已有結算單就直接回傳既有的」冪等檢查與「原子認領訂單」併發防護，對排程與本方法一視同仁，
     * 手動觸發不會讓同一筆訂單被兩張結算單重複結算。
     */
    @Transactional
    public SettlementStatementResponse generateStatementManually(
            UUID tenantId, LocalDate periodStart, LocalDate periodEnd) {
        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessException(ErrorCode.E_9008, "periodEnd must not be before periodStart");
        }
        SettlementStatement statement = generateStatementForTenant(tenantId, periodStart, periodEnd);
        return mapper.toStatementResponse(statement);
    }

    /**
     * 取得商家結算單列表（分頁）
     */
    @Transactional(readOnly = true)
    public SettlementStatementListResponse getStatementsByTenant(int page, int size) {
        UUID tenantId = TenantContext.getCurrentTenant();
        // Sprint 234：沒有店鋪的呼叫者（系統租戶）回空；BUYER 持有此端點要求的 order:read，
        // 而每週結算會替每個 ACTIVE 租戶（含系統租戶）產生結算單，原本任何買家都讀得到平台的 GMV、抽成與退款
        if (!TenantContext.isStoreTenant(tenantId)) {
            return SettlementStatementListResponse.builder()
                    .statements(java.util.Collections.emptyList())
                    .page(page)
                    .size(size)
                    .totalElements(0L)
                    .totalPages(0)
                    .build();
        }

        Page<SettlementStatement> statements = settlementRepository.findByTenantIdOrderByPeriodStartDesc(
                tenantId, PageableUtils.of(page, size, 100));

        return SettlementStatementListResponse.builder()
                .statements(statements.getContent().stream()
                        .map(mapper::toStatementResponse)
                        .collect(Collectors.toList()))
                .page(page)
                .size(size)
                .totalElements(statements.getTotalElements())
                .totalPages(statements.getTotalPages())
                .build();
    }

    /**
     * 取得結算單詳情（含租戶隔離）
     */
    @Transactional(readOnly = true)
    public SettlementStatementResponse getStatementById(UUID statementId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (!TenantContext.isStoreTenant(tenantId)) {
            throw new BusinessException(ErrorCode.E_5013, "Settlement statement not found");
        }

        SettlementStatement statement = settlementRepository.findByIdAndTenantId(statementId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5013, "Settlement statement not found"));

        return mapper.toStatementResponse(statement);
    }

    // ========== Helper Methods ==========

    /**
     * 生成結算單號
     * 格式：STL-{tenantId 前 8 碼}-{periodStart yyyyMMdd}
     */
    private String generateStatementNumber(UUID tenantId, LocalDate periodStart) {
        String dateStr = periodStart.toString().replace("-", "");
        return "STL-" + tenantId.toString().substring(0, 8) + "-" + dateStr;
    }
}
