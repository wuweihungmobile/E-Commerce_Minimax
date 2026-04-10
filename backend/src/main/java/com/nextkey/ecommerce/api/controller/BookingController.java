package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.BookingDto;
import com.nextkey.ecommerce.core.booking.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 預訂 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * 檢查日期範圍可用性
     */
    @GetMapping("/availability")
    @PreAuthorize("hasAuthority('booking:read')")
    public ResponseEntity<ApiResponse<BookingDto.AvailabilityResponse>> checkAvailability(
            @Valid @RequestBody BookingDto.AvailabilityRequest request) {
        BookingDto.AvailabilityResponse result = bookingService.checkAvailability(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 建立預訂
     */
    @PostMapping
    @PreAuthorize("hasAuthority('booking:create')")
    public ResponseEntity<ApiResponse<BookingDto.BookingResponse>> createBooking(
            @Valid @RequestBody BookingDto.CreateRequest request) {
        BookingDto.BookingResponse booking = bookingService.createBooking(request);
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