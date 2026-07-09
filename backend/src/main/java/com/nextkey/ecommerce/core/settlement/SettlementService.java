package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 結算服務（Sprint 20 US-004 清理後）
 *
 * 職責：持有三個子服務的引用，提供對外 getter 與共用 DTO 類別。
 * 業務邏輯已在 Sprint 16 TI-002 拆分至：
 *   - {@link SettlementCalculator}：純金額計算（無狀態）
 *   - {@link SettlementGenerator}：結算單生成與查詢
 *   - {@link SettlementReviewer}：結算單審核狀態機
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementCalculator calculator;
    private final SettlementGenerator generator;
    private final SettlementReviewer reviewer;

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
        private BigDecimal adjustmentAmount;
        private UUID reversalInitiatedBy;
        private String reversalInitiatedByRole;
        private java.time.Instant reversalRequestedAt;
        private String reversalReason;
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
