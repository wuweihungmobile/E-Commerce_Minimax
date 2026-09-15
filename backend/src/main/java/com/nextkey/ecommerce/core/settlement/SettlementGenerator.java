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
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.model.settlement.SettlementAdjustment;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
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
    private final PaymentRepository paymentRepository;
    private final SettlementAdjustmentRepository adjustmentRepository;
    private final SettlementCalculator calculator;
    private final SettlementMapper mapper;

    /**
     * 每週一凌晨自動生成結算單
     * 結算上一週 (週一 00:00 至 週日 23:59) 的已完成訂單
     */
    @Scheduled(cron = "0 0 0 ? * MON")
    @Transactional
    public void generateWeeklyStatements() {
        log.info("Starting weekly settlement statement generation");

        LocalDate today = LocalDate.now();
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

        // 取得期間內的所有訂單
        List<Order> allOrders = orderRepository.findByTenantIdAndCreatedAtBetween(
                tenantId,
                periodStart.atStartOfDay(),
                periodEnd.atTime(23, 59, 59));

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        // 過濾出可結算訂單
        List<Order> completedOrders = calculator.filterSettleableOrders(allOrders);

        // 計算結算金額（Sprint 80 AI-2416：抽成比例改用租戶自訂 commissionRate，取代先前硬編碼 10%）
        BigDecimal totalGmv = calculator.calculateTotalGmv(completedOrders);
        BigDecimal commissionRate = BigDecimal.valueOf(tenant.getCommissionRate());
        BigDecimal commissionAmount = calculator.calculateCommission(totalGmv, commissionRate);
        // Sprint 86：真正扣除已結算訂單的部分退款金額（PRD §6.2.1）
        Map<UUID, BigDecimal> refundedAmountByOrderId = buildRefundedAmountMap(completedOrders);
        BigDecimal totalRefunds = calculator.calculateTotalRefunds(completedOrders, refundedAmountByOrderId);
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
                .totalGmv(totalGmv)
                .totalRefunds(totalRefunds)
                .commissionAmount(commissionAmount)
                .netSettlementAmount(netAmount)
                .adjustmentAmount(adjustmentAmount)
                .currency("TWD")
                .status(SettlementStatus.PENDING)
                .build();

        statement = settlementRepository.save(statement);

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
            paymentRepository.findByOrderId(order.getId())
                    .map(Payment::getRefundedAmount)
                    .filter(java.util.Objects::nonNull)
                    .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                    .ifPresent(amount -> refundedAmountByOrderId.put(order.getId(), amount));
        }
        return refundedAmountByOrderId;
    }

    /**
     * 取得商家結算單列表（分頁）
     */
    @Transactional(readOnly = true)
    public SettlementStatementListResponse getStatementsByTenant(int page, int size) {
        UUID tenantId = TenantContext.getCurrentTenant();

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
