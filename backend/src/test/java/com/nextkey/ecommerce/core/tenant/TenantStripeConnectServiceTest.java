package com.nextkey.ecommerce.core.tenant;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.nextkey.ecommerce.api.dto.StripeConnectDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantStripeConnectService 單元測試（Sprint 53 AI-2413 Phase D-1）。
 *
 * 驗證：
 * - 首次 onboarding：建立 Connect Express 帳戶 + 存回 tenant + 產生 account link
 * - 重複呼叫：複用既有 accountId，只重新產生 account link（不重建帳戶）
 * - 狀態查詢：回填 charges/payouts_enabled；details_submitted 皆真 → COMPLETE
 * - toggle 關閉時拒絕（E_2004）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenantStripeConnectService（AI-2413 Phase D-1）")
class TenantStripeConnectServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private FeatureToggleService featureToggleService;
    @Mock private PaymentGatewayFactory paymentGatewayFactory;
    @Mock private AuditService auditService;

    private TenantStripeConnectService service;

    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        service = new TenantStripeConnectService(tenantRepository, featureToggleService, paymentGatewayFactory, auditService);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:3000");
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder()
                .name("Test Seller")
                .slug("test-seller")
                .contactEmail("seller@example.com")
                .connectOnboardingStatus(Tenant.ConnectOnboardingStatus.NOT_STARTED)
                .connectChargesEnabled(false)
                .connectPayoutsEnabled(false)
                .build();
        tenant.setId(TENANT_ID);
        return tenant;
    }

    @Test
    @DisplayName("UT-CONNECT-001: 首次 onboarding — 建帳戶 + 存 accountId + 回傳 onboarding URL")
    void initiateOnboarding_firstTime_createsAccountAndReturnsUrl() {
        Tenant tenant = newTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(paymentGatewayFactory.createConnectAccount(eq("STRIPE"), eq("seller@example.com")))
                .thenReturn(PaymentGatewayRequestResponse.ConnectAccountResult.builder()
                        .accountId("acct_new_1").chargesEnabled(false).payoutsEnabled(false)
                        .detailsSubmitted(false).build());
        when(paymentGatewayFactory.createAccountLink(eq("STRIPE"), eq("acct_new_1"), anyString(), anyString()))
                .thenReturn(PaymentGatewayRequestResponse.AccountLinkResult.builder()
                        .url("https://connect.stripe.com/setup/e/acct_new_1/onboarding").build());

        StripeConnectDto.OnboardingResponse response = service.initiateOnboarding(TENANT_ID);

        assertThat(response.getOnboardingUrl())
                .isEqualTo("https://connect.stripe.com/setup/e/acct_new_1/onboarding");
        assertThat(tenant.getStripeConnectAccountId()).isEqualTo("acct_new_1");
        assertThat(tenant.getConnectOnboardingStatus()).isEqualTo(Tenant.ConnectOnboardingStatus.PENDING);
        verify(tenantRepository).save(tenant);
        // Sprint 135（DEF-112）：帳戶開通須記錄稽核
        verify(auditService).record(eq("CONNECT_ONBOARDING_INITIATED"), eq("TENANT"), eq(TENANT_ID), eq(TENANT_ID),
                eq("NOT_STARTED"), eq("PENDING"), any(), isNull());
    }

    @Test
    @DisplayName("UT-CONNECT-002: 重複呼叫 onboarding — 複用既有 accountId，不重建帳戶")
    void initiateOnboarding_alreadyHasAccount_reusesAccountId() {
        Tenant tenant = newTenant();
        tenant.setStripeConnectAccountId("acct_existing_1");
        tenant.setConnectOnboardingStatus(Tenant.ConnectOnboardingStatus.PENDING);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(paymentGatewayFactory.createAccountLink(eq("STRIPE"), eq("acct_existing_1"), anyString(), anyString()))
                .thenReturn(PaymentGatewayRequestResponse.AccountLinkResult.builder()
                        .url("https://connect.stripe.com/setup/e/acct_existing_1/onboarding").build());

        StripeConnectDto.OnboardingResponse response = service.initiateOnboarding(TENANT_ID);

        assertThat(response.getOnboardingUrl())
                .isEqualTo("https://connect.stripe.com/setup/e/acct_existing_1/onboarding");
        verify(paymentGatewayFactory, never()).createConnectAccount(anyString(), anyString());
    }

    @Test
    @DisplayName("UT-CONNECT-003: 狀態查詢 — details_submitted+charges+payouts 皆真 → COMPLETE")
    void getAccountStatus_allEnabled_transitionsToComplete() {
        Tenant tenant = newTenant();
        tenant.setStripeConnectAccountId("acct_1");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(paymentGatewayFactory.getConnectAccountStatus(eq("STRIPE"), eq("acct_1")))
                .thenReturn(PaymentGatewayRequestResponse.ConnectAccountResult.builder()
                        .accountId("acct_1").chargesEnabled(true).payoutsEnabled(true)
                        .detailsSubmitted(true).build());

        StripeConnectDto.StatusResponse response = service.getAccountStatus(TENANT_ID);

        assertThat(response.isChargesEnabled()).isTrue();
        assertThat(response.isPayoutsEnabled()).isTrue();
        assertThat(response.getOnboardingStatus()).isEqualTo("COMPLETE");
        assertThat(tenant.getConnectOnboardingStatus()).isEqualTo(Tenant.ConnectOnboardingStatus.COMPLETE);
        // Sprint 135（DEF-112）：狀態同步（含手動查詢）須記錄稽核
        verify(auditService).record(eq("CONNECT_STATUS_SYNCED"), eq("TENANT"), eq(TENANT_ID), eq(TENANT_ID),
                eq("NOT_STARTED"), eq("COMPLETE"), any(), isNull());
    }

    @Test
    @DisplayName("UT-CONNECT-007: webhook 同步帳戶狀態 — 找到 tenant 時回填並記錄稽核（actor=null，webhook 無使用者情境）")
    void syncAccountStatusFromWebhook_tenantFound_updatesAndRecordsAudit() {
        Tenant tenant = newTenant();
        tenant.setStripeConnectAccountId("acct_1");
        when(tenantRepository.findByStripeConnectAccountId("acct_1")).thenReturn(Optional.of(tenant));

        boolean result = service.syncAccountStatusFromWebhook("acct_1", true, true, true);

        assertThat(result).isTrue();
        assertThat(tenant.getConnectOnboardingStatus()).isEqualTo(Tenant.ConnectOnboardingStatus.COMPLETE);
        verify(tenantRepository).save(tenant);
        verify(auditService).record(eq("CONNECT_STATUS_SYNCED"), eq("TENANT"), eq(TENANT_ID), eq(TENANT_ID),
                eq("NOT_STARTED"), eq("COMPLETE"), any(), isNull());
    }

    @Test
    @DisplayName("UT-CONNECT-008: webhook 同步帳戶狀態 — 找不到對應 tenant 時 no-op，不記錄稽核")
    void syncAccountStatusFromWebhook_tenantNotFound_noOpWithoutAudit() {
        when(tenantRepository.findByStripeConnectAccountId("acct_unknown")).thenReturn(Optional.empty());

        boolean result = service.syncAccountStatusFromWebhook("acct_unknown", true, true, true);

        assertThat(result).isFalse();
        verify(tenantRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("UT-CONNECT-004: 狀態查詢 — 尚未 onboarding（無 accountId）→ NOT_STARTED，不呼叫 Stripe")
    void getAccountStatus_noAccountYet_returnsNotStarted() {
        Tenant tenant = newTenant();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

        StripeConnectDto.StatusResponse response = service.getAccountStatus(TENANT_ID);

        assertThat(response.getOnboardingStatus()).isEqualTo("NOT_STARTED");
        assertThat(response.isChargesEnabled()).isFalse();
        verify(paymentGatewayFactory, never()).getConnectAccountStatus(anyString(), anyString());
    }

    @Test
    @DisplayName("UT-CONNECT-005: toggle 關閉 — 拒絕 onboarding（E_2004）")
    void initiateOnboarding_toggleDisabled_throwsBusinessException() {
        doThrow(new BusinessException(ErrorCode.E_2004, "Feature 'STRIPE_CONNECT_ENABLED' is disabled"))
                .when(featureToggleService).checkFeatureEnabled("STRIPE_CONNECT_ENABLED");

        assertThatThrownBy(() -> service.initiateOnboarding(TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_2004));

        verify(tenantRepository, never()).findById(any());
    }

    @Test
    @DisplayName("UT-CONNECT-006: tenant 不存在 — E_2000")
    void initiateOnboarding_tenantNotFound_throwsBusinessException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateOnboarding(TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.E_2000));
    }
}
