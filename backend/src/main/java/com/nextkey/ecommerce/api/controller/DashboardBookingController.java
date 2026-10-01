package com.nextkey.ecommerce.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.booking.BookingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 訂房管理店家層 API（Sprint 231，DEF-316；PRD §9.16「GET /api/v2/dashboard/bookings」）。
 *
 * <p>買家層 {@code /v2/bookings}（{@link BookingController}）、店家層 {@code /v2/dashboard/bookings}，
 * 比照 {@code ReturnRequestController} 的分層路由慣例。取消動作沿用買家層既有的
 * {@code POST /v2/bookings/{id}/cancel}（{@code BookingService.checkBookingOwnership} 已於本 Sprint
 * 新增租戶範圍分支），不另開一個店家層取消端點。
 */
@Slf4j
@RestController
@RequestMapping("/v2/dashboard/bookings")
@RequiredArgsConstructor
public class DashboardBookingController {

    private final BookingService bookingService;

    @GetMapping
    @PreAuthorize("hasAuthority('booking:read')")
    public ResponseEntity<ApiResponse<Page<BookingDto.BookingListResponse>>> getTenantBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        Page<BookingDto.BookingListResponse> bookings = bookingService.getTenantBookings(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }
}
