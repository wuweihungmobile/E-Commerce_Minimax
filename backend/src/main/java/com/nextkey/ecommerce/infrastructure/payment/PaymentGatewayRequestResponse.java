package com.nextkey.ecommerce.infrastructure.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付請求和結果 DTO
 */
public class PaymentGatewayRequestResponse {

    private PaymentGatewayRequestResponse() {}

    /**
     * 支付意圖請求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentIntentRequest {
        private UUID orderId;
        private UUID bookingId;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod; // STRIPE, LINE_PAY, MOCK
        private Map<String, Object> metadata;
        private String idempotencyKey;
    }

    /**
     * 支付意圖結果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentIntentResult {
        private String transactionId;
        private String clientSecret;
        private String status;
        private String paymentIntentId;
        private BigDecimal amount;
        private String currency;
        private Map<String, Object> metadata;
        private String errorMessage;
    }

    /**
     * 支付確認請求
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentConfirmRequest {
        private String transactionId;
        private String paymentIntentId;
        private String paymentMethod;
        private Map<String, Object> paymentResult;
    }

    /**
     * 支付確認結果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentConfirmResult {
        private boolean success;
        private String transactionId;
        private String status;
        private String errorMessage;
    }

    /**
     * 退款請求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundRequest {
        private String transactionId;
        private BigDecimal amount; // null = 全額退款
        private String reason;
        private String idempotencyKey;
    }

    /**
     * 退款結果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundResult {
        private boolean success;
        private String refundId;
        private String transactionId;
        private BigDecimal refundAmount;
        private String status;
        private String errorMessage;
    }

    /**
     * 支付狀態結果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentStatusResult {
        private String transactionId;
        private String status;
        private BigDecimal amount;
        private String currency;
        private String errorMessage;
    }
}