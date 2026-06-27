package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.SellerDashboardDto;
import com.nextkey.ecommerce.core.seller.SellerDashboardService;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v2/seller/dashboard")
@RequiredArgsConstructor
public class SellerDashboardController {

    private final SellerDashboardService sellerDashboardService;

    @GetMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerDashboardDto.DashboardResponse>> getDashboard() {
        UUID tenantId = TenantContext.getCurrentTenant();
        log.info("Seller dashboard request: tenantId={}", tenantId);
        SellerDashboardDto.DashboardResponse response = sellerDashboardService.getDashboard(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
