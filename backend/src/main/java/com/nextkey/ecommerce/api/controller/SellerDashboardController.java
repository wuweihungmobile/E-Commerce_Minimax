package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.SellerDashboardDto;
import com.nextkey.ecommerce.api.dto.StripeConnectDto;
import com.nextkey.ecommerce.core.seller.SellerDashboardService;
import com.nextkey.ecommerce.core.tenant.TenantStripeConnectService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v2/seller/dashboard")
@RequiredArgsConstructor
public class SellerDashboardController {

    private final SellerDashboardService sellerDashboardService;
    private final TenantStripeConnectService tenantStripeConnectService;

    /**
     * 賣家儀表板（近期訂單數、營收、待處理訂單）。
     *
     * <p>Sprint 240（DEF-326）：原本只有 {@code hasRole('SELLER')}——自助註冊即得、不建租戶，而核准開店後的角色是
     * STORE_OWNER，所以真正的店主反而呼叫不了，只有沒有店鋪的 SELLER 能呼叫並讀到系統租戶（所有一般消費者訂單）的統計。
     * 現在 STORE_OWNER 可以呼叫；沒有店鋪的 SELLER／HOST 簽發時已是買家（{@code AuthService}），這裡再擋一次系統租戶的呼叫者
     * ——換發前簽發、仍帶 SELLER 角色的舊 token 在有效期內也讀不到。
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('STORE_OWNER', 'SELLER')")
    public ResponseEntity<ApiResponse<SellerDashboardDto.DashboardResponse>> getDashboard() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (!TenantContext.isStoreTenant(tenantId)) {
            throw new BusinessException(ErrorCode.E_1007, "The seller dashboard is only available to a store");
        }
        log.info("Seller dashboard request: tenantId={}", tenantId);
        SellerDashboardDto.DashboardResponse response = sellerDashboardService.getDashboard(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 發起（或重新產生）Stripe Connect Express 帳戶 onboarding link（Sprint 53 AI-2413 Phase D-1）。
     *
     * <p>Sprint 235（DEF-327）：只限店主（{@code STORE_OWNER}）。原本是 {@code hasRole('SELLER')}——自助註冊即得、不建租戶，
     * 而核准開店後的角色是 STORE_OWNER，所以真正的店主反而呼叫不了，只有沒有店鋪的 SELLER 能呼叫（在系統租戶上建立
     * Connect 帳戶，見 {@link com.nextkey.ecommerce.core.tenant.TenantStripeConnectService}）。
     */
    @PostMapping("/stripe-connect/onboarding")
    @PreAuthorize("hasRole('STORE_OWNER')")
    public ResponseEntity<ApiResponse<StripeConnectDto.OnboardingResponse>> initiateStripeConnectOnboarding() {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.info("Stripe Connect onboarding request: tenantId={}", tenantId);
        StripeConnectDto.OnboardingResponse response = tenantStripeConnectService.initiateOnboarding(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 查詢 Stripe Connect 帳戶最新狀態（Sprint 53 AI-2413 Phase D-1）。
     */
    @GetMapping("/stripe-connect/status")
    @PreAuthorize("hasRole('STORE_OWNER')")
    public ResponseEntity<ApiResponse<StripeConnectDto.StatusResponse>> getStripeConnectStatus() {
        UUID tenantId = TenantContext.getCurrentTenant();
        StripeConnectDto.StatusResponse response = tenantStripeConnectService.getAccountStatus(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
