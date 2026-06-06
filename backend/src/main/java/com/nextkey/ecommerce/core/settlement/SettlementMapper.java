package com.nextkey.ecommerce.core.settlement;

import org.springframework.stereotype.Component;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;

/**
 * 結算單 Entity ↔ DTO 映射器
 *
 * 職責：負責 SettlementStatement 與 SettlementStatementResponse 之間的轉換
 *
 * 拆分原因：
 * - 原本 toStatementResponse 是 SettlementService 的 private 方法
 * - 拆分 SettlementService 後，Generator/Reviewer 都需要此轉換邏輯
 * - 抽出獨立 Mapper 提升可重用性與可測試性
 */
@Component
public class SettlementMapper {

    /**
     * 將 SettlementStatement Entity 轉換為 SettlementStatementResponse DTO
     */
    public SettlementStatementResponse toStatementResponse(SettlementStatement statement) {
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
}
