package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * SettlementReviewer 單元測試（Sprint 81，DEF-040 修復）。
 *
 * <p>驗證非 SUPER_ADMIN 僅能操作自己租戶的結算單；SUPER_ADMIN 可跨租戶操作/查詢。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementReviewer 單元測試 (Sprint 81 DEF-040)")
class SettlementReviewerTest {

    @Mock
    private SettlementStatementRepository settlementRepository;

    @Mock
    private SettlementMapper mapper;

    @Mock
    private TransferService transferService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private com.nextkey.ecommerce.domain.repository.OrderRepository orderRepository;

    @Mock
    private com.nextkey.ecommerce.domain.repository.BookingRepository bookingRepository;

    @Mock
    private com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository adjustmentRepository;

    @Mock
    private EntityManager entityManager;

    private SettlementReviewer reviewer;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private SettlementReviewer newReviewer() {
        SettlementReviewer created = new SettlementReviewer(settlementRepository, mapper, transferService, userRepository,
                auditService, orderRepository, bookingRepository, adjustmentRepository);
        // EntityManager 是 @PersistenceContext 欄位注入（不在建構子），比照 PaymentStateServiceBookingTest 的作法
        ReflectionTestUtils.setField(created, "entityManager", entityManager);
        return created;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        // DEF-443 的案例會手動啟用交易同步；案例中途失敗也不能把它留給下一個案例
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private SettlementStatement pendingReviewStatement(UUID tenantId) {
        return SettlementStatement.builder()
                .id(STATEMENT_ID)
                .tenantId(tenantId)
                .status(SettlementStatus.PENDING_REVIEW)
                .build();
    }

    @Test
    @DisplayName("🔴 approveStatement: 非 SUPER_ADMIN 批准他租戶結算單應被拒絕")
    void approveStatement_crossTenantNonSuperAdmin_denied() {
        reviewer = newReviewer();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(pendingReviewStatement(OTHER_TENANT_ID)));
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));
    }

    @Test
    @DisplayName("approveStatement: 非 SUPER_ADMIN 批准自己租戶結算單成功")
    void approveStatement_ownTenant_succeeds() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingReviewStatement(TENANT_ID);
        User admin = User.builder().id(ADMIN_ID).build();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.APPROVED)).thenReturn(1);
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin);
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("APPROVED").build());
        TenantContext.setCurrentTenant(TENANT_ID);

        SettlementStatementResponse response = reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.APPROVED);
        assertThat(statement.getReviewedBy()).isEqualTo(admin);
        assertThat(statement.getApprovedAt()).isNotNull();
        verify(transferService).createTransferAfterApproval(STATEMENT_ID);
        verify(auditService).record(eq("SETTLEMENT_APPROVED"), eq("SETTLEMENT_STATEMENT"), eq(STATEMENT_ID), eq(TENANT_ID),
                eq("PENDING_REVIEW"), eq("APPROVED"), any(), eq(ADMIN_ID));
    }

    @Test
    @DisplayName("approveStatement: SUPER_ADMIN 批准任意租戶結算單成功（略過租戶檢查）")
    void approveStatement_superAdmin_bypassesTenantCheck() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingReviewStatement(OTHER_TENANT_ID);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.APPROVED)).thenReturn(1);
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(User.builder().id(ADMIN_ID).build());
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("APPROVED").build());
        TenantContext.setCurrentTenant(TENANT_ID);

        SettlementStatementResponse response = reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, true);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("🔴 rejectStatement: 非 SUPER_ADMIN 駁回他租戶結算單應被拒絕")
    void rejectStatement_crossTenantNonSuperAdmin_denied() {
        reviewer = newReviewer();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(pendingReviewStatement(OTHER_TENANT_ID)));
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> reviewer.rejectStatement(STATEMENT_ID, ADMIN_ID, "reason", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));
    }

    @Test
    @DisplayName("rejectStatement: 非 SUPER_ADMIN 駁回自己租戶結算單成功")
    void rejectStatement_ownTenant_succeeds() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingReviewStatement(TENANT_ID);
        User admin = User.builder().id(ADMIN_ID).build();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.REJECTED)).thenReturn(1);
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin);
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("REJECTED").build());
        TenantContext.setCurrentTenant(TENANT_ID);

        SettlementStatementResponse response = reviewer.rejectStatement(STATEMENT_ID, ADMIN_ID, "reason", false);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(statement.getReviewedBy()).isEqualTo(admin);
        verify(auditService).record(eq("SETTLEMENT_REJECTED"), eq("SETTLEMENT_STATEMENT"), eq(STATEMENT_ID), eq(TENANT_ID),
                eq("PENDING_REVIEW"), eq("REJECTED"), eq("reason"), eq(ADMIN_ID));
    }

    // ── Sprint 320 DEF-442：批准／駁回要用條件式更新搶占，輸家不寫入任何東西 ──────────────

    private SettlementStatement statementWithStatus(SettlementStatus status) {
        return SettlementStatement.builder().id(STATEMENT_ID).tenantId(TENANT_ID).status(status).build();
    }

    @Test
    @DisplayName("🔴 DEF-442 approveStatement: 搶占輸了（updateStatusIfCurrent 影響 0 列）→ E-5014；不儲存、不留稽核、不觸發 transfer、記憶體狀態不變")
    void approveStatement_lostTheClaim_failsWithoutAnyWrite() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingReviewStatement(TENANT_ID);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.APPROVED)).thenReturn(0);
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        verify(settlementRepository, never()).save(any(SettlementStatement.class));
        verifyNoInteractions(auditService, transferService);
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("🔴 DEF-442 rejectStatement: 搶占輸了 → E-5014；不儲存、不留稽核、不釋放訂單／訂房／調整單（否則會在贏家批准的結算單上釋放訂單）")
    void rejectStatement_lostTheClaim_failsWithoutAnyWriteOrRelease() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingReviewStatement(TENANT_ID);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.REJECTED)).thenReturn(0);
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> reviewer.rejectStatement(STATEMENT_ID, ADMIN_ID, "reason", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        verify(settlementRepository, never()).save(any(SettlementStatement.class));
        verifyNoInteractions(auditService, orderRepository, bookingRepository, adjustmentRepository);
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.PENDING_REVIEW);
        assertThat(statement.getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("DEF-442 approveStatement／rejectStatement: 狀態早已不是 PENDING_REVIEW → 直接 E-5014，不必打資料庫搶占（循序情境的前置檢查）")
    void notPendingReview_failsBeforeClaiming() {
        reviewer = newReviewer();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(statementWithStatus(SettlementStatus.APPROVED)));
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));
        assertThatThrownBy(() -> reviewer.rejectStatement(STATEMENT_ID, ADMIN_ID, "reason", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        verify(settlementRepository, never()).updateStatusIfCurrent(any(), any(), any());
        verify(settlementRepository, never()).save(any(SettlementStatement.class));
    }

    @Test
    @DisplayName("DEF-442 approveStatement: 搶占發生在任何寫入之前（先搶占 → 儲存 → 稽核 → 觸發 transfer）")
    void approveStatement_claimsBeforeAnyWrite() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingReviewStatement(TENANT_ID);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.APPROVED)).thenReturn(1);
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(User.builder().id(ADMIN_ID).build());
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("APPROVED").build());
        TenantContext.setCurrentTenant(TENANT_ID);

        reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false);

        InOrder order = inOrder(settlementRepository, auditService, transferService);
        order.verify(settlementRepository).updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.APPROVED);
        order.verify(settlementRepository).save(statement);
        order.verify(auditService).record(eq("SETTLEMENT_APPROVED"), eq("SETTLEMENT_STATEMENT"), eq(STATEMENT_ID),
                eq(TENANT_ID), eq("PENDING_REVIEW"), eq("APPROVED"), any(), eq(ADMIN_ID));
        order.verify(transferService).createTransferAfterApproval(STATEMENT_ID);
    }

    @Test
    @DisplayName("DEF-442 rejectStatement: 搶占發生在任何寫入與釋放之前（先搶占 → 儲存 → 稽核 → 釋放訂單／訂房／調整單）")
    void rejectStatement_claimsBeforeAnyWriteOrRelease() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingReviewStatement(TENANT_ID);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.REJECTED)).thenReturn(1);
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(User.builder().id(ADMIN_ID).build());
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("REJECTED").build());
        TenantContext.setCurrentTenant(TENANT_ID);

        reviewer.rejectStatement(STATEMENT_ID, ADMIN_ID, "reason", false);

        InOrder order = inOrder(settlementRepository, auditService, orderRepository, bookingRepository,
                adjustmentRepository);
        order.verify(settlementRepository).updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.REJECTED);
        order.verify(settlementRepository).save(statement);
        order.verify(auditService).record(eq("SETTLEMENT_REJECTED"), eq("SETTLEMENT_STATEMENT"), eq(STATEMENT_ID),
                eq(TENANT_ID), eq("PENDING_REVIEW"), eq("REJECTED"), eq("reason"), eq(ADMIN_ID));
        order.verify(orderRepository).releaseOrdersOfStatement(STATEMENT_ID);
        order.verify(bookingRepository).releaseBookingsOfStatement(STATEMENT_ID);
        order.verify(adjustmentRepository).releaseAppliedTo(STATEMENT_ID);
    }

    // ── Sprint 321 DEF-444：搶占成功後要重新載入，整列寫回才不會蓋掉同時入帳的退款 ─────────────

    /**
     * 真實的 {@code EntityManager.refresh} 會用資料庫那一列<strong>取代</strong>記憶體狀態（包含丟棄尚未寫出的修改）。
     * mock 的 refresh 什麼都不做，測不出「refresh 放在哪裡」——這裡讓它照真實語意取代：搶占之後資料庫是目標狀態、
     * 還沒有審核欄位，而且同時入帳的退款已把金額改成 total_refunds 100／淨額 800。
     */
    private void stubRefreshAsReloadFromDatabase(SettlementStatement statement, SettlementStatus statusAfterClaim) {
        doAnswer(inv -> {
            SettlementStatement s = inv.getArgument(0);
            s.setStatus(statusAfterClaim);
            s.setReviewedBy(null);
            s.setReviewedAt(null);
            s.setApprovedAt(null);
            s.setRejectionReason(null);
            s.setTotalRefunds(new BigDecimal("100.00"));
            s.setNetSettlementAmount(new BigDecimal("800.00"));
            return null;
        }).when(entityManager).refresh(statement);
    }

    private SettlementStatement statementWithStaleAmounts() {
        SettlementStatement statement = pendingReviewStatement(TENANT_ID);
        statement.setTotalRefunds(new BigDecimal("0.00"));
        statement.setNetSettlementAmount(new BigDecimal("900.00"));
        return statement;
    }

    @Test
    @DisplayName("🔴 DEF-444 approveStatement: 搶占成功後、任何 setter 之前先 refresh——寫回的實體帶資料庫的最新金額，也帶這次批准的審核欄位")
    void approveStatement_refreshesAfterClaimBeforeAnySetter() {
        reviewer = newReviewer();
        SettlementStatement statement = statementWithStaleAmounts();
        User admin = User.builder().id(ADMIN_ID).build();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.APPROVED)).thenReturn(1);
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin);
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("APPROVED").build());
        stubRefreshAsReloadFromDatabase(statement, SettlementStatus.APPROVED);
        TenantContext.setCurrentTenant(TENANT_ID);

        reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false);

        InOrder order = inOrder(settlementRepository, entityManager);
        order.verify(settlementRepository).updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.APPROVED);
        order.verify(entityManager).refresh(statement);
        order.verify(settlementRepository).save(statement);
        assertThat(statement.getTotalRefunds()).as("寫回的退款扣款是資料庫的最新值，不是讀取當下的舊值").isEqualByComparingTo("100.00");
        assertThat(statement.getNetSettlementAmount()).as("寫回的淨額是資料庫的最新值").isEqualByComparingTo("800.00");
        assertThat(statement.getReviewedBy()).as("refresh 在 setter 之前，沒有把審核人丟掉").isEqualTo(admin);
        assertThat(statement.getApprovedAt()).as("refresh 在 setter 之前，沒有把批准時間丟掉").isNotNull();
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.APPROVED);
    }

    @Test
    @DisplayName("🔴 DEF-444 rejectStatement: 搶占成功後、任何 setter 之前先 refresh——寫回的實體帶資料庫的最新金額，也帶這次駁回的審核欄位與原因")
    void rejectStatement_refreshesAfterClaimBeforeAnySetter() {
        reviewer = newReviewer();
        SettlementStatement statement = statementWithStaleAmounts();
        User admin = User.builder().id(ADMIN_ID).build();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.REJECTED)).thenReturn(1);
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin);
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("REJECTED").build());
        stubRefreshAsReloadFromDatabase(statement, SettlementStatus.REJECTED);
        TenantContext.setCurrentTenant(TENANT_ID);

        reviewer.rejectStatement(STATEMENT_ID, ADMIN_ID, "金額有疑慮", false);

        InOrder order = inOrder(settlementRepository, entityManager);
        order.verify(settlementRepository).updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.REJECTED);
        order.verify(entityManager).refresh(statement);
        order.verify(settlementRepository).save(statement);
        assertThat(statement.getTotalRefunds()).as("寫回的退款扣款是資料庫的最新值，不是讀取當下的舊值").isEqualByComparingTo("100.00");
        assertThat(statement.getNetSettlementAmount()).as("寫回的淨額是資料庫的最新值").isEqualByComparingTo("800.00");
        assertThat(statement.getReviewedBy()).as("refresh 在 setter 之前，沒有把審核人丟掉").isEqualTo(admin);
        assertThat(statement.getRejectionReason()).as("refresh 在 setter 之前，沒有把駁回原因丟掉").isEqualTo("金額有疑慮");
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.REJECTED);
    }

    // ── Sprint 322 DEF-445：提交審核要搶占＋refresh，不可用整列寫回蓋掉同時入帳的退款 ─────────────
    //
    // submitForReview 在真實類別上原本沒有任何單元測試（只有系統租戶守門與 mock 掉 reviewer 的 controller 測試）。
    // 真實競態由 SettlementReviewRaceRealStackIntegrationTest 守住；這裡守 mock 看得到的部分：搶占的方向、輸家不碰任何東西、
    // refresh 在搶占之後，以及回應是由「重新載入後」的實體組出來的（不是讀取當下的過期快照）。

    private SettlementStatement pendingStatementWithStaleAmounts() {
        SettlementStatement statement = statementWithStaleAmounts();
        statement.setStatus(SettlementStatus.PENDING);
        return statement;
    }

    @Test
    @DisplayName("🔴 DEF-445 submitForReview: 先搶占（PENDING→PENDING_REVIEW）、再 refresh；回應是重新載入後的狀態與金額（不是過期的 PENDING／0／900）")
    void submitForReview_claimsThenRefreshesAndRespondsWithTheReloadedState() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingStatementWithStaleAmounts();
        when(settlementRepository.findByIdAndTenantId(STATEMENT_ID, TENANT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING, SettlementStatus.PENDING_REVIEW)).thenReturn(1);
        // 同時入帳的退款已把資料庫的金額改成 100／800；真實的 refresh 會用它取代記憶體狀態
        stubRefreshAsReloadFromDatabase(statement, SettlementStatus.PENDING_REVIEW);
        when(mapper.toStatementResponse(any())).thenAnswer(inv -> {
            SettlementStatement s = inv.getArgument(0);
            return SettlementStatementResponse.builder().id(s.getId()).status(s.getStatus().name())
                    .totalRefunds(s.getTotalRefunds()).netSettlementAmount(s.getNetSettlementAmount()).build();
        });
        TenantContext.setCurrentTenant(TENANT_ID);

        SettlementStatementResponse response = reviewer.submitForReview(STATEMENT_ID);

        InOrder order = inOrder(settlementRepository, entityManager);
        order.verify(settlementRepository).updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING, SettlementStatus.PENDING_REVIEW);
        order.verify(entityManager).refresh(statement);
        assertThat(response.getStatus()).as("回應的狀態是搶占之後資料庫的狀態").isEqualTo("PENDING_REVIEW");
        assertThat(response.getTotalRefunds()).as("回應的退款扣款是鎖住當下的最新值").isEqualByComparingTo("100.00");
        assertThat(response.getNetSettlementAmount()).as("回應的淨額是鎖住當下的最新值").isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("🔴 DEF-445 submitForReview: 搶占輸了（updateStatusIfCurrent 影響 0 列）→ E-5014；不 refresh、不組回應、記憶體狀態不變")
    void submitForReview_lostTheClaim_failsWithoutRefreshing() {
        reviewer = newReviewer();
        SettlementStatement statement = pendingStatementWithStaleAmounts();
        when(settlementRepository.findByIdAndTenantId(STATEMENT_ID, TENANT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING, SettlementStatus.PENDING_REVIEW)).thenReturn(0);
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> reviewer.submitForReview(STATEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        verifyNoInteractions(entityManager, mapper);
        verify(settlementRepository, never()).save(any(SettlementStatement.class));
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.PENDING);
    }

    @Test
    @DisplayName("DEF-445 submitForReview: 狀態早已不是 PENDING（循序的第二次提交）→ 直接 E-5014，不必打資料庫搶占")
    void submitForReview_alreadyPendingReview_failsBeforeClaiming() {
        reviewer = newReviewer();
        when(settlementRepository.findByIdAndTenantId(STATEMENT_ID, TENANT_ID))
                .thenReturn(Optional.of(statementWithStatus(SettlementStatus.PENDING_REVIEW)));
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> reviewer.submitForReview(STATEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        verify(settlementRepository, never()).updateStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName("DEF-445 submitForReview: 結算單不存在（或不屬於呼叫者的租戶）→ E-5013，不搶占")
    void submitForReview_notFound_failsWithE5013WithoutClaiming() {
        reviewer = newReviewer();
        when(settlementRepository.findByIdAndTenantId(STATEMENT_ID, TENANT_ID)).thenReturn(Optional.empty());
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> reviewer.submitForReview(STATEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5013));

        verify(settlementRepository, never()).updateStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(entityManager);
    }

    // ── Sprint 321 DEF-443：撥款觸發在核准交易「提交之後」才執行，失敗不影響核准 ──────────────

    private void stubApprovableStatement() {
        SettlementStatement statement = pendingReviewStatement(TENANT_ID);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PENDING_REVIEW, SettlementStatus.APPROVED)).thenReturn(1);
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(User.builder().id(ADMIN_ID).build());
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder()
                .id(STATEMENT_ID).status("APPROVED").build());
        TenantContext.setCurrentTenant(TENANT_ID);
    }

    @Test
    @DisplayName("🔴 DEF-443 approveStatement: 有交易時，撥款要等核准交易提交（afterCommit）之後才觸發——提交前不可撥款")
    void approveStatement_triggersTransferOnlyAfterTheTransactionCommits() {
        reviewer = newReviewer();
        stubApprovableStatement();
        TransactionSynchronizationManager.initSynchronization();

        reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false);

        verifyNoInteractions(transferService);
        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(transferService).createTransferAfterApproval(STATEMENT_ID);
    }

    @Test
    @DisplayName("🔴 DEF-443 approveStatement: 核准交易回滾（沒有 afterCommit）→ 不可撥款（沒有提交的核准不能付錢）")
    void approveStatement_rolledBackTransaction_neverTriggersTransfer() {
        reviewer = newReviewer();
        stubApprovableStatement();
        TransactionSynchronizationManager.initSynchronization();

        reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false);
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(transferService);
    }

    @Test
    @DisplayName("🔴 DEF-443 approveStatement: 撥款觸發丟出例外 → 只記錄，不外洩（afterCommit 的例外會讓已提交的核准變成 HTTP 500）")
    void approveStatement_transferTriggerFailure_isLoggedNotThrown() {
        reviewer = newReviewer();
        stubApprovableStatement();
        doThrow(new IllegalStateException("模擬閘道連線層當機")).when(transferService).createTransferAfterApproval(STATEMENT_ID);
        TransactionSynchronizationManager.initSynchronization();

        SettlementStatementResponse response = reviewer.approveStatement(STATEMENT_ID, ADMIN_ID, false);

        assertThatCode(TransactionSynchronizationUtils::triggerAfterCommit).doesNotThrowAnyException();
        verify(transferService).createTransferAfterApproval(STATEMENT_ID);
        assertThat(response.getStatus()).as("回應是核准決定的快照").isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("getPendingReviewStatements: 非 SUPER_ADMIN 僅回自己租戶")
    void getPendingReviewStatements_nonSuperAdmin_scopesToCurrentTenant() {
        reviewer = newReviewer();
        TenantContext.setCurrentTenant(TENANT_ID);
        when(settlementRepository.findByTenantIdAndStatusOrderByGeneratedAtDesc(
                eq(TENANT_ID), eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class)))
                .thenReturn(Page.empty());

        SettlementStatementListResponse response = reviewer.getPendingReviewStatements(0, 20, false, null);

        assertThat(response.getTotalElements()).isEqualTo(0L);
        verify(settlementRepository).findByTenantIdAndStatusOrderByGeneratedAtDesc(
                eq(TENANT_ID), eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class));
    }

    @Test
    @DisplayName("getPendingReviewStatements: SUPER_ADMIN 帶 tenantIdOverride 僅回指定租戶")
    void getPendingReviewStatements_superAdminWithOverride_scopesToGivenTenant() {
        reviewer = newReviewer();
        when(settlementRepository.findByTenantIdAndStatusOrderByGeneratedAtDesc(
                eq(OTHER_TENANT_ID), eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class)))
                .thenReturn(Page.empty());

        reviewer.getPendingReviewStatements(0, 20, true, OTHER_TENANT_ID);

        verify(settlementRepository).findByTenantIdAndStatusOrderByGeneratedAtDesc(
                eq(OTHER_TENANT_ID), eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class));
        verify(settlementRepository, org.mockito.Mockito.never())
                .findByStatusOrderByGeneratedAtDesc(any(), any());
    }

    @Test
    @DisplayName("getPendingReviewStatements: SUPER_ADMIN 未帶 tenantIdOverride 維持跨租戶總覽")
    void getPendingReviewStatements_superAdminWithoutOverride_returnsAllTenants() {
        reviewer = newReviewer();
        when(settlementRepository.findByStatusOrderByGeneratedAtDesc(
                eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class)))
                .thenReturn(Page.empty());

        reviewer.getPendingReviewStatements(0, 20, true, null);

        verify(settlementRepository).findByStatusOrderByGeneratedAtDesc(
                eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class));
    }

    @Test
    @DisplayName("Sprint 164: size 帶超大值時，實際查詢頁面大小上限為 100（避免資源耗盡）")
    void getPendingReviewStatements_hugeSize_cappedAt100() {
        reviewer = newReviewer();
        when(settlementRepository.findByStatusOrderByGeneratedAtDesc(
                eq(SettlementStatus.PENDING_REVIEW), any(PageRequest.class)))
                .thenReturn(Page.empty());

        reviewer.getPendingReviewStatements(0, 999999999, true, null);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(settlementRepository).findByStatusOrderByGeneratedAtDesc(
                eq(SettlementStatus.PENDING_REVIEW), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
