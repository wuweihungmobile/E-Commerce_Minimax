package com.nextkey.ecommerce.core.settlement;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 結算單審核服務（狀態機）
 *
 * 職責：結算單的審核流程狀態機管理
 *
 * 狀態轉換規則：
 * - PENDING → PENDING_REVIEW（商家提交審核）
 * - PENDING_REVIEW → APPROVED（Admin 批准）
 * - PENDING_REVIEW → REJECTED（Admin 駁回，需填原因）
 * - APPROVED → PAID（撥款成功）
 * - 其他轉換均為無效
 *
 * 拆分原因：狀態機邏輯與生成/查詢邏輯分離，便於維護與測試
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementReviewer {

    private final SettlementStatementRepository settlementRepository;
    private final SettlementMapper mapper;

    /**
     * 商家提交結算單審核（PENDING → PENDING_REVIEW）
     */
    @Transactional
    public SettlementStatementResponse submitForReview(UUID statementId) {
        UUID tenantId = TenantContext.getCurrentTenant();

        SettlementStatement statement = settlementRepository.findByIdAndTenantId(statementId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5013, "Settlement statement not found"));

        if (statement.getStatus() != SettlementStatus.PENDING) {
            throw new BusinessException(ErrorCode.E_5014, "Only PENDING statements can be submitted for review");
        }

        statement.setStatus(SettlementStatus.PENDING_REVIEW);
        statement = settlementRepository.save(statement);

        log.info("Settlement statement submitted for review: id={}", statementId);

        return mapper.toStatementResponse(statement);
    }

    /**
     * Admin 批准結算單（PENDING_REVIEW → APPROVED）
     */
    @Transactional
    public SettlementStatementResponse approveStatement(UUID statementId, UUID adminId) {
        SettlementStatement statement = settlementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5013, "Settlement statement not found"));

        if (statement.getStatus() != SettlementStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.E_5014, "Only PENDING_REVIEW statements can be approved");
        }

        statement.setStatus(SettlementStatus.APPROVED);
        statement.setReviewedAt(Instant.now());
        statement = settlementRepository.save(statement);

        log.info("Settlement statement approved: id={}, by={}", statementId, adminId);

        return mapper.toStatementResponse(statement);
    }

    /**
     * Admin 駁回結算單（PENDING_REVIEW → REJECTED）
     */
    @Transactional
    public SettlementStatementResponse rejectStatement(UUID statementId, UUID adminId, String reason) {
        SettlementStatement statement = settlementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5013, "Settlement statement not found"));

        if (statement.getStatus() != SettlementStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.E_5014, "Only PENDING_REVIEW statements can be rejected");
        }

        statement.setStatus(SettlementStatus.REJECTED);
        statement.setReviewedAt(Instant.now());
        statement.setRejectionReason(reason);
        statement = settlementRepository.save(statement);

        log.info("Settlement statement rejected: id={}, by={}, reason={}", statementId, adminId, reason);

        return mapper.toStatementResponse(statement);
    }

    /**
     * Admin 取得所有待審核結算單
     */
    @Transactional(readOnly = true)
    public SettlementStatementListResponse getPendingReviewStatements(int page, int size) {
        Page<SettlementStatement> statements = settlementRepository.findByStatusOrderByGeneratedAtDesc(
                SettlementStatus.PENDING_REVIEW,
                PageRequest.of(page, size));

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
}
