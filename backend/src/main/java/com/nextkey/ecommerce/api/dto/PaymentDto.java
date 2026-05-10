package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.*;

/**
 * 支付 DTO
 */
public class PaymentDto {

    // ========== Payment Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRequest {
        @NotNull(message = "Order ID or Booking ID is required")
        private UUID orderId; // for PRODUCT orders

        private UUID bookingId; // for ROOM bookings

        @NotNull(message = "Payment method is required")
        private PaymentMethod paymentMethod;
    }

    public enum PaymentMethod {
        LINE_PAY,
        CREDIT_CARD,
        MOCK
    }

    // ========== Payment Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentResponse {
        private UUID paymentId;
        private UUID orderId;
        private UUID bookingId;
        private String paymentMethod;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String transactionId;
        private String paymentUrl; // for LINE_PAY redirect
        private Instant paidAt;
        private Instant createdAt;
    }

    // ========== Payment Status Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentStatusResponse {
        private UUID paymentId;
        private String status; // PENDING, SUCCESS, FAILED
        private String transactionId;
        private String message;
        private Instant updatedAt;
    }

    // ========== Refund Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundRequest {
        @NotNull(message = "Payment ID is required")
        private UUID paymentId;

        @Min(value = 1, message = "Amount must be positive")
        private BigDecimal amount; // optional, full refund if null

        private String reason;
    }

    // ========== Refund Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundResponse {
        private UUID refundId;
        private UUID paymentId;
        private BigDecimal refundAmount;
        private String status;
        private String reason;
        private Instant processedAt;
    }
}