package com.nextkey.ecommerce.infrastructure.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mock 支付網關實現
 * Phase 2-A 使用 Mock 支付，不需要真實金流整合
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    private final PaymentRepository paymentRepository;

    private static final String GATEWAY_TYPE = "MOCK";

    @Override
    public PaymentGatewayRequestResponse.PaymentIntentResult createPaymentIntent(
            PaymentGatewayRequestResponse.PaymentIntentRequest request) {
        log.info("Creating Mock PaymentIntent: orderId={}, amount={}, currency={}",
                request.getOrderId(), request.getAmount(), request.getCurrency());

        String paymentIntentId = "MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        return PaymentGatewayRequestResponse.PaymentIntentResult.builder()
                .transactionId(paymentIntentId)
                .clientSecret(paymentIntentId + "_secret")
                .status("succeeded")
                .paymentIntentId(paymentIntentId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .build();
    }

    @Override
    public PaymentGatewayRequestResponse.PaymentConfirmResult confirmPayment(
            PaymentGatewayRequestResponse.PaymentConfirmRequest request) {
        log.info("Confirming Mock payment: transactionId={}", request.getTransactionId());

        return PaymentGatewayRequestResponse.PaymentConfirmResult.builder()
                .success(true)
                .transactionId(request.getTransactionId())
                .status("succeeded")
                .build();
    }

    @Override
    public PaymentGatewayRequestResponse.RefundResult processRefund(
            PaymentGatewayRequestResponse.RefundRequest request) {
        log.info("Processing Mock refund: transactionId={}, amount={}, reason={}",
                request.getTransactionId(), request.getAmount(), request.getReason());

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

        String refundId = "MOCK-RF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        return PaymentGatewayRequestResponse.RefundResult.builder()
                .success(true)
                .refundId(refundId)
                .transactionId(request.getTransactionId())
                .refundAmount(refundAmount)
                .status("succeeded")
                .build();
    }

    @Override
    public PaymentGatewayRequestResponse.PaymentStatusResult getPaymentStatus(String transactionId) {
        log.info("Getting Mock payment status: transactionId={}", transactionId);

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
    }

    @Override
    public String getGatewayType() {
        return GATEWAY_TYPE;
    }
}