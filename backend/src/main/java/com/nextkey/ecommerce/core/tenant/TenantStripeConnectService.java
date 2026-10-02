package com.nextkey.ecommerce.core.tenant;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.StripeConnectDto;
import com.nextkey.ecommerce.core.audit.AuditService;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

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
    private final TenantMemberRepository tenantMemberRepository;
    private final FeatureToggleService featureToggleService;
    private final PaymentGatewayFactory paymentGatewayFactory;
    private final AuditService auditService;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * 發起（或重新產生）onboarding link。
     * 首次呼叫：建立 Connect Express 帳戶並存回 tenant，狀態轉 PENDING。
     * 已有 accountId：複用既有帳戶、僅重新產生 account link（account link 為一次性、會逾期）。
     */
    @Transactional
    public StripeConnectDto.OnboardingResponse initiateOnboarding(UUID tenantId) {
        requireStoreOwner(tenantId);
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
            auditService.record("CONNECT_ONBOARDING_INITIATED", "TENANT", tenantId, tenantId,
                    "NOT_STARTED", Tenant.ConnectOnboardingStatus.PENDING.name(),
                    "Stripe Connect accountId=" + accountResult.getAccountId(), TenantContext.getCurrentUser());
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
        requireStoreOwner(tenantId);
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

        String oldStatus = tenant.getConnectOnboardingStatus().name();
        tenant.setConnectChargesEnabled(chargesEnabled);
        tenant.setConnectPayoutsEnabled(payoutsEnabled);
        if (detailsSubmitted && chargesEnabled && payoutsEnabled) {
            tenant.setConnectOnboardingStatus(Tenant.ConnectOnboardingStatus.COMPLETE);
        }
        tenantRepository.save(tenant);
        auditService.record("CONNECT_STATUS_SYNCED", "TENANT", tenantId, tenantId,
                oldStatus, tenant.getConnectOnboardingStatus().name(),
                "manual sync (getAccountStatus), chargesEnabled=" + chargesEnabled + ", payoutsEnabled=" + payoutsEnabled,
                TenantContext.getCurrentUser());

        return StripeConnectDto.StatusResponse.builder()
                .accountId(tenant.getStripeConnectAccountId())
                .onboardingStatus(tenant.getConnectOnboardingStatus().name())
                .chargesEnabled(chargesEnabled)
                .payoutsEnabled(payoutsEnabled)
                .build();
    }

    /**
     * Stripe Connect 的開通與狀態查詢只限「這間店鋪的店主」（Sprint 235，DEF-327）。
     *
     * <p>原本兩個端點只要求 {@code hasRole('SELLER')}（自助註冊即得、不建租戶），而 {@link #initiateOnboarding}
     * 對任何租戶都會建立 Connect Express 帳戶並回傳一次性 onboarding 連結——沒有店鋪的 SELLER 呼叫時租戶是系統租戶，
     * 帳戶會建在系統租戶上；持連結者完成 KYC 後綁自己的銀行帳戶，系統租戶結算單被核准時撥款就會撥給他
     * （推論，未驗 Stripe 端；前置條件是系統租戶的 {@code STRIPE_CONNECT_ENABLED} 被開啟）。
     * 所以這裡要求：租戶是真正的店鋪租戶（{@link TenantContext#isStoreTenant}），且呼叫者在資料庫裡是該店鋪的店主。
     * 放在 service 層而不只靠 controller 的角色檢查，日後任何新的呼叫路徑也都被涵蓋。
     */
    private void requireStoreOwner(final UUID tenantId) {
        UUID userId = TenantContext.getCurrentUser();
        if (!TenantContext.isStoreTenant(tenantId) || userId == null
                || !tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(
                        tenantId, userId, TenantMember.StoreRole.STORE_OWNER)) {
            throw new BusinessException(ErrorCode.E_1007, "Only the store owner can manage Stripe Connect");
        }
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
        String oldStatus = tenant.getConnectOnboardingStatus().name();
        tenant.setConnectChargesEnabled(chargesEnabled);
        tenant.setConnectPayoutsEnabled(payoutsEnabled);
        if (detailsSubmitted && chargesEnabled && payoutsEnabled) {
            tenant.setConnectOnboardingStatus(Tenant.ConnectOnboardingStatus.COMPLETE);
        }
        tenantRepository.save(tenant);
        log.info("Stripe Connect account status synced (webhook): accountId={}, tenantId={}",
                accountId, tenant.getId());
        auditService.record("CONNECT_STATUS_SYNCED", "TENANT", tenant.getId(), tenant.getId(),
                oldStatus, tenant.getConnectOnboardingStatus().name(),
                "webhook (account.updated), chargesEnabled=" + chargesEnabled + ", payoutsEnabled=" + payoutsEnabled,
                null);
        return true;
    }
}
