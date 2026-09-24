package com.nextkey.ecommerce.core.settlement;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.util.PageableUtils;
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
    private final TransferService transferService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final OrderRepository orderRepository;
    private final SettlementAdjustmentRepository adjustmentRepository;

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
     * Admin 批准結算單（PENDING_REVIEW → APPROVED）。
     * 非 SUPER_ADMIN 僅能批准自己租戶的結算單（Sprint 81，DEF-040 修復）。
     */
    @Transactional
    public SettlementStatementResponse approveStatement(UUID statementId, UUID adminId, boolean isSuperAdmin) {
        SettlementStatement statement = settlementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5013, "Settlement statement not found"));

        checkTenantAccess(statement.getTenantId(), isSuperAdmin);

        if (statement.getStatus() != SettlementStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.E_5014, "Only PENDING_REVIEW statements can be approved");
        }

        statement.setStatus(SettlementStatus.APPROVED);
        statement.setReviewedAt(Instant.now());
        statement.setApprovedAt(Instant.now());
        statement.setReviewedBy(userRepository.getReferenceById(adminId));
        statement = settlementRepository.save(statement);

        log.info("Settlement statement approved: id={}, by={}", statementId, adminId);
        auditService.record("SETTLEMENT_APPROVED", "SETTLEMENT_STATEMENT", statementId, statement.getTenantId(),
                "PENDING_REVIEW", "APPROVED", null, adminId);

        // Sprint 80（AI-2416 Phase D-2）：審核通過後觸發實際 transfer。
        // 任何失敗僅記錄，絕不回滾/中斷「審核通過」這個已持久化的 Admin 決策。
        try {
            transferService.createTransferForStatement(statementId);
        } catch (RuntimeException e) {
            log.error("Transfer trigger failed after settlement approval: statementId={}, error={}",
                    statementId, e.getMessage(), e);
        }

        return mapper.toStatementResponse(statement);
    }

    /**
     * Admin 駁回結算單（PENDING_REVIEW → REJECTED）。
     * 非 SUPER_ADMIN 僅能駁回自己租戶的結算單（Sprint 81，DEF-040 修復）。
     */
    @Transactional
    public SettlementStatementResponse rejectStatement(UUID statementId, UUID adminId, String reason, boolean isSuperAdmin) {
        SettlementStatement statement = settlementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5013, "Settlement statement not found"));

        checkTenantAccess(statement.getTenantId(), isSuperAdmin);

        if (statement.getStatus() != SettlementStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.E_5014, "Only PENDING_REVIEW statements can be rejected");
        }

        statement.setStatus(SettlementStatus.REJECTED);
        statement.setReviewedAt(Instant.now());
        statement.setRejectionReason(reason);
        statement.setReviewedBy(userRepository.getReferenceById(adminId));
        statement = settlementRepository.save(statement);

        log.info("Settlement statement rejected: id={}, by={}, reason={}", statementId, adminId, reason);
        auditService.record("SETTLEMENT_REJECTED", "SETTLEMENT_STATEMENT", statementId, statement.getTenantId(),
                "PENDING_REVIEW", "REJECTED", reason, adminId);

        // DEF-273：駁回是終態、資金未發生——釋放這張結算單認領的訂單與折入的調整單，讓下一期重新結算；
        // 否則訂單永遠掛在一張死掉的結算單上（又是「永遠不被結算」），調整單（賣家該被扣的退款）也隨之消失。
        // 刻意不釋放 FAILED（retryFailedTransfer 可把它改回 APPROVED 重試撥款，釋放會雙重撥款）與 REVERSED（會計沖銷）。
        int releasedOrders = orderRepository.releaseOrdersOfStatement(statementId);
        int releasedAdjustments = adjustmentRepository.releaseAppliedTo(statementId);
        log.info("Released settlement claims on rejection: statementId={}, orders={}, adjustments={}",
                statementId, releasedOrders, releasedAdjustments);

        return mapper.toStatementResponse(statement);
    }

    /**
     * Admin 取得待審核結算單。非 SUPER_ADMIN 僅能查自己租戶；SUPER_ADMIN 可用 {@code tenantIdOverride}
     * 指定查詢特定租戶，未指定則維持跨租戶總覽（Sprint 81，DEF-040 修復）。
     */
    @Transactional(readOnly = true)
    public SettlementStatementListResponse getPendingReviewStatements(
            int page, int size, boolean isSuperAdmin, UUID tenantIdOverride) {
        Pageable pageable = PageableUtils.of(page, size, 100);
        Page<SettlementStatement> statements;

        if (!isSuperAdmin) {
            UUID callerTenantId = TenantContext.getCurrentTenant();
            statements = settlementRepository.findByTenantIdAndStatusOrderByGeneratedAtDesc(
                    callerTenantId, SettlementStatus.PENDING_REVIEW, pageable);
        } else if (tenantIdOverride != null) {
            statements = settlementRepository.findByTenantIdAndStatusOrderByGeneratedAtDesc(
                    tenantIdOverride, SettlementStatus.PENDING_REVIEW, pageable);
        } else {
            statements = settlementRepository.findByStatusOrderByGeneratedAtDesc(
                    SettlementStatus.PENDING_REVIEW, pageable);
        }

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
     * 驗證非 SUPER_ADMIN 呼叫者僅能操作自己租戶的結算單（比照 {@code TransferService.checkTenantAccess}）。
     */
    private void checkTenantAccess(UUID resourceTenantId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return;
        }
        UUID callerTenantId = TenantContext.getCurrentTenant();
        if (!resourceTenantId.equals(callerTenantId)) {
            log.warn("Cross-tenant settlement review access denied: callerTenant={}, resourceTenant={}",
                    callerTenantId, resourceTenantId);
            throw new BusinessException(ErrorCode.E_1007, "No permission to access this tenant's settlement statement");
        }
    }
}
