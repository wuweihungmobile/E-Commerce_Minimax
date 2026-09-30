package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.payment.CheckoutSessionResponse;
import com.nextkey.ecommerce.api.dto.payment.OrderPaymentStateDto;
import com.nextkey.ecommerce.core.payment.PaymentStateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 訂房付款 REST API（Sprint 221，DEF-303 (1)）。
 *
 * <p>訂房原本完全沒有付款入口：前端訂房流程沒有付款步驟，舊版 {@code POST /v2/payments} 的 {@code orderId} 又是必填，
 * 訂房分支根本打不到。本控制器比照 {@link OrderPaymentController} 提供 Mock 付款、Stripe 結帳、回跳確認三個入口；
 * 訂房付款狀態沿用既有的 {@code GET /v2/orders/bookings/{bookingId}/payment}。
 *
 * <p>權限：買家有 {@code booking:create}、{@code booking:cancel}，但<b>沒有</b> {@code booking:update}
 * （DEF-298 對訂單付款端點的同一個教訓），所以付款動作放行 {@code booking:create} 或 {@code booking:update}。
 * 能不能付「這一筆」仍由服務層的本人或 ADMIN 檢查決定。
 */
@Slf4j
@RestController
@RequestMapping("/v2/bookings/{bookingId}")
@RequiredArgsConstructor
public class BookingPaymentController {

    private final PaymentStateService paymentStateService;

    /**
     * 模擬付款成功（Mock）：訂房 CREATED → PAID。啟用 Stripe 後一律拒絕（E-6004）。
     */
    @PostMapping("/pay")
    @PreAuthorize("hasAuthority('booking:create') or hasAuthority('booking:update')")
    public ResponseEntity<ApiResponse<OrderPaymentStateDto>> mockPaySuccess(@PathVariable UUID bookingId) {
        log.info("Mock booking pay request: bookingId={}", bookingId);
        OrderPaymentStateDto state = paymentStateService.mockBookingPaymentSuccess(bookingId);
        return ResponseEntity.ok(ApiResponse.success("Payment successful", state));
    }

    /**
     * 發起 Stripe Checkout：建 Checkout Session，回前端重導 URL。
     */
    @PostMapping("/pay/checkout")
    @PreAuthorize("hasAuthority('booking:create') or hasAuthority('booking:update')")
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> initiateStripeCheckout(
            @PathVariable UUID bookingId) {
        log.info("Stripe booking checkout request: bookingId={}", bookingId);
        CheckoutSessionResponse response = paymentStateService.initiateStripeBookingCheckout(bookingId);
        return ResponseEntity.ok(ApiResponse.success("Checkout session created", response));
    }

    /**
     * Stripe Checkout 回跳確認：以 session_id retrieve 並回填狀態。
     */
    @GetMapping("/pay/checkout/return")
    @PreAuthorize("hasAuthority('booking:read')")
    public ResponseEntity<ApiResponse<OrderPaymentStateDto>> confirmStripeCheckout(
            @PathVariable UUID bookingId,
            @RequestParam("sessionId") String sessionId) {
        log.info("Stripe booking checkout return: bookingId={}, sessionId={}", bookingId, sessionId);
        OrderPaymentStateDto state = paymentStateService.confirmStripeBookingCheckout(bookingId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(state));
    }
}
