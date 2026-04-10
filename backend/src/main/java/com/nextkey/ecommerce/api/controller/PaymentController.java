package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.PaymentDto;
import com.nextkey.ecommerce.core.payment.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 支付 REST API (Mock Implementation)
 */
@Slf4j
@RestController
@RequestMapping("/v2/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 處理支付
     */
    @PostMapping
    @PreAuthorize("hasAuthority('order:create') or hasAuthority('booking:create')")
    public ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentDto.PaymentRequest request) {
        log.info("Payment request: orderId={}, bookingId={}, method={}",
                request.getOrderId(), request.getBookingId(), request.getPaymentMethod());
        PaymentDto.PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", response));
    }

    /**
     * 處理退款
     */
    @PostMapping("/refund")
    @PreAuthorize("hasAuthority('order:update') or hasAuthority('booking:update')")
    public ResponseEntity<ApiResponse<PaymentDto.RefundResponse>> processRefund(
            @Valid @RequestBody PaymentDto.RefundRequest request) {
        log.info("Refund request: paymentId={}, amount={}", request.getPaymentId(), request.getAmount());
        PaymentDto.RefundResponse response = paymentService.processRefund(request);
        return ResponseEntity.ok(ApiResponse.success("Refund processed successfully", response));
    }

    /**
     * 取得支付狀態
     */
    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAuthority('order:read') or hasAuthority('booking:read')")
    public ResponseEntity<ApiResponse<PaymentDto.PaymentStatusResponse>> getPaymentStatus(
            @PathVariable UUID paymentId) {
        PaymentDto.PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}