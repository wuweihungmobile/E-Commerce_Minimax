package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

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
            } catch (Exception e) {
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

        // 過濾出可結算訂單
        List<Order> completedOrders = calculator.filterSettleableOrders(allOrders);

        // 計算結算金額
        BigDecimal totalGmv = calculator.calculateTotalGmv(completedOrders);
        BigDecimal commissionAmount = calculator.calculateCommission(totalGmv);
        BigDecimal totalRefunds = calculator.calculateTotalRefunds(completedOrders);
        BigDecimal netAmount = calculator.calculateNetSettlementAmount(totalGmv, commissionAmount, totalRefunds);

        // 生成結算單號
        String statementNumber = generateStatementNumber(tenantId, periodStart);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5001, "Tenant not found"));

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
                .currency("TWD")
                .status(SettlementStatus.PENDING)
                .build();

        statement = settlementRepository.save(statement);

        log.info("Generated settlement statement: id={}, tenant={}, amount={}",
                statement.getId(), tenantId, netAmount);

        return statement;
    }

    /**
     * 取得商家結算單列表（分頁）
     */
    @Transactional(readOnly = true)
    public SettlementStatementListResponse getStatementsByTenant(int page, int size) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Page<SettlementStatement> statements = settlementRepository.findByTenantIdOrderByPeriodStartDesc(
                tenantId, PageRequest.of(page, size));

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
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5005, "Settlement statement not found"));

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
