package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.CreditNote;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.settlement.CreditNoteRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.infrastructure.persistence.UniqueConflictRetry;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

/**
 * SettlementReversalService 單元測試（Sprint 86，PRD §6.2.1 PAID 結算單逆轉雙重授權）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementReversalService 單元測試（Sprint 86）")
class SettlementReversalServiceTest {

    @Mock
    private SettlementStatementRepository settlementRepository;

    @Mock
    private CreditNoteRepository creditNoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SettlementMapper mapper;

    private SettlementReversalService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();
    private static final UUID SUPER_ADMIN_ID = UUID.randomUUID();
    private static final UUID CFO_ID = UUID.randomUUID();

    private SettlementReversalService newService() {
        // DEF-448：撞號時由 UniqueConflictRetry 整個交易重做；這裡用 mock 的交易管理員，只驗重做的次數與結果。
        return new SettlementReversalService(settlementRepository, creditNoteRepository, userRepository, mapper,
                new UniqueConflictRetry(org.mockito.Mockito.mock(PlatformTransactionManager.class)));
    }

    private SettlementStatement paidStatement() {
        return SettlementStatement.builder()
                .id(STATEMENT_ID)
                .tenant(Tenant.builder().id(TENANT_ID).build())
                .tenantId(TENANT_ID)
                .status(SettlementStatus.PAID)
                .netSettlementAmount(new BigDecimal("5000.00"))
                .build();
    }

    private SettlementStatement reversalPendingStatement(String initiatedByRole) {
        SettlementStatement statement = paidStatement();
        statement.setStatus(SettlementStatus.REVERSAL_PENDING);
        statement.setReversalInitiatedByRole(initiatedByRole);
        statement.setReversalReason("測試逆轉原因");
        return statement;
    }

    @Test
    @DisplayName("initiateReversal：PAID 結算單由 SUPER_ADMIN 發起成功，轉 REVERSAL_PENDING")
    void initiateReversal_paidStatement_bySuperAdmin_succeeds() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(paidStatement()));
        when(userRepository.findById(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(User.builder().id(SUPER_ADMIN_ID).build()));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PAID, SettlementStatus.REVERSAL_PENDING)).thenReturn(1);
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder().build());

        service.initiateReversal(STATEMENT_ID, SUPER_ADMIN_ID, "SUPER_ADMIN", "客戶投訴要求全額退款");

        ArgumentCaptor<SettlementStatement> captor = ArgumentCaptor.forClass(SettlementStatement.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SettlementStatus.REVERSAL_PENDING);
        assertThat(captor.getValue().getReversalInitiatedByRole()).isEqualTo("SUPER_ADMIN");
        assertThat(captor.getValue().getReversalReason()).isEqualTo("客戶投訴要求全額退款");
    }

    @Test
    @DisplayName("initiateReversal：非 PAID 狀態發起應拋出 E_5014")
    void initiateReversal_nonPaidStatus_throwsE5014() {
        service = newService();
        SettlementStatement statement = paidStatement();
        statement.setStatus(SettlementStatus.APPROVED);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));

        assertThatThrownBy(() -> service.initiateReversal(STATEMENT_ID, SUPER_ADMIN_ID, "SUPER_ADMIN", "reason"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));
    }

    @Test
    @DisplayName("initiateReversal：非 SUPER_ADMIN/CFO 角色發起應拒絕")
    void initiateReversal_invalidRole_throwsE1007() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(paidStatement()));

        assertThatThrownBy(() -> service.initiateReversal(STATEMENT_ID, SUPER_ADMIN_ID, "ADMIN", "reason"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));
    }

    @Test
    @DisplayName("confirmReversal：CFO 確認 SUPER_ADMIN 發起的逆轉成功——先條件式更新搶占（REVERSAL_PENDING → REVERSED）再產生 CreditNote，回應狀態是 REVERSED")
    void confirmReversal_differentRole_succeeds() {
        service = newService();
        SettlementStatement statement = reversalPendingStatement("SUPER_ADMIN");
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(userRepository.findById(CFO_ID)).thenReturn(Optional.of(User.builder().id(CFO_ID).build()));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED)).thenReturn(1);
        // 回應是由「搶占前讀到的實體」組出來的，所以 mapper 看到的狀態還是 REVERSAL_PENDING——服務要把它補成 REVERSED
        when(mapper.toStatementResponse(any())).thenReturn(
                SettlementStatementResponse.builder().id(STATEMENT_ID).status("REVERSAL_PENDING").build());

        SettlementStatementResponse response = service.confirmReversal(STATEMENT_ID, CFO_ID, "CFO");

        ArgumentCaptor<CreditNote> creditNoteCaptor = ArgumentCaptor.forClass(CreditNote.class);
        verify(creditNoteRepository).save(creditNoteCaptor.capture());
        assertThat(creditNoteCaptor.getValue().getAmount()).isEqualByComparingTo("-5000.00");
        assertThat(creditNoteCaptor.getValue().getTenant().getId()).isEqualTo(TENANT_ID);
        assertThat(response.getStatus()).as("狀態已由搶占的 UPDATE 寫成 REVERSED，回應要反映它").isEqualTo("REVERSED");
        assertThat(creditNoteCaptor.getValue().getCreditNoteNumber())
                .as("DEF-448：當日第一張，編號是 CN-{租戶前 8 碼}-{開立日}-01").isEqualTo(todayNumberPrefix() + "01");

        // DEF-447：搶占在產生信用票據之前（輸家不可留下信用票據）；DEF-448：流水號在搶占之後才算（只有贏家算）
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(settlementRepository, creditNoteRepository);
        order.verify(settlementRepository).updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED);
        order.verify(creditNoteRepository).countByTenantIdAndCreditNoteNumberStartingWith(TENANT_ID, todayNumberPrefix());
        order.verify(creditNoteRepository).save(any(CreditNote.class));

        // 搶占的 UPDATE 已經把狀態寫完：實體不可再被 setter 弄髒、也不可 save（整列寫回正是 DEF-444／447 的機制）
        assertThat(statement.getStatus()).as("實體不該被 setter 修改（髒檢查會整列寫回）").isEqualTo(SettlementStatus.REVERSAL_PENDING);
        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("🔴 DEF-447 confirmReversal：併發搶占失敗（快照仍是 REVERSAL_PENDING，條件式更新回 0）→ E_5014，輸家不產生信用票據、不 save")
    void confirmReversal_concurrentClaimLost_throwsE5014WithoutCreditNote() {
        // 兩位 CFO 幾乎同時確認，都通過「快照 status == REVERSAL_PENDING」的前置檢查，但只有一邊搶到條件式更新
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(reversalPendingStatement("SUPER_ADMIN")));
        when(userRepository.findById(CFO_ID)).thenReturn(Optional.of(User.builder().id(CFO_ID).build()));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED)).thenReturn(0);

        assertThatThrownBy(() -> service.confirmReversal(STATEMENT_ID, CFO_ID, "CFO"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        verify(creditNoteRepository, never()).save(any());
        verify(settlementRepository, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(mapper);
        // DEF-448：搶輸是 BusinessException（409），不是唯一約束違反——UniqueConflictRetry 不可把它當成撞號重做
        verify(settlementRepository, times(1)).updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED);
        verify(creditNoteRepository, never()).countByTenantIdAndCreditNoteNumberStartingWith(any(), any());
    }

    // ── Sprint 324 DEF-448：信用票據編號＝開立日＋當日流水號；撞號整個交易重做 ───────────────────

    /** 今天開立的信用票據編號前綴 {@code CN-{租戶 id 前 8 碼}-{yyyyMMdd}-}。 */
    private static String todayNumberPrefix() {
        return "CN-" + TENANT_ID.toString().substring(0, 8) + "-" + LocalDate.now().toString().replace("-", "") + "-";
    }

    /** 當日已有 {@code alreadyIssued} 張時確認一張逆轉，回傳開出來的信用票據編號。 */
    private String confirmAndCaptureNumber(final long alreadyIssued) {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(reversalPendingStatement("SUPER_ADMIN")));
        when(userRepository.findById(CFO_ID)).thenReturn(Optional.of(User.builder().id(CFO_ID).build()));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED)).thenReturn(1);
        when(creditNoteRepository.countByTenantIdAndCreditNoteNumberStartingWith(TENANT_ID, todayNumberPrefix()))
                .thenReturn(alreadyIssued);
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder().build());

        service.confirmReversal(STATEMENT_ID, CFO_ID, "CFO");

        ArgumentCaptor<CreditNote> captor = ArgumentCaptor.forClass(CreditNote.class);
        verify(creditNoteRepository).save(captor.capture());
        return captor.getValue().getCreditNoteNumber();
    }

    @Test
    @DisplayName("DEF-448 confirmReversal：流水號＝當日已開立張數＋1，至少兩位（0 張→-01、8 張→-09、9 張→-10、98 張→-99、99 張→-100）")
    void confirmReversal_runningNumberIsCountPlusOnePaddedToTwoDigits() {
        assertThat(confirmAndCaptureNumber(0)).isEqualTo(todayNumberPrefix() + "01");
        org.mockito.Mockito.reset(settlementRepository, creditNoteRepository, userRepository, mapper);
        assertThat(confirmAndCaptureNumber(8)).isEqualTo(todayNumberPrefix() + "09");
        org.mockito.Mockito.reset(settlementRepository, creditNoteRepository, userRepository, mapper);
        assertThat(confirmAndCaptureNumber(9)).isEqualTo(todayNumberPrefix() + "10");
        org.mockito.Mockito.reset(settlementRepository, creditNoteRepository, userRepository, mapper);
        assertThat(confirmAndCaptureNumber(98)).isEqualTo(todayNumberPrefix() + "99");
        org.mockito.Mockito.reset(settlementRepository, creditNoteRepository, userRepository, mapper);
        assertThat(confirmAndCaptureNumber(99)).isEqualTo(todayNumberPrefix() + "100");
    }

    @Test
    @DisplayName("🔴 DEF-448 confirmReversal：INSERT 撞唯一約束（別張結算單搶先用掉流水號）→ 整個交易（含狀態搶占）重做，這次改用下一個流水號，回應仍是成功")
    void confirmReversal_uniqueConflictOnTheFirstAttempt_rerunsTheWholeTransactionWithTheNextNumber() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(reversalPendingStatement("SUPER_ADMIN")));
        when(userRepository.findById(CFO_ID)).thenReturn(Optional.of(User.builder().id(CFO_ID).build()));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED)).thenReturn(1);
        // 第一次計數時別張結算單的票據還沒提交（0 張）；撞約束整個重做之後，它已提交（1 張）
        when(creditNoteRepository.countByTenantIdAndCreditNoteNumberStartingWith(TENANT_ID, todayNumberPrefix()))
                .thenReturn(0L, 1L);
        when(creditNoteRepository.save(any(CreditNote.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder().build());

        SettlementStatementResponse response = service.confirmReversal(STATEMENT_ID, CFO_ID, "CFO");

        assertThat(response.getStatus()).isEqualTo("REVERSED");
        ArgumentCaptor<CreditNote> captor = ArgumentCaptor.forClass(CreditNote.class);
        verify(creditNoteRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(CreditNote::getCreditNoteNumber)
                .containsExactly(todayNumberPrefix() + "01", todayNumberPrefix() + "02");
        // 「整個重做」：狀態搶占也重做了一次（第一次的搶占隨交易回滾，不能沿用）
        verify(settlementRepository, times(2)).updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.REVERSAL_PENDING, SettlementStatus.REVERSED);
    }

    @Test
    @DisplayName("🔴 DEF-448 confirmReversal 不可標 @Transactional：UniqueConflictRetry 必須是最外層，已在交易內就無法重做（撞號會直接 500）")
    void confirmReversal_isNotTransactional_soTheRetryComponentOwnsTheTransaction() throws NoSuchMethodException {
        assertThat(SettlementReversalService.class
                .getMethod("confirmReversal", java.util.UUID.class, java.util.UUID.class, String.class)
                .getAnnotation(Transactional.class)).isNull();
        assertThat(SettlementReversalService.class.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    @DisplayName("confirmReversal：同角色確認應被拒絕（雙重授權核心檢查）——連搶占都不嘗試")
    void confirmReversal_sameRoleAsInitiator_throwsE1007() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID))
                .thenReturn(Optional.of(reversalPendingStatement("SUPER_ADMIN")));

        assertThatThrownBy(() -> service.confirmReversal(STATEMENT_ID, SUPER_ADMIN_ID, "SUPER_ADMIN"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));

        verify(creditNoteRepository, never()).save(any());
        verify(settlementRepository, never()).save(any());
        verify(settlementRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    @DisplayName("confirmReversal：非 REVERSAL_PENDING 狀態確認應拋出 E_5014——連搶占都不嘗試")
    void confirmReversal_nonReversalPendingStatus_throwsE5014() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(paidStatement()));

        assertThatThrownBy(() -> service.confirmReversal(STATEMENT_ID, CFO_ID, "CFO"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        verify(settlementRepository, never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    @DisplayName("initiateReversal：併發搶占發起權失敗（快照仍是 PAID）→ E_5014，不覆寫發起人")
    void initiateReversal_concurrentClaimLost_throwsE5014() {
        // 🔴 Sprint 137 DEF-138：模擬 SUPER_ADMIN 與 CFO 幾乎同時發起逆轉，都通過「快照 status == PAID」
        // 的前置檢查，但只有一邊真正搶到原子 CAS（另一邊 updateStatusIfCurrent 回傳 0）。
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(paidStatement()));
        when(userRepository.findById(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(User.builder().id(SUPER_ADMIN_ID).build()));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID,
                SettlementStatus.PAID, SettlementStatus.REVERSAL_PENDING)).thenReturn(0);

        assertThatThrownBy(() -> service.initiateReversal(STATEMENT_ID, SUPER_ADMIN_ID, "SUPER_ADMIN", "reason"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("initiateReversal：結算單不存在拋出 E_5013")
    void initiateReversal_statementNotFound_throwsE5013() {
        service = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateReversal(STATEMENT_ID, SUPER_ADMIN_ID, "SUPER_ADMIN", "reason"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5013));
    }

    @Test
    @DisplayName("getReversalCandidateStatements：未指定 tenantId 時跨租戶查詢 PAID+REVERSAL_PENDING")
    void getReversalCandidateStatements_noTenantOverride_queriesAcrossTenants() {
        service = newService();
        Page<SettlementStatement> page = new PageImpl<>(List.of(paidStatement(), reversalPendingStatement("SUPER_ADMIN")));
        when(settlementRepository.findByStatusInOrderByGeneratedAtDesc(anyList(), any(Pageable.class)))
                .thenReturn(page);
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder().build());

        SettlementStatementListResponse response = service.getReversalCandidateStatements(0, 20, null);

        assertThat(response.getStatements()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(2);
        verify(settlementRepository, never()).findByTenantIdAndStatusInOrderByGeneratedAtDesc(
                any(), anyList(), any(Pageable.class));
    }

    @Test
    @DisplayName("getReversalCandidateStatements：指定 tenantId 時限定該租戶查詢")
    void getReversalCandidateStatements_withTenantOverride_scopesToTenant() {
        service = newService();
        Page<SettlementStatement> page = new PageImpl<>(List.of(paidStatement()));
        when(settlementRepository.findByTenantIdAndStatusInOrderByGeneratedAtDesc(
                eq(TENANT_ID), anyList(), any(Pageable.class))).thenReturn(page);
        when(mapper.toStatementResponse(any())).thenReturn(SettlementStatementResponse.builder().build());

        SettlementStatementListResponse response = service.getReversalCandidateStatements(0, 20, TENANT_ID);

        assertThat(response.getStatements()).hasSize(1);
        verify(settlementRepository, never()).findByStatusInOrderByGeneratedAtDesc(anyList(), any(Pageable.class));
    }

    @Test
    @DisplayName("Sprint 164: size 帶超大值時，實際查詢頁面大小上限為 100（避免資源耗盡）")
    void getReversalCandidateStatements_hugeSize_cappedAt100() {
        SettlementReversalService service = newService();
        when(settlementRepository.findByStatusInOrderByGeneratedAtDesc(anyList(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getReversalCandidateStatements(0, 999999999, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(settlementRepository).findByStatusInOrderByGeneratedAtDesc(anyList(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
