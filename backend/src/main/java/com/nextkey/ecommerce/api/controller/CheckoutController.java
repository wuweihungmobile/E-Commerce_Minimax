package com.nextkey.ecommerce.api.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.CheckoutDto;
import com.nextkey.ecommerce.core.checkout.CombinedCheckoutService;
import com.nextkey.ecommerce.core.idempotency.IdempotencyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 合併結帳 REST API（Sprint 126，DEF-048 擴大範圍）。
 *
 * <p>只給「購物車同時有 PRODUCT 與 ROOM 項目」這個場景使用；純單一類型仍走既有的
 * {@code POST /v2/orders}／{@code POST /v2/bookings}。
 */
@Slf4j
@RestController
@RequestMapping("/v2/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CombinedCheckoutService combinedCheckoutService;
    private final IdempotencyService idempotencyService;

    /**
     * 合併結帳：一次動作同時建立 Order（PRODUCT）與 Booking（ROOM）。
     *
     * <p>比照 {@code BookingController.createBooking} 走 Idempotency-Key（{@code OrderController}
     * 目前沒有這個機制，但合併結帳同時牽動庫存預扣與訂房日曆鎖定，比照 Booking 走 idempotency
     * 較安全）。
     */
    @PostMapping("/mixed")
    @PreAuthorize("hasAuthority('order:create') and hasAuthority('booking:create')")
    public ResponseEntity<ApiResponse<CheckoutDto.MixedCheckoutResponse>> checkoutMixedCart(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutDto.MixedCheckoutRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            CheckoutDto.MixedCheckoutResponse response = combinedCheckoutService.checkoutMixedCart(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Mixed checkout completed successfully", response));
        }

        if (!idempotencyService.isValidUuidV4(idempotencyKey)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("E-9004", "Invalid Idempotency-Key format. Must be UUID v4"));
        }

        if (!idempotencyService.checkAndMark(idempotencyKey)) {
            CheckoutDto.MixedCheckoutResponse storedResponse = idempotencyService.getStoredResponse(idempotencyKey);
            if (storedResponse != null) {
                log.info("Returning cached response for idempotent key: {}", idempotencyKey);
                return ResponseEntity.ok(ApiResponse.success(storedResponse));
            }
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("E_6005", "Request with this Idempotency-Key is still being processed"));
        }

        boolean completed = false;
        try {
            CheckoutDto.MixedCheckoutResponse response = combinedCheckoutService.checkoutMixedCart(request);
            idempotencyService.markCompleted(idempotencyKey, response);
            completed = true;
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Mixed checkout completed successfully", response));
        } finally {
            if (!completed) {
                idempotencyService.remove(idempotencyKey);
            }
        }
    }
}
