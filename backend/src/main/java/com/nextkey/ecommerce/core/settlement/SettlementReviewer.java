package com.nextkey.ecommerce.core.settlement;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
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
    private final BookingRepository bookingRepository;
    private final SettlementAdjustmentRepository adjustmentRepository;

    /** 只用於搶占成功後重新載入結算單，見 {@link #approveStatement} 內的 DEF-444 說明。 */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 商家提交結算單審核（PENDING → PENDING_REVIEW）
     */
    @Transactional
    public SettlementStatementResponse submitForReview(UUID statementId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        // Sprint 234：沒有店鋪的呼叫者當作找不到（此端點只要求 order:read，BUYER 也持有）
        if (!TenantContext.isStoreTenant(tenantId)) {
            throw new BusinessException(ErrorCode.E_5013, "Settlement statement not found");
        }

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

        // 🔴 併發防護（Sprint 320 DEF-442）：上面的狀態檢查只是快照，兩位管理員（或同一位連點兩下）可能同時通過它，
        // 後寫者會無條件覆寫先寫者已提交的批准／駁回。見 SettlementStatementRepository.updateStatusIfCurrent 說明。
        int claimed = settlementRepository.updateStatusIfCurrent(statementId,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.APPROVED);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.E_5014, "Only PENDING_REVIEW statements can be approved");
        }

        // 🔴 金額防護（Sprint 321 DEF-444）：搶占只保證「狀態」轉換不被覆寫。上面 findById 讀到的金額可能已過期——退款對
        // PENDING／PENDING_REVIEW 的結算單以原子 UPDATE 直接扣款（SettlementAdjustmentService.applyDirectDeduction），
        // 可以在我們讀取之後、搶占之前提交；而下面的 setStatus＋save 是整列寫回（SettlementStatement 沒有 @DynamicUpdate），
        // 會用舊金額把那筆退款蓋掉，transfer 也會拿舊淨額撥款。搶占成功＝這一列已被本交易鎖住（其他交易對它的 UPDATE 要等本交易
        // 結束），此時重新載入拿到的是鎖住當下最新的已提交值。必須在任何 setter 之前呼叫——refresh 會丟棄記憶體中尚未寫出的修改。
        entityManager.refresh(statement);

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
        // Sprint 321（DEF-443）：原本在本交易裡呼叫並用 try-catch 吞例外，但 createTransferForStatement 是 @Transactional
        // （加入本交易），任何 RuntimeException 穿出它的交易代理時本交易就被標成 rollback-only，吞掉也沒用——提交時
        // UnexpectedRollbackException，核准連同稽核整個回滾、HTTP 500。改成本交易提交之後才觸發，並在自己的新交易裡執行。
        triggerTransferAfterCommit(statementId);

        // 回應是核准決定當下的快照（APPROVED）；撥款在提交之後才嘗試，結果（PAID／FAILED／仍是 APPROVED）要另行查詢（API_M07 §4.5）。
        return mapper.toStatementResponse(statement);
    }

    /**
     * 在目前交易<strong>提交之後</strong>才觸發撥款（比照 {@code BookingService}、{@code PaymentStateService} 既有的
     * {@code afterCommit} 模式）；沒有作用中的交易（例如未經 Spring 交易的單元測試）時立即執行。交易回滾（搶占輸了、
     * 稽核寫入失敗…）時不會觸發——沒有提交的核准不可撥款。撥款在 {@code TransferService.createTransferAfterApproval} 的新交易裡
     * 執行，任何例外只記錄：核准已經提交，結算單停在 {@code APPROVED}，可用 {@code POST /v2/admin/transfers/{id}/retry} 補撥款。
     */
    private void triggerTransferAfterCommit(final UUID statementId) {
        Runnable trigger = () -> {
            try {
                transferService.createTransferAfterApproval(statementId);
            } catch (RuntimeException e) {
                log.error("Transfer trigger failed after settlement approval (the approval stays committed; "
                        + "retry via POST /v2/admin/transfers/{}/retry): statementId={}, error={}",
                        statementId, statementId, e.getMessage(), e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            trigger.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                trigger.run();
            }
        });
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

        // 🔴 併發防護（Sprint 320 DEF-442）：同 approveStatement。駁回還會釋放訂單／調整單，輸家若也「成功」，
        // 會在贏家批准（將撥款）的結算單上釋放訂單，下一期重複結算。
        int claimed = settlementRepository.updateStatusIfCurrent(statementId,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.REJECTED);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.E_5014, "Only PENDING_REVIEW statements can be rejected");
        }

        // 🔴 金額防護（Sprint 321 DEF-444）：同 approveStatement——搶占成功後、任何 setter 之前先重新載入，
        // 否則整列寫回會蓋掉同時入帳的退款扣款。
        entityManager.refresh(statement);

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
        // Sprint 247（DEF-353）：訂房與訂單同一張結算單，駁回時必須一併釋放，否則訂房會永遠掛在這張死掉的結算單上
        int releasedBookings = bookingRepository.releaseBookingsOfStatement(statementId);
        int releasedAdjustments = adjustmentRepository.releaseAppliedTo(statementId);
        log.info("Released settlement claims on rejection: statementId={}, orders={}, bookings={}, adjustments={}",
                statementId, releasedOrders, releasedBookings, releasedAdjustments);

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
