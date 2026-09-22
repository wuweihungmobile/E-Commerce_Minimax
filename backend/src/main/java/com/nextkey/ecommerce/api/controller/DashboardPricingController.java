package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.pricing.PricingService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dashboard 定價管理 API
 * T-M12-03: 手動覆蓋價格端點
 */
@Slf4j
@RestController
@RequestMapping("/v2/dashboard/pricing")
@RequiredArgsConstructor
public class DashboardPricingController {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final PricingService pricingService;

    /**
     * 手動覆蓋價格端點
     * T-M12-03: POST /api/v2/dashboard/pricing/rules/:id/override
     *
     * @param ruleId 定價規則 ID
     * @param request 覆蓋請求（開始日期、結束日期、覆蓋價格）
     * @return 覆蓋結果
     */
    @PostMapping("/rules/{ruleId}/override")
    @PreAuthorize("hasAuthority('room:update')")
    public ResponseEntity<ApiResponse<PricingDto.RuleOverrideResponse>> overridePrice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @RequestBody PricingDto.RuleOverrideRequest request) {

        log.info("Override price: ruleId={}, startDate={}, endDate={}, price={}",
                ruleId, request.getStartDate(), request.getEndDate(), request.getOverridePrice());

        // 驗證日期範圍
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(ErrorCode.E_4003, "End date must be after start date");
        }

        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        PricingDto.RuleOverrideResponse response = pricingService.overridePrice(
                ruleId,
                request.getStartDate(),
                request.getEndDate(),
                request.getOverridePrice(),
                request.getReason(),
                isSuperAdmin
        );

        return ResponseEntity.ok(ApiResponse.success("Price override applied", response));
    }
}