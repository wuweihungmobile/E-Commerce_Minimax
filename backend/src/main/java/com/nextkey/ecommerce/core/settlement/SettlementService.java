package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 結算服務 Facade
 *
 * 拆分歷程（Sprint 16 Retro TI-002）：
 * - 原 SettlementService 331 行，業務邏輯過於集中
 * - 拆分為三個專責子服務：
 *   - {@link SettlementCalculator}：純金額計算邏輯（無狀態）
 *   - {@link SettlementGenerator}：結算單生成與查詢（含 @Scheduled）
 *   - {@link SettlementReviewer}：結算單狀態機審核流程
 *   - {@link SettlementMapper}：Entity ↔ DTO 轉換
 *
 * 設計：SettlementService 保留為 Facade，向後相容既有呼叫端
 * - 既有測試 SettlementServiceTest 維持通過
 * - 內部委派給三個子服務
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementCalculator calculator;
    private final SettlementGenerator generator;
    private final SettlementReviewer reviewer;
    private final SettlementMapper mapper;

    // ========== 委派方法（向後相容） ==========

    /** @deprecated 委派給 {@link SettlementGenerator#generateWeeklyStatements()} */
    @Deprecated
    public void generateWeeklyStatements() {
        generator.generateWeeklyStatements();
    }

    /** @deprecated 委派給 {@link SettlementGenerator#generateStatementForTenant(UUID, LocalDate, LocalDate)} */
    @Deprecated
    public SettlementStatement generateStatementForTenant(UUID tenantId, LocalDate periodStart, LocalDate periodEnd) {
        return generator.generateStatementForTenant(tenantId, periodStart, periodEnd);
    }

    /** @deprecated 委派給 {@link SettlementGenerator#getStatementsByTenant(int, int)} */
    @Deprecated
    public SettlementStatementListResponse getStatementsByTenant(int page, int size) {
        return generator.getStatementsByTenant(page, size);
    }

    /** @deprecated 委派給 {@link SettlementGenerator#getStatementById(UUID)} */
    @Deprecated
    public SettlementStatementResponse getStatementById(UUID statementId) {
        return generator.getStatementById(statementId);
    }

    /** @deprecated 委派給 {@link SettlementReviewer#getPendingReviewStatements(int, int)} */
    @Deprecated
    public SettlementStatementListResponse getPendingReviewStatements(int page, int size) {
        return reviewer.getPendingReviewStatements(page, size);
    }

    /** @deprecated 委派給 {@link SettlementReviewer#submitForReview(UUID)} */
    @Deprecated
    public SettlementStatementResponse submitForReview(UUID statementId) {
        return reviewer.submitForReview(statementId);
    }

    /** @deprecated 委派給 {@link SettlementReviewer#approveStatement(UUID, UUID)} */
    @Deprecated
    public SettlementStatementResponse approveStatement(UUID statementId, UUID adminId) {
        return reviewer.approveStatement(statementId, adminId);
    }

    /** @deprecated 委派給 {@link SettlementReviewer#rejectStatement(UUID, UUID, String)} */
    @Deprecated
    public SettlementStatementResponse rejectStatement(UUID statementId, UUID adminId, String reason) {
        return reviewer.rejectStatement(statementId, adminId, reason);
    }

    // ========== 對外公開的計算方法（純函數） ==========

    /** 對外公開：取得結算計算器（純函數，無副作用） */
    public SettlementCalculator getCalculator() {
        return calculator;
    }

    /** 對外公開：取得結算單生成器 */
    public SettlementGenerator getGenerator() {
        return generator;
    }

    /** 對外公開：取得結算單審核器 */
    public SettlementReviewer getReviewer() {
        return reviewer;
    }

    // ========== DTO Classes ==========

    @Data
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

    @Data
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
