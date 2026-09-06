package com.nextkey.ecommerce.api.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.booking.BookingService;
import com.nextkey.ecommerce.core.idempotency.IdempotencyService;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 預訂 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final IdempotencyService idempotencyService;

    /**
     * 檢查日期範圍可用性
     */
    @GetMapping("/availability")
    @PreAuthorize("hasAuthority('booking:read')")
    public ResponseEntity<ApiResponse<BookingDto.AvailabilityResponse>> checkAvailability(
            @RequestParam final UUID roomListingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate checkInDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate checkOutDate) {
        // 改用 @RequestParam（原 GET+@RequestBody 瀏覽器無法送 body，前端無法呼叫）；
        // service 簽名不變，內部重建 AvailabilityRequest。（S40 AI-2201）
        final BookingDto.AvailabilityRequest request = BookingDto.AvailabilityRequest.builder()
                .roomListingId(roomListingId)
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .build();
        final BookingDto.AvailabilityResponse result = bookingService.checkAvailability(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 取得房源指定日期區間的日曆狀態（整月日曆用，read-only）
     * GET /v2/bookings/calendar?roomListingId=&startDate=&endDate=
     */
    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('booking:read')")
    public ResponseEntity<ApiResponse<List<BookingDto.CalendarResponse>>> getCalendar(
            @RequestParam final UUID roomListingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate endDate) {
        final List<BookingDto.CalendarResponse> result =
                bookingService.getCalendar(roomListingId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 建立預訂
     */
    @PostMapping
    @PreAuthorize("hasAuthority('booking:create')")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> createBooking(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookingDto.CreateRequest request) {

        // 處理 Idempotency-Key
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            // 驗證 Idempotency-Key 格式
            if (!idempotencyService.isValidUuidV4(idempotencyKey)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("E-9004", "Invalid Idempotency-Key format. Must be UUID v4"));
            }

            // 檢查是否為重複請求
            if (!idempotencyService.checkAndMark(idempotencyKey)) {
                // 重複請求 - 嘗試取得已儲存的回應
                BookingDto.BookingResponse storedResponse = idempotencyService.getStoredResponse(idempotencyKey);
                if (storedResponse != null) {
                    log.info("Returning cached response for idempotent key: {}", idempotencyKey);
                    return ResponseEntity.ok(ApiResponse.success(storedResponse));
                }
                // 如果還在處理中，返回 409 Conflict
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error(ErrorCode.E_6005.getCode(), "Request with this Idempotency-Key is still being processed"));
            }

            boolean completed = false;
            try {
                BookingDto.BookingResponse booking = bookingService.createBooking(request, idempotencyKey);
                idempotencyService.markCompleted(idempotencyKey, booking);
                completed = true;
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success("Booking created successfully", booking));
            } finally {
                // 例外時清除 idempotency key，讓客戶端可以重試
                if (!completed) {
                    idempotencyService.remove(idempotencyKey);
                }
            }
        }

        // 沒有 Idempotency-Key 的請求（不建議，但允許）
        BookingDto.BookingResponse booking = bookingService.createBooking(request, null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking created successfully", booking));
    }

    /**
     * 取得用戶預訂列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('booking:read')")
    public ResponseEntity<ApiResponse<Page<BookingDto.BookingListResponse>>> getBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        Page<BookingDto.BookingListResponse> bookings = bookingService.getUserBookings(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }

    /**
     * 取得預訂詳情
     */
    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAuthority('booking:read')")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> getBooking(
            @PathVariable UUID bookingId) {
        BookingDto.BookingResponse booking = bookingService.getBooking(bookingId);
        return ResponseEntity.ok(ApiResponse.success(booking));
    }

    /**
     * 更新預訂
     */
    @PutMapping("/{bookingId}")
    @PreAuthorize("hasAuthority('booking:update')")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> updateBooking(
            @PathVariable UUID bookingId,
            @Valid @RequestBody BookingDto.UpdateRequest request) {
        BookingDto.BookingResponse booking = bookingService.updateBooking(bookingId, request);
        return ResponseEntity.ok(ApiResponse.success("Booking updated successfully", booking));
    }

    /**
     * 取消預訂
     */
    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasAuthority('booking:cancel')")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(
            @PathVariable UUID bookingId,
            @RequestParam(required = false) String reason) {
        bookingService.cancelBooking(bookingId, reason);
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled successfully", null));
    }
}