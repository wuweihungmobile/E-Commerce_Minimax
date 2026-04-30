package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.core.pricing.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 動態定價 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/dashboard/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    /**
     * 建立定價規則
     */
    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('room:create')")
    public ResponseEntity<ApiResponse<PricingDto.RuleResponse>> createRule(
            @Valid @RequestBody PricingDto.CreateRuleRequest request) {
        log.info("Create pricing rule: roomListingId={}, type={}",
                request.getRoomListingId(), request.getRuleType());
        PricingDto.RuleResponse response = pricingService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pricing rule created", response));
    }

    /**
     * 更新定價規則
     */
    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('room:update')")
    public ResponseEntity<ApiResponse<PricingDto.RuleResponse>> updateRule(
            @PathVariable UUID ruleId,
            @Valid @RequestBody PricingDto.UpdateRuleRequest request) {
        log.info("Update pricing rule: ruleId={}", ruleId);
        PricingDto.RuleResponse response = pricingService.updateRule(ruleId, request);
        return ResponseEntity.ok(ApiResponse.success("Pricing rule updated", response));
    }

    /**
     * 取得定價規則列表
     */
    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('room:read')")
    public ResponseEntity<ApiResponse<List<PricingDto.RuleResponse>>> getRules(
            @RequestParam(required = false) UUID roomListingId,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly) {
        List<PricingDto.RuleResponse> response = pricingService.getRules(roomListingId, activeOnly);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 刪除定價規則
     */
    @DeleteMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('room:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable UUID ruleId) {
        log.info("Delete pricing rule: ruleId={}", ruleId);
        pricingService.deleteRule(ruleId);
        return ResponseEntity.ok(ApiResponse.success("Pricing rule deleted", null));
    }

    /**
     * 計算價格
     */
    @PostMapping("/calculate")
    @PreAuthorize("hasAuthority('room:read')")
    public ResponseEntity<ApiResponse<PricingDto.CalculatePriceResponse>> calculatePrice(
            @Valid @RequestBody PricingDto.CalculatePriceRequest request) {
        log.info("Calculate price: roomListingId={}, checkIn={}, checkOut={}",
                request.getRoomListingId(), request.getCheckInDate(), request.getCheckOutDate());
        PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 設定日曆價格（手動覆蓋）
     */
    @PostMapping("/calendar/price")
    @PreAuthorize("hasAuthority('room:update')")
    public ResponseEntity<ApiResponse<PricingDto.CalendarPriceResponse>> setCalendarPrice(
            @Valid @RequestBody PricingDto.SetCalendarPriceRequest request) {
        log.info("Set calendar price: roomListingId={}, date={}, price={}",
                request.getRoomListingId(), request.getDate(), request.getPrice());
        PricingDto.CalendarPriceResponse response = pricingService.setCalendarPrice(request);
        return ResponseEntity.ok(ApiResponse.success("Calendar price set", response));
    }
}
