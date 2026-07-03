package com.nextkey.ecommerce.core.tenant;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.StripeConnectDto;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tenant Stripe Connect Express 帳戶 onboarding（Sprint 53 AI-2413 Phase D-1）。
 * 本 Sprint 只做「帳戶開通」：建 Connect Express 帳戶 + 產生 onboarding link + 查詢/同步帳戶狀態。
 * 代收後 transfer 分潤（Phase D-2）不在本服務範圍。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantStripeConnectService {

    private static final String STRIPE_CONNECT_ENABLED = "STRIPE_CONNECT_ENABLED";
    private static final String GATEWAY_STRIPE = "STRIPE";

    private final TenantRepository tenantRepository;
    private final FeatureToggleService featureToggleService;
    private final PaymentGatewayFactory paymentGatewayFactory;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * 發起（或重新產生）onboarding link。
     * 首次呼叫：建立 Connect Express 帳戶並存回 tenant，狀態轉 PENDING。
     * 已有 accountId：複用既有帳戶、僅重新產生 account link（account link 為一次性、會逾期）。
     */
    @Transactional
    public StripeConnectDto.OnboardingResponse initiateOnboarding(UUID tenantId) {
        featureToggleService.checkFeatureEnabled(STRIPE_CONNECT_ENABLED);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        if (tenant.getStripeConnectAccountId() == null) {
            PaymentGatewayRequestResponse.ConnectAccountResult accountResult =
                    paymentGatewayFactory.createConnectAccount(GATEWAY_STRIPE, tenant.getContactEmail());
            tenant.setStripeConnectAccountId(accountResult.getAccountId());
            tenant.setConnectOnboardingStatus(Tenant.ConnectOnboardingStatus.PENDING);
            tenantRepository.save(tenant);
            log.info("Stripe Connect account created: tenantId={}, accountId={}",
                    tenantId, accountResult.getAccountId());
        }

        PaymentGatewayRequestResponse.AccountLinkResult linkResult = paymentGatewayFactory.createAccountLink(
                GATEWAY_STRIPE,
                tenant.getStripeConnectAccountId(),
                frontendBaseUrl + "/seller/stripe-connect/refresh",
                frontendBaseUrl + "/seller/stripe-connect/return");

        log.info("Stripe Connect onboarding link created: tenantId={}, accountId={}",
                tenantId, tenant.getStripeConnectAccountId());

        return StripeConnectDto.OnboardingResponse.builder()
                .onboardingUrl(linkResult.getUrl())
                .build();
    }

    /**
     * 查詢帳戶最新狀態（呼叫 Stripe 即時查詢並回填本地欄位）。
     * details_submitted + charges_enabled + payouts_enabled 皆為真 → 狀態轉 COMPLETE。
     */
    @Transactional
    public StripeConnectDto.StatusResponse getAccountStatus(UUID tenantId) {
        featureToggleService.checkFeatureEnabled(STRIPE_CONNECT_ENABLED);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        if (tenant.getStripeConnectAccountId() == null) {
            return StripeConnectDto.StatusResponse.builder()
                    .onboardingStatus(Tenant.ConnectOnboardingStatus.NOT_STARTED.name())
                    .chargesEnabled(false)
                    .payoutsEnabled(false)
                    .build();
        }

        PaymentGatewayRequestResponse.ConnectAccountResult result = paymentGatewayFactory
                .getConnectAccountStatus(GATEWAY_STRIPE, tenant.getStripeConnectAccountId());

        boolean chargesEnabled = Boolean.TRUE.equals(result.getChargesEnabled());
        boolean payoutsEnabled = Boolean.TRUE.equals(result.getPayoutsEnabled());
        boolean detailsSubmitted = Boolean.TRUE.equals(result.getDetailsSubmitted());

        tenant.setConnectChargesEnabled(chargesEnabled);
        tenant.setConnectPayoutsEnabled(payoutsEnabled);
        if (detailsSubmitted && chargesEnabled && payoutsEnabled) {
            tenant.setConnectOnboardingStatus(Tenant.ConnectOnboardingStatus.COMPLETE);
        }
        tenantRepository.save(tenant);

        return StripeConnectDto.StatusResponse.builder()
                .accountId(tenant.getStripeConnectAccountId())
                .onboardingStatus(tenant.getConnectOnboardingStatus().name())
                .chargesEnabled(chargesEnabled)
                .payoutsEnabled(payoutsEnabled)
                .build();
    }

    /**
     * 同步 Connect 帳戶狀態（webhook account.updated 權威，Sprint 53 AI-2413 Phase D-1 US-002）：
     * 依 accountId 反查 tenant，回填 charges/payouts_enabled；找不到對應 tenant 則 no-op（可能是非本平台帳戶）。
     */
    @Transactional
    public boolean syncAccountStatusFromWebhook(
            String accountId, boolean chargesEnabled, boolean payoutsEnabled, boolean detailsSubmitted) {
        Tenant tenant = tenantRepository.findByStripeConnectAccountId(accountId).orElse(null);
        if (tenant == null) {
            log.warn("Stripe Connect webhook: tenant not found for accountId={}", accountId);
            return false;
        }
        tenant.setConnectChargesEnabled(chargesEnabled);
        tenant.setConnectPayoutsEnabled(payoutsEnabled);
        if (detailsSubmitted && chargesEnabled && payoutsEnabled) {
            tenant.setConnectOnboardingStatus(Tenant.ConnectOnboardingStatus.COMPLETE);
        }
        tenantRepository.save(tenant);
        log.info("Stripe Connect account status synced (webhook): accountId={}, tenantId={}",
                accountId, tenant.getId());
        return true;
    }
}
