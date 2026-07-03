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

    @GetMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerDashboardDto.DashboardResponse>> getDashboard() {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.info("Seller dashboard request: tenantId={}", tenantId);
        SellerDashboardDto.DashboardResponse response = sellerDashboardService.getDashboard(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 發起（或重新產生）Stripe Connect Express 帳戶 onboarding link（Sprint 53 AI-2413 Phase D-1）。
     */
    @PostMapping("/stripe-connect/onboarding")
    @PreAuthorize("hasRole('SELLER')")
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
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<StripeConnectDto.StatusResponse>> getStripeConnectStatus() {
        UUID tenantId = TenantContext.getCurrentTenant();
        StripeConnectDto.StatusResponse response = tenantStripeConnectService.getAccountStatus(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
