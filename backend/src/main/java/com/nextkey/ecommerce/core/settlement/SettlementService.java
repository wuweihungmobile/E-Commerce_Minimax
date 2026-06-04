package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 結算服務
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementStatementRepository settlementRepository;
    private final TenantRepository tenantRepository;
    private final OrderRepository orderRepository;

    // 平台抽成比例 (Phase 1 預設 10%)
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.10");

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
     * 為指定租戶生成結算單
     */
    @Transactional
    public SettlementStatement generateStatementForTenant(UUID tenantId, LocalDate periodStart, LocalDate periodEnd) {
        // 檢查是否已存在該期間的結算單
        List<SettlementStatement> existingStatements = settlementRepository.findByTenantIdAndPeriodStartBetween(
                tenantId, periodStart, periodEnd);

        if (!existingStatements.isEmpty()) {
            log.warn("Settlement statement already exists for tenant {} period {} to {}", tenantId, periodStart, periodEnd);
            return existingStatements.get(0);
        }

        // 取得期間內的已完成訂單
        List<Order> completedOrders = orderRepository.findByTenantIdAndCreatedAtBetween(
                tenantId,
                periodStart.atStartOfDay(),
                periodEnd.atTime(23, 59, 59));

        completedOrders = completedOrders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.COMPLETED ||
                        o.getStatus() == Order.OrderStatus.DELIVERED)
                .collect(Collectors.toList());

        // 計算結算金額 (商家結算金額 = Σ(order.total_amount × (1 - commission_rate)) - 退款金額)
        BigDecimal totalGmv = completedOrders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.REFUNDED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 計算抽成金額 (僅針對已完成訂單)
        BigDecimal commissionAmount = totalGmv.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);

        // 計算退款金額
        BigDecimal totalRefunds = completedOrders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.REFUNDED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 結算金額 = (GMV * (1 - commission_rate)) - 退款
        BigDecimal netAmount = totalGmv.subtract(commissionAmount).subtract(totalRefunds);

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
                .status(SettlementStatement.SettlementStatus.PENDING)
                .build();

        statement = settlementRepository.save(statement);

        log.info("Generated settlement statement: id={}, tenant={}, amount={}",
                statement.getId(), tenantId, netAmount);

        return statement;
    }

    /**
     * 取得商家結算單列表
     */
    @Transactional(readOnly = true)
    public SettlementStatementListResponse getStatementsByTenant(int page, int size) {
        UUID tenantId = TenantContext.getCurrentTenant();

        Page<SettlementStatement> statements = settlementRepository.findByTenantIdOrderByPeriodStartDesc(
                tenantId, PageRequest.of(page, size));

        return SettlementStatementListResponse.builder()
                .statements(statements.getContent().stream()
                        .map(this::toStatementResponse)
                        .collect(Collectors.toList()))
                .page(page)
                .size(size)
                .totalElements(statements.getTotalElements())
                .totalPages(statements.getTotalPages())
                .build();
    }

    /**
     * 取得結算單詳情
     */
    @Transactional(readOnly = true)
    public SettlementStatementResponse getStatementById(UUID statementId) {
        UUID tenantId = TenantContext.getCurrentTenant();

        SettlementStatement statement = settlementRepository.findByIdAndTenantId(statementId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5005, "Settlement statement not found"));

        return toStatementResponse(statement);
    }

    /**
     * Admin: 取得所有待審核結算單
     */
    @Transactional(readOnly = true)
    public SettlementStatementListResponse getPendingReviewStatements(int page, int size) {
        Page<SettlementStatement> statements = settlementRepository.findByStatusOrderByGeneratedAtDesc(
                SettlementStatement.SettlementStatus.PENDING_REVIEW,
                PageRequest.of(page, size));

        return SettlementStatementListResponse.builder()
                .statements(statements.getContent().stream()
                        .map(this::toStatementResponse)
                        .collect(Collectors.toList()))
                .page(page)
                .size(size)
                .totalElements(statements.getTotalElements())
                .totalPages(statements.getTotalPages())
                .build();
    }

    /**
     * Admin: 提交結算單審核
     */
    @Transactional
    public SettlementStatementResponse submitForReview(UUID statementId) {
        UUID tenantId = TenantContext.getCurrentTenant();

        SettlementStatement statement = settlementRepository.findByIdAndTenantId(statementId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5005, "Settlement statement not found"));

        if (statement.getStatus() != SettlementStatement.SettlementStatus.PENDING) {
            throw new BusinessException(ErrorCode.E_5006, "Only PENDING statements can be submitted for review");
        }

        statement.setStatus(SettlementStatement.SettlementStatus.PENDING_REVIEW);
        statement = settlementRepository.save(statement);

        log.info("Settlement statement submitted for review: id={}", statementId);

        return toStatementResponse(statement);
    }

    /**
     * Admin: 批准結算單
     */
    @Transactional
    public SettlementStatementResponse approveStatement(UUID statementId, UUID adminId) {
        SettlementStatement statement = settlementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5005, "Settlement statement not found"));

        if (statement.getStatus() != SettlementStatement.SettlementStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.E_5006, "Only PENDING_REVIEW statements can be approved");
        }

        statement.setStatus(SettlementStatement.SettlementStatus.APPROVED);
        statement.setReviewedAt(java.time.Instant.now());
        statement = settlementRepository.save(statement);

        log.info("Settlement statement approved: id={}, by={}", statementId, adminId);

        return toStatementResponse(statement);
    }

    /**
     * Admin: 駁回結算單
     */
    @Transactional
    public SettlementStatementResponse rejectStatement(UUID statementId, UUID adminId, String reason) {
        SettlementStatement statement = settlementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5005, "Settlement statement not found"));

        if (statement.getStatus() != SettlementStatement.SettlementStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.E_5006, "Only PENDING_REVIEW statements can be rejected");
        }

        statement.setStatus(SettlementStatement.SettlementStatus.REJECTED);
        statement.setReviewedAt(java.time.Instant.now());
        statement.setRejectionReason(reason);
        statement = settlementRepository.save(statement);

        log.info("Settlement statement rejected: id={}, by={}, reason={}", statementId, adminId, reason);

        return toStatementResponse(statement);
    }

    // ========== Helper Methods ==========

    private String generateStatementNumber(UUID tenantId, LocalDate periodStart) {
        String dateStr = periodStart.toString().replace("-", "");
        return "STL-" + tenantId.toString().substring(0, 8) + "-" + dateStr;
    }

    private SettlementStatementResponse toStatementResponse(SettlementStatement statement) {
        return SettlementStatementResponse.builder()
                .id(statement.getId())
                .statementNumber(statement.getStatementNumber())
                .periodStart(statement.getPeriodStart())
                .periodEnd(statement.getPeriodEnd())
                .totalOrders(statement.getTotalOrders())
                .totalGmv(statement.getTotalGmv())
                .totalRefunds(statement.getTotalRefunds())
                .commissionAmount(statement.getCommissionAmount())
                .netSettlementAmount(statement.getNetSettlementAmount())
                .currency(statement.getCurrency())
                .status(statement.getStatus().name())
                .generatedAt(statement.getGeneratedAt())
                .reviewedAt(statement.getReviewedAt())
                .rejectionReason(statement.getRejectionReason())
                .approvedAt(statement.getApprovedAt())
                .paidAt(statement.getPaidAt())
                .notes(statement.getNotes())
                .build();
    }

    // ========== DTO Classes ==========

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SettlementStatementResponse {
        private UUID id;
        private String statementNumber;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private Integer totalOrders;
        private BigDecimal totalGmv;
        private BigDecimal totalRefunds;
        private BigDecimal commissionAmount;
        private BigDecimal netSettlementAmount;
        private String currency;
        private String status;
        private java.time.Instant generatedAt;
        private java.time.Instant reviewedAt;
        private String rejectionReason;
        private java.time.Instant approvedAt;
        private java.time.Instant paidAt;
        private String notes;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SettlementStatementListResponse {
        private List<SettlementStatementResponse> statements;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}