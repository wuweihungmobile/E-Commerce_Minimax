package com.nextkey.ecommerce.infrastructure.payment;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LinePay 支付網關實現
 * Phase 2-B 預留介面
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinePayPaymentGateway implements PaymentGateway {

    private final PaymentRepository paymentRepository;

    private static final String GATEWAY_TYPE = "LINE_PAY";

    @Override
    public PaymentGatewayRequestResponse.PaymentIntentResult createPaymentIntent(
            PaymentGatewayRequestResponse.PaymentIntentRequest request) {
        log.info("Creating LinePay payment: orderId={}, amount={}, currency={}",
                request.getOrderId(), request.getAmount(), request.getCurrency());

        String transactionId = "LP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        String orderId = "LPORD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("order_id", orderId);
        if (request.getOrderId() != null) {
            metadata.put("original_order_id", request.getOrderId().toString());
        }
        if (request.getBookingId() != null) {
            metadata.put("booking_id", request.getBookingId().toString());
        }

        return PaymentGatewayRequestResponse.PaymentIntentResult.builder()
                .transactionId(transactionId)
                .clientSecret(null)
                .status("pending")
                .paymentIntentId(orderId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .metadata(metadata)
                .build();
    }

    @Override
    public PaymentGatewayRequestResponse.PaymentConfirmResult confirmPayment(
            PaymentGatewayRequestResponse.PaymentConfirmRequest request) {
        log.info("Confirming LinePay payment: transactionId={}", request.getTransactionId());

        return PaymentGatewayRequestResponse.PaymentConfirmResult.builder()
                .success(true)
                .transactionId(request.getTransactionId())
                .status("completed")
                .build();
    }

    @Override
    public PaymentGatewayRequestResponse.RefundResult processRefund(
            PaymentGatewayRequestResponse.RefundRequest request) {
        log.info("Processing LinePay refund: transactionId={}, amount={}, reason={}",
                request.getTransactionId(), request.getAmount(), request.getReason());

        try {
            Payment payment = paymentRepository.findByTransactionId(request.getTransactionId())
                    .orElse(null);

            if (payment == null) {
                return PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(false)
                        .transactionId(request.getTransactionId())
                        .status("failed")
                        .errorMessage("Payment not found")
                        .build();
            }

            BigDecimal refundAmount = request.getAmount() != null ? request.getAmount() : payment.getAmount();

            if (refundAmount.compareTo(payment.getAmount()) > 0) {
                return PaymentGatewayRequestResponse.RefundResult.builder()
                        .success(false)
                        .transactionId(request.getTransactionId())
                        .status("failed")
                        .errorMessage("Refund amount exceeds payment amount")
                        .build();
            }

            String refundId = "LPRF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

            return PaymentGatewayRequestResponse.RefundResult.builder()
                    .success(true)
                    .refundId(refundId)
                    .transactionId(request.getTransactionId())
                    .refundAmount(refundAmount)
                    .status("completed")
                    .build();

        } catch (DataAccessException e) {
            log.error("[E_5012] Failed to process LinePay refund (DataAccessException): {}", e.getMessage(), e);
            return PaymentGatewayRequestResponse.RefundResult.builder()
                    .success(false)
                    .transactionId(request.getTransactionId())
                    .status("failed")
                    .errorMessage("LinePay refund processing failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public PaymentGatewayRequestResponse.PaymentStatusResult getPaymentStatus(String transactionId) {
        log.info("Getting LinePay payment status: transactionId={}", transactionId);

        try {
            Payment payment = paymentRepository.findByTransactionId(transactionId)
                    .orElse(null);

            if (payment == null) {
                return PaymentGatewayRequestResponse.PaymentStatusResult.builder()
                        .transactionId(transactionId)
                        .status("not_found")
                        .errorMessage("Payment not found")
                        .build();
            }

            return PaymentGatewayRequestResponse.PaymentStatusResult.builder()
                    .transactionId(transactionId)
                    .status(payment.getStatus().name().toLowerCase())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .build();

        } catch (DataAccessException e) {
            log.error("[E_6001] Failed to get LinePay payment status (DataAccessException): {}", e.getMessage(), e);
            return PaymentGatewayRequestResponse.PaymentStatusResult.builder()
                    .transactionId(transactionId)
                    .status("error")
                    .errorMessage("Failed to get payment status: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String getGatewayType() {
        return GATEWAY_TYPE;
    }
}