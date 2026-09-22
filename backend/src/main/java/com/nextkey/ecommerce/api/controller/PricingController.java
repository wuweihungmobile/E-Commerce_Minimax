package com.nextkey.ecommerce.api.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.api.dto.PricingDto;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.booking.BookingService;
import com.nextkey.ecommerce.core.pricing.PricingService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



/**
 * 動態定價 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/dashboard/pricing")
@RequiredArgsConstructor
public class PricingController {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final PricingService pricingService;
    private final BookingService bookingService;

    /**
     * 建立定價規則
     */
    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('room:create') or hasAuthority('product:create')")
    public ResponseEntity<ApiResponse<PricingDto.RuleResponse>> createRule(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PricingDto.CreateRuleRequest request) {
        log.info("Create pricing rule: roomListingId={}, type={}",
                request.getRoomListingId(), request.getRuleType());
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        PricingDto.RuleResponse response = pricingService.createRule(request, isSuperAdmin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pricing rule created", response));
    }

    /**
     * 更新定價規則
     */
    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('room:update') or hasAuthority('product:update')")
    public ResponseEntity<ApiResponse<PricingDto.RuleResponse>> updateRule(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @RequestBody PricingDto.UpdateRuleRequest request) {
        log.info("Update pricing rule: ruleId={}", ruleId);
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        PricingDto.RuleResponse response = pricingService.updateRule(ruleId, request, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success("Pricing rule updated", response));
    }

    /**
     * 取得定價規則列表
     */
    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('room:read') or hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<List<PricingDto.RuleResponse>>> getRules(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID roomListingId,
            @RequestParam(required = false) UUID listingId,
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly) {
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        List<PricingDto.RuleResponse> response =
                pricingService.getRules(roomListingId, listingId, activeOnly, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 刪除定價規則
     */
    @DeleteMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('room:delete') or hasAuthority('product:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteRule(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID ruleId) {
        log.info("Delete pricing rule: ruleId={}", ruleId);
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        pricingService.deleteRule(ruleId, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success("Pricing rule deleted", null));
    }

    /**
     * 計算價格
     */
    @PostMapping("/calculate")
    @PreAuthorize("hasAuthority('room:read') or hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<PricingDto.CalculatePriceResponse>> calculatePrice(
            @Valid @RequestBody PricingDto.CalculatePriceRequest request) {
        log.info("Calculate price: roomListingId={}, checkIn={}, checkOut={}",
                request.getRoomListingId(), request.getCheckInDate(), request.getCheckOutDate());
        PricingDto.CalculatePriceResponse response = pricingService.calculatePrice(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 房東後台預覽定價日曆（Sprint 83，PRD P0：未來 90 天定價日曆預覽）。
     * 非 SUPER_ADMIN 僅能檢視自己租戶房源的定價日曆。
     */
    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('room:read') or hasAuthority('product:read')")
    public ResponseEntity<ApiResponse<List<BookingDto.CalendarResponse>>> getCalendarPreview(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID roomListingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.E_4001, "定價預覽不支援過去日期");
        }
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        List<BookingDto.CalendarResponse> response =
                bookingService.getCalendarForOwner(roomListingId, startDate, endDate, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 設定日曆價格（手動覆蓋）
     */
    @PostMapping("/calendar/price")
    @PreAuthorize("hasAuthority('room:update')")
    public ResponseEntity<ApiResponse<PricingDto.CalendarPriceResponse>> setCalendarPrice(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PricingDto.SetCalendarPriceRequest request) {
        log.info("Set calendar price: roomListingId={}, date={}, price={}",
                request.getRoomListingId(), request.getDate(), request.getPrice());
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        PricingDto.CalendarPriceResponse response = pricingService.setCalendarPrice(request, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success("Calendar price set", response));
    }
}
