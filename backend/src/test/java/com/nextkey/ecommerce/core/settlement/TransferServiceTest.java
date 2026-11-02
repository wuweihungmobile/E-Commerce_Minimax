package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.settlement.Transfer;
import com.nextkey.ecommerce.domain.model.settlement.Transfer.TransferStatus;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.domain.repository.settlement.TransferRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * TransferService 單元測試（Sprint 80，AI-2416 Phase D-2）。
 *
 * <p>涵蓋：結算單狀態守門、冪等、toggle/前置條件跳過、Stripe 成功/失敗、跨租戶存取拒絕。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferService 單元測試 (Sprint 80 AI-2416)")
class TransferServiceTest {

    @Mock
    private SettlementStatementRepository settlementRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PaymentGatewayFactory paymentGatewayFactory;

    @Mock
    private FeatureToggleService featureToggleService;

    private TransferService transferService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID STATEMENT_ID = UUID.randomUUID();

    private TransferService newService() {
        return new TransferService(settlementRepository, transferRepository, tenantRepository,
                paymentGatewayFactory, featureToggleService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private SettlementStatement approvedStatement() {
        return SettlementStatement.builder()
                .id(STATEMENT_ID)
                .tenantId(TENANT_ID)
                .netSettlementAmount(new BigDecimal("9000.00"))
                .currency("TWD")
                .status(SettlementStatus.APPROVED)
                .build();
    }

    private Tenant readyTenant() {
        return Tenant.builder()
                .id(TENANT_ID)
                .stripeConnectAccountId("acct_test_1")
                .connectOnboardingStatus(Tenant.ConnectOnboardingStatus.COMPLETE)
                .connectChargesEnabled(true)
                .connectPayoutsEnabled(true)
                .build();
    }

    @Test
    @DisplayName("createTransferForStatement: 非 APPROVED 狀態應拒絕")
    void createTransferForStatement_notApproved_throws() {
        transferService = newService();
        SettlementStatement pending = approvedStatement();
        pending.setStatus(SettlementStatus.PENDING);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> transferService.createTransferForStatement(STATEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));
    }

    @Test
    @DisplayName("createTransferForStatement: 已存在 transfer 記錄應直接回傳，不重複轉帳")
    void createTransferForStatement_idempotent_returnsExisting() {
        transferService = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(approvedStatement()));
        Transfer existing = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.COMPLETED).build();
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.of(existing));

        Transfer result = transferService.createTransferForStatement(STATEMENT_ID);

        assertThat(result).isSameAs(existing);
        verify(paymentGatewayFactory, org.mockito.Mockito.never())
                .createTransfer(anyString(), anyString(), any(Long.class), anyString(), anyString());
    }

    @Test
    @DisplayName("createTransferForStatement: STRIPE_TRANSFER_ENABLED 關閉應記錄 SKIPPED，不呼叫 Stripe")
    void createTransferForStatement_toggleDisabled_skipsTransfer() {
        transferService = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(approvedStatement()));
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(readyTenant()));
        when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, "STRIPE_TRANSFER_ENABLED")).thenReturn(false);
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = transferService.createTransferForStatement(STATEMENT_ID);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.SKIPPED_ONBOARDING_INCOMPLETE);
        verify(paymentGatewayFactory, org.mockito.Mockito.never())
                .createTransfer(anyString(), anyString(), any(Long.class), anyString(), anyString());
        verify(settlementRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("createTransferForStatement: Connect 帳戶未就緒應記錄 SKIPPED，結算單維持 APPROVED")
    void createTransferForStatement_connectNotReady_skipsTransfer() {
        transferService = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(approvedStatement()));
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.empty());
        Tenant notReady = Tenant.builder().id(TENANT_ID)
                .connectOnboardingStatus(Tenant.ConnectOnboardingStatus.PENDING)
                .build();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(notReady));
        when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, "STRIPE_TRANSFER_ENABLED")).thenReturn(true);
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = transferService.createTransferForStatement(STATEMENT_ID);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.SKIPPED_ONBOARDING_INCOMPLETE);
        verify(settlementRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("createTransferForStatement: Stripe 成功應轉 COMPLETED，結算單推進至 PAID")
    void createTransferForStatement_success_completesAndMarksPaid() {
        transferService = newService();
        SettlementStatement statement = approvedStatement();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(readyTenant()));
        when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, "STRIPE_TRANSFER_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.createTransfer(eq("STRIPE"), eq("acct_test_1"), eq(900000L), eq("TWD"), eq(STATEMENT_ID.toString())))
                .thenReturn(PaymentGatewayRequestResponse.TransferResult.builder().transferId("tr_test_1").build());
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = transferService.createTransferForStatement(STATEMENT_ID);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.getStripeTransferId()).isEqualTo("tr_test_1");
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(statement.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("createTransferForStatement: Stripe 失敗應轉 FAILED，結算單轉 FAILED，不拋例外")
    void createTransferForStatement_stripeFails_marksFailedWithoutThrowing() {
        transferService = newService();
        SettlementStatement statement = approvedStatement();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(readyTenant()));
        when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, "STRIPE_TRANSFER_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.createTransfer(anyString(), anyString(), any(Long.class), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.E_6010, "No such destination account"));
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = transferService.createTransferForStatement(STATEMENT_ID);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.getFailureReason()).contains("No such destination account");
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    @DisplayName("🔴 createTransferForStatement: 併發搶佔（saveAndFlush 唯一索引衝突）→ E_5014 乾淨失敗，絕不呼叫 Stripe、不重查資料庫、不寫入（DEF-441）")
    void createTransferForStatement_concurrentClaim_failsWithE5014WithoutCallingStripe() {
        transferService = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(approvedStatement()));
        // 第一次查詢（existing 檢查）回空，模擬本交易先通過檢查；saveAndFlush 才因為
        // 另一個交易已搶先提交同一 statementId 而違反唯一索引。
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(readyTenant()));
        when(transferRepository.saveAndFlush(any(Transfer.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> transferService.createTransferForStatement(STATEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        verify(paymentGatewayFactory, org.mockito.Mockito.never())
                .createTransfer(anyString(), anyString(), any(Long.class), anyString(), anyString());
        verify(settlementRepository, org.mockito.Mockito.never()).save(any());
        // 交易已中止（PostgreSQL）：違反之後不可再用同一個交易查詢（原本改查贏家的記錄，必然失敗）；
        // existing 檢查那一次是違反「之前」，所以恰好一次
        verify(transferRepository, org.mockito.Mockito.times(1)).findBySettlementStatementId(STATEMENT_ID);
    }

    @Test
    @DisplayName("🔴 retryFailedTransfer: 跨租戶存取應被拒絕（非 SUPER_ADMIN）")
    void retryFailedTransfer_crossTenantAccess_denied() {
        transferService = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(approvedStatement()));

        UUID otherTenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(otherTenantId);

        assertThatThrownBy(() -> transferService.retryFailedTransfer(STATEMENT_ID, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_1007));
    }

    @Test
    @DisplayName("retryFailedTransfer: SUPER_ADMIN 可跨租戶重試（略過租戶檢查）")
    @SuppressWarnings("unchecked")
    void retryFailedTransfer_superAdmin_bypassesTenantCheck() {
        transferService = newService();
        SettlementStatement statement = approvedStatement();
        statement.setStatus(SettlementStatus.FAILED);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        Transfer failedTransfer = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.FAILED).build();
        // 第一次呼叫（retryFailedTransfer 本身的既有記錄檢查）回既有 FAILED 記錄；
        // delete 後第二次呼叫（createTransferForStatement 內的冪等檢查）應回空，模擬記錄已被清除。
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID))
                .thenReturn(Optional.of(failedTransfer), Optional.empty());
        // Sprint 323（DEF-447）：舊記錄改用條件式刪除搶占，回傳 1＝本次搶到
        when(transferRepository.deleteByIdAndStatusIn(eq(failedTransfer.getId()), any())).thenReturn(1);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(readyTenant()));
        when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, "STRIPE_TRANSFER_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.createTransfer(anyString(), anyString(), any(Long.class), anyString(), anyString()))
                .thenReturn(PaymentGatewayRequestResponse.TransferResult.builder().transferId("tr_retry_1").build());
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID otherTenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(otherTenantId);

        Transfer result = transferService.retryFailedTransfer(STATEMENT_ID, true);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.getStripeTransferId()).isEqualTo("tr_retry_1");
    }

    @Test
    @DisplayName("retryFailedTransfer: 非 FAILED/SKIPPED 狀態應拒絕重試")
    void retryFailedTransfer_notFailedStatus_throws() {
        transferService = newService();
        SettlementStatement statement = approvedStatement();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        Transfer completed = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.COMPLETED).build();
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.of(completed));
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> transferService.retryFailedTransfer(STATEMENT_ID, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        // 前置狀態檢查先擋下，連搶占（條件式刪除）都不嘗試——不可動到已完成的 transfer
        verify(transferRepository, org.mockito.Mockito.never()).deleteByIdAndStatusIn(any(), any());
    }

    // ── Sprint 323 DEF-447：重試對舊 transfer 記錄的條件式刪除搶占 ────────────────────────────
    //
    // 真實競態（兩位管理員同時重試）由 SettlementPostApprovalRaceRealStackIntegrationTest 守住；這裡守 mock 看得到的部分：
    // 搶輸時乾淨失敗（不碰結算單、不走到閘道）、SKIPPED 的 transfer 也可重試、搶占發生在寫入結算單之前。

    /** 重試一筆既有的 transfer，走到『撥款開關關閉 → SKIPPED』的最短路徑（不碰閘道）。 */
    @SuppressWarnings("unchecked")
    private SettlementStatement arrangeRetryOf(final Transfer existing, final SettlementStatus statementStatus) {
        SettlementStatement statement = approvedStatement();
        statement.setStatus(statementStatus);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID))
                .thenReturn(Optional.of(existing), Optional.empty());
        when(transferRepository.deleteByIdAndStatusIn(eq(existing.getId()), any())).thenReturn(1);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(readyTenant()));
        when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, "STRIPE_TRANSFER_ENABLED")).thenReturn(false);
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.setCurrentTenant(TENANT_ID);
        return statement;
    }

    @Test
    @DisplayName("🔴 DEF-447 retryFailedTransfer: 舊 transfer 記錄已被另一個併發的重試搶先刪除（條件式刪除回 0）→ E-5014，不碰結算單、不走到閘道、不建立新 transfer")
    void retryFailedTransfer_concurrentRetryClaimedTheTransfer_throwsE5014WithoutSideEffects() {
        transferService = newService();
        SettlementStatement statement = approvedStatement();
        statement.setStatus(SettlementStatus.FAILED);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        Transfer failedTransfer = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.FAILED).build();
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.of(failedTransfer));
        when(transferRepository.deleteByIdAndStatusIn(eq(failedTransfer.getId()), any())).thenReturn(0);
        TenantContext.setCurrentTenant(TENANT_ID);

        assertThatThrownBy(() -> transferService.retryFailedTransfer(STATEMENT_ID, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        assertThat(statement.getStatus()).as("搶輸之前不可動到結算單的狀態").isEqualTo(SettlementStatus.FAILED);
        verify(settlementRepository, org.mockito.Mockito.never()).save(any());
        verify(transferRepository, org.mockito.Mockito.never()).saveAndFlush(any());
        verify(paymentGatewayFactory, org.mockito.Mockito.never())
                .createTransfer(anyString(), anyString(), any(Long.class), anyString(), anyString());
    }

    @Test
    @DisplayName("DEF-447 retryFailedTransfer: SKIPPED 的 transfer 也可重試——條件式刪除涵蓋 FAILED 與 SKIPPED_ONBOARDING_INCOMPLETE 兩種狀態")
    @SuppressWarnings("unchecked")
    void retryFailedTransfer_skippedTransfer_isRetryable_claimCoversBothRetryableStatuses() {
        transferService = newService();
        Transfer skipped = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.SKIPPED_ONBOARDING_INCOMPLETE).build();
        arrangeRetryOf(skipped, SettlementStatus.APPROVED);

        Transfer result = transferService.retryFailedTransfer(STATEMENT_ID, false);

        ArgumentCaptor<Collection<TransferStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(transferRepository).deleteByIdAndStatusIn(eq(skipped.getId()), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                TransferStatus.FAILED, TransferStatus.SKIPPED_ONBOARDING_INCOMPLETE);
        assertThat(result.getStatus()).isEqualTo(TransferStatus.SKIPPED_ONBOARDING_INCOMPLETE);
    }

    @Test
    @DisplayName("DEF-447 retryFailedTransfer: 先搶占舊 transfer（條件式刪除）、再寫結算單——輸家不可在搶占之前改動結算單")
    void retryFailedTransfer_claimsTheOldTransferBeforeWritingTheStatement() {
        transferService = newService();
        Transfer failed = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.FAILED).build();
        SettlementStatement statement = arrangeRetryOf(failed, SettlementStatus.FAILED);

        transferService.retryFailedTransfer(STATEMENT_ID, false);

        InOrder order = org.mockito.Mockito.inOrder(transferRepository, settlementRepository);
        order.verify(transferRepository).deleteByIdAndStatusIn(eq(failed.getId()), any());
        order.verify(settlementRepository).save(statement);
        assertThat(statement.getStatus()).as("重試把結算單退回 APPROVED 重新判斷前置條件").isEqualTo(SettlementStatus.APPROVED);
    }

    // ── Sprint 321 DEF-443：核准提交之後觸發撥款；非預期失敗後（APPROVED、沒有 transfer）可補撥款 ────────

    @Test
    @DisplayName("🔴 DEF-443 createTransferAfterApproval: 必須 REQUIRES_NEW——在 afterCommit 回呼裡用 REQUIRED 會加入已完成的核准交易，撥款的寫入永遠不會被提交")
    void createTransferAfterApproval_runsInItsOwnNewTransaction() throws NoSuchMethodException {
        Transactional transactional = TransferService.class
                .getMethod("createTransferAfterApproval", UUID.class).getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("DEF-443 createTransferAfterApproval: 實際做事的仍是 createTransferForStatement（冪等：已有記錄直接回傳）")
    void createTransferAfterApproval_delegatesToCreateTransferForStatement() {
        transferService = newService();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(approvedStatement()));
        Transfer existing = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.COMPLETED).build();
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.of(existing));

        assertThat(transferService.createTransferAfterApproval(STATEMENT_ID)).isSameAs(existing);
    }

    @Test
    @DisplayName("🔴 DEF-443 retryFailedTransfer: 已核准（APPROVED）但完全沒有 transfer 記錄（撥款觸發本身失敗）→ 直接觸發撥款，沒有舊記錄可刪")
    void retryFailedTransfer_approvedWithoutAnyTransfer_triggersThePayout() {
        transferService = newService();
        SettlementStatement statement = approvedStatement();
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(readyTenant()));
        when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, "STRIPE_TRANSFER_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.createTransfer(anyString(), anyString(), any(Long.class), anyString(), anyString()))
                .thenReturn(PaymentGatewayRequestResponse.TransferResult.builder().transferId("tr_after_failure").build());
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = transferService.retryFailedTransfer(STATEMENT_ID, true);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.getStripeTransferId()).isEqualTo("tr_after_failure");
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.PAID);
        verify(transferRepository, org.mockito.Mockito.never()).delete(any(Transfer.class));
    }

    @Test
    @DisplayName("🔴 DEF-443 retryFailedTransfer: 沒有 transfer、結算單也不是 APPROVED（尚未核准／已駁回…）→ E-5014，絕不撥款（沒有 transfer 不等於撥款失敗）")
    void retryFailedTransfer_noTransferAndNotApproved_throwsWithoutPayout() {
        transferService = newService();
        SettlementStatement statement = approvedStatement();
        statement.setStatus(SettlementStatus.PENDING_REVIEW);
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(transferRepository.findBySettlementStatementId(STATEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.retryFailedTransfer(STATEMENT_ID, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_5014));

        verify(paymentGatewayFactory, org.mockito.Mockito.never())
                .createTransfer(anyString(), anyString(), any(Long.class), anyString(), anyString());
        verify(transferRepository, org.mockito.Mockito.never()).saveAndFlush(any(Transfer.class));
    }

    // ── Sprint 323 DEF-447：webhook 改成條件式更新（PAID → FAILED），不再讀實體後整列寫回 ─────────────
    //
    // 原本的單元測試斷言「記憶體裡的 statement.getStatus() 變成 FAILED」——那個斷言在缺陷存在時照樣全綠（整列寫回正是缺陷本身）。
    // 真實競態（與 initiateReversal 交錯）由 SettlementPostApprovalRaceRealStackIntegrationTest 守住；這裡守 mock 看得到的部分：
    // 委派給條件式更新、搶不到時不覆寫、完全不載入也不 save 結算單實體。

    @Test
    @DisplayName("handleTransferReversedWebhook: 查有記錄 → Transfer 轉 REVERSED，結算單以條件式更新（PAID → FAILED）轉 FAILED")
    void handleTransferReversedWebhook_existingTransfer_marksReversedAndFailsStatement() {
        transferService = newService();
        Transfer completed = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.COMPLETED).stripeTransferId("tr_rev_1").build();
        when(transferRepository.findByStripeTransferId("tr_rev_1")).thenReturn(Optional.of(completed));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID, SettlementStatus.PAID, SettlementStatus.FAILED))
                .thenReturn(1);

        transferService.handleTransferReversedWebhook("tr_rev_1");

        assertThat(completed.getStatus()).isEqualTo(TransferStatus.REVERSED);
        verify(settlementRepository).updateStatusIfCurrent(STATEMENT_ID, SettlementStatus.PAID, SettlementStatus.FAILED);
        // 不載入結算單實體、不 save：整列寫回會用讀取當下的舊值蓋掉同時提交的逆轉發起（含四個逆轉欄位）
        verify(settlementRepository, org.mockito.Mockito.never()).findById(any());
        verify(settlementRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("🔴 handleTransferReversedWebhook: 結算單已在人工雙重授權逆轉流程中（REVERSAL_PENDING）"
            + "——條件式更新搶不到，不可覆寫回 FAILED；transfer 仍記為 REVERSED")
    void handleTransferReversedWebhook_statementInReversalPending_doesNotOverrideStatus() {
        transferService = newService();
        Transfer completed = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.COMPLETED).stripeTransferId("tr_rev_2").build();
        when(transferRepository.findByStripeTransferId("tr_rev_2")).thenReturn(Optional.of(completed));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID, SettlementStatus.PAID, SettlementStatus.FAILED))
                .thenReturn(0);
        when(settlementRepository.findStatusById(STATEMENT_ID)).thenReturn(Optional.of(SettlementStatus.REVERSAL_PENDING));

        transferService.handleTransferReversedWebhook("tr_rev_2");

        assertThat(completed.getStatus()).isEqualTo(TransferStatus.REVERSED);
        verify(settlementRepository).updateStatusIfCurrent(STATEMENT_ID, SettlementStatus.PAID, SettlementStatus.FAILED);
        verify(settlementRepository, org.mockito.Mockito.never()).findById(any());
        verify(settlementRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("handleTransferReversedWebhook: 結算單不存在（條件式更新 0 筆、重讀也沒有）→ 不拋例外（Stripe 不該因此無限重送），transfer 仍記為 REVERSED")
    void handleTransferReversedWebhook_statementNotFound_doesNotThrow() {
        transferService = newService();
        Transfer completed = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.COMPLETED).stripeTransferId("tr_rev_3").build();
        when(transferRepository.findByStripeTransferId("tr_rev_3")).thenReturn(Optional.of(completed));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.updateStatusIfCurrent(STATEMENT_ID, SettlementStatus.PAID, SettlementStatus.FAILED))
                .thenReturn(0);
        when(settlementRepository.findStatusById(STATEMENT_ID)).thenReturn(Optional.empty());

        transferService.handleTransferReversedWebhook("tr_rev_3");

        assertThat(completed.getStatus()).isEqualTo(TransferStatus.REVERSED);
        verify(settlementRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("handleTransferReversedWebhook: 查無記錄 → 不拋例外，不呼叫 save，也不碰結算單")
    void handleTransferReversedWebhook_noMatchingTransfer_doesNothing() {
        transferService = newService();
        when(transferRepository.findByStripeTransferId("tr_unknown")).thenReturn(Optional.empty());

        transferService.handleTransferReversedWebhook("tr_unknown");

        verify(transferRepository, org.mockito.Mockito.never()).save(any());
        verify(settlementRepository, org.mockito.Mockito.never()).save(any());
        verify(settlementRepository, org.mockito.Mockito.never()).updateStatusIfCurrent(any(), any(), any());
    }

    @Test
    @DisplayName("getTransfersForCurrentTenant: 依 TenantContext 範圍化查詢，不可外洩他租戶參數")
    void getTransfersForCurrentTenant_scopesToCurrentTenant() {
        transferService = newService();
        TenantContext.setCurrentTenant(TENANT_ID);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(transferRepository.findByTenantIdOrderByCreatedAtDesc(TENANT_ID, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        transferService.getTransfersForCurrentTenant(pageable);

        verify(transferRepository).findByTenantIdOrderByCreatedAtDesc(TENANT_ID, pageable);
    }

    @Test
    @DisplayName("getTransfersForCurrentTenant: 沒有店鋪的呼叫者（系統租戶）→ 空頁，完全不查詢（Sprint 234）")
    void getTransfersForCurrentTenant_systemTenant_returnsEmptyWithoutQuerying() {
        transferService = newService();
        TenantContext.setCurrentTenant(UUID.fromString(com.nextkey.ecommerce.shared.constants.AppConstants.SYSTEM_TENANT_ID));
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);

        org.springframework.data.domain.Page<Transfer> page = transferService.getTransfersForCurrentTenant(pageable);

        assertThat(page.getContent()).isEmpty();
        verify(transferRepository, org.mockito.Mockito.never()).findByTenantIdOrderByCreatedAtDesc(any(), any());
    }
}
