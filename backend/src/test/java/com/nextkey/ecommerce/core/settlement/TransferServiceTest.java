package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = transferService.createTransferForStatement(STATEMENT_ID);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.getFailureReason()).contains("No such destination account");
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.FAILED);
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
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(readyTenant()));
        when(featureToggleService.isFeatureEnabledForTenant(TENANT_ID, "STRIPE_TRANSFER_ENABLED")).thenReturn(true);
        when(paymentGatewayFactory.createTransfer(anyString(), anyString(), any(Long.class), anyString(), anyString()))
                .thenReturn(PaymentGatewayRequestResponse.TransferResult.builder().transferId("tr_retry_1").build());
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
    }

    @Test
    @DisplayName("handleTransferReversedWebhook: 查有記錄 → Transfer 轉 REVERSED，結算單轉 FAILED")
    void handleTransferReversedWebhook_existingTransfer_marksReversedAndFailsStatement() {
        transferService = newService();
        SettlementStatement statement = approvedStatement();
        statement.setStatus(SettlementStatus.PAID);
        Transfer completed = Transfer.builder().id(UUID.randomUUID()).settlementStatementId(STATEMENT_ID)
                .status(TransferStatus.COMPLETED).stripeTransferId("tr_rev_1").build();
        when(transferRepository.findByStripeTransferId("tr_rev_1")).thenReturn(Optional.of(completed));
        when(settlementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> inv.getArgument(0));

        transferService.handleTransferReversedWebhook("tr_rev_1");

        assertThat(completed.getStatus()).isEqualTo(TransferStatus.REVERSED);
        assertThat(statement.getStatus()).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    @DisplayName("handleTransferReversedWebhook: 查無記錄 → 不拋例外，不呼叫 save")
    void handleTransferReversedWebhook_noMatchingTransfer_doesNothing() {
        transferService = newService();
        when(transferRepository.findByStripeTransferId("tr_unknown")).thenReturn(Optional.empty());

        transferService.handleTransferReversedWebhook("tr_unknown");

        verify(transferRepository, org.mockito.Mockito.never()).save(any());
        verify(settlementRepository, org.mockito.Mockito.never()).save(any());
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
}
