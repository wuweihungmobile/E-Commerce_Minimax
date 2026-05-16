package com.nextkey.ecommerce.infrastructure.payment;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stripe 支付網關實現
 * Phase 2-B 預留介面
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentGateway implements PaymentGateway {

    private final PaymentRepository paymentRepository;

    private static final String GATEWAY_TYPE = "STRIPE";

    @Override
    public PaymentGatewayRequestResponse.PaymentIntentResult createPaymentIntent(
            PaymentGatewayRequestResponse.PaymentIntentRequest request) {
        log.info("Creating Stripe PaymentIntent: orderId={}, amount={}, currency={}",
                request.getOrderId(), request.getAmount(), request.getCurrency());

        try {
            String paymentIntentId = "pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            String clientSecret = paymentIntentId + "_secret_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            Map<String, Object> metadata = new HashMap<>();
            if (request.getOrderId() != null) {
                metadata.put("order_id", request.getOrderId().toString());
            }
            if (request.getBookingId() != null) {
                metadata.put("booking_id", request.getBookingId().toString());
            }

            return PaymentGatewayRequestResponse.PaymentIntentResult.builder()
                    .transactionId(paymentIntentId)
                    .clientSecret(clientSecret)
                    .status("requires_payment_method")
                    .paymentIntentId(paymentIntentId)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .metadata(metadata)
                    .build();

        } catch (Exception e) {
            log.error("Failed to create Stripe PaymentIntent: {}", e.getMessage(), e);
            return PaymentGatewayRequestResponse.PaymentIntentResult.builder()
                    .status("failed")
                    .errorMessage("Failed to create payment intent: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public PaymentGatewayRequestResponse.PaymentConfirmResult confirmPayment(
            PaymentGatewayRequestResponse.PaymentConfirmRequest request) {
        log.info("Confirming Stripe payment: transactionId={}, paymentIntentId={}",
                request.getTransactionId(), request.getPaymentIntentId());

        try {
            return PaymentGatewayRequestResponse.PaymentConfirmResult.builder()
                    .success(true)
                    .transactionId(request.getTransactionId())
                    .status("succeeded")
                    .build();

        } catch (Exception e) {
            log.error("Failed to confirm Stripe payment: {}", e.getMessage(), e);
            return PaymentGatewayRequestResponse.PaymentConfirmResult.builder()
                    .success(false)
                    .transactionId(request.getTransactionId())
                    .status("failed")
                    .errorMessage("Payment confirmation failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public PaymentGatewayRequestResponse.RefundResult processRefund(
            PaymentGatewayRequestResponse.RefundRequest request) {
        log.info("Processing Stripe refund: transactionId={}, amount={}, reason={}",
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

            String refundId = "re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

            return PaymentGatewayRequestResponse.RefundResult.builder()
                    .success(true)
                    .refundId(refundId)
                    .transactionId(request.getTransactionId())
                    .refundAmount(refundAmount)
                    .status("succeeded")
                    .build();

        } catch (Exception e) {
            log.error("Failed to process Stripe refund: {}", e.getMessage(), e);
            return PaymentGatewayRequestResponse.RefundResult.builder()
                    .success(false)
                    .transactionId(request.getTransactionId())
                    .status("failed")
                    .errorMessage("Refund processing failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public PaymentGatewayRequestResponse.PaymentStatusResult getPaymentStatus(String transactionId) {
        log.info("Getting Stripe payment status: transactionId={}", transactionId);

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

        } catch (Exception e) {
            log.error("Failed to get Stripe payment status: {}", e.getMessage(), e);
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