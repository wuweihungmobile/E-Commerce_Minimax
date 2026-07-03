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
 * 訂單支付狀態機 REST API
 * 提供訂單支付狀態查詢和操作介面
 */
@Slf4j
@RestController
@RequestMapping("/v2/orders")
@RequiredArgsConstructor
public class OrderPaymentController {

    private final PaymentStateService paymentStateService;

    /**
     * 取得訂單支付狀態
     */
    @GetMapping("/{orderId}/payment")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<OrderPaymentStateDto>> getOrderPaymentState(
            @PathVariable UUID orderId) {
        OrderPaymentStateDto state = paymentStateService.getOrderPaymentState(orderId);
        return ResponseEntity.ok(ApiResponse.success(state));
    }

    /**
     * 模擬支付成功（Mock）
     */
    @PostMapping("/{orderId}/pay")
    @PreAuthorize("hasAuthority('order:update')")
    public ResponseEntity<ApiResponse<OrderPaymentStateDto>> mockPaySuccess(
            @PathVariable UUID orderId) {
        log.info("Mock pay success request: orderId={}", orderId);
        OrderPaymentStateDto state = paymentStateService.mockPaymentSuccess(orderId);
        return ResponseEntity.ok(ApiResponse.success("Payment successful", state));
    }

    /**
     * 模擬支付失敗（Mock）
     */
    @PostMapping("/{orderId}/pay/fail")
    @PreAuthorize("hasAuthority('order:update')")
    public ResponseEntity<ApiResponse<OrderPaymentStateDto>> mockPayFailure(
            @PathVariable UUID orderId,
            @RequestParam(required = false) String reason) {
        log.info("Mock pay failure request: orderId={}, reason={}", orderId, reason);
        OrderPaymentStateDto state = paymentStateService.mockPaymentFailure(orderId, reason);
        return ResponseEntity.ok(ApiResponse.success("Payment failed", state));
    }

    /**
     * 模擬退款（Mock）
     */
    @PostMapping("/{orderId}/refund")
    @PreAuthorize("hasAuthority('order:update')")
    public ResponseEntity<ApiResponse<OrderPaymentStateDto>> mockRefund(
            @PathVariable UUID orderId,
            @RequestParam(required = false) String reason) {
        log.info("Mock refund request: orderId={}, reason={}", orderId, reason);
        OrderPaymentStateDto state = paymentStateService.mockRefund(orderId, reason);
        return ResponseEntity.ok(ApiResponse.success("Refund processed", state));
    }

    /**
     * 發起 Stripe Checkout（真實金流 Phase A，Sprint 50 AI-2410）：建 Checkout Session，回前端重導 URL。
     */
    @PostMapping("/{orderId}/pay/checkout")
    @PreAuthorize("hasAuthority('order:update')")
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> initiateStripeCheckout(
            @PathVariable UUID orderId) {
        log.info("Stripe checkout request: orderId={}", orderId);
        CheckoutSessionResponse resp = paymentStateService.initiateStripeCheckout(orderId);
        return ResponseEntity.ok(ApiResponse.success("Checkout session created", resp));
    }

    /**
     * Stripe Checkout 回跳確認（真實金流 Phase A，Sprint 50 AI-2410）：以 session_id retrieve 並回填狀態。
     */
    @GetMapping("/{orderId}/pay/checkout/return")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<OrderPaymentStateDto>> confirmStripeCheckout(
            @PathVariable UUID orderId,
            @RequestParam("sessionId") String sessionId) {
        log.info("Stripe checkout return: orderId={}, sessionId={}", orderId, sessionId);
        OrderPaymentStateDto state = paymentStateService.confirmStripeCheckout(orderId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(state));
    }

    /**
     * 取得預訂支付狀態
     */
    @GetMapping("/bookings/{bookingId}/payment")
    @PreAuthorize("hasAuthority('booking:read')")
    public ResponseEntity<ApiResponse<OrderPaymentStateDto>> getBookingPaymentState(
            @PathVariable UUID bookingId) {
        OrderPaymentStateDto state = paymentStateService.getBookingPaymentState(bookingId);
        return ResponseEntity.ok(ApiResponse.success(state));
    }
}