package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import lombok.*;

/**
 * 物流 DTO
 */
public class LogisticsDto {

    // ========== Create Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "Order ID is required")
        private UUID orderId;

        @NotNull(message = "Logistics provider is required")
        private LogisticsProvider logisticsProvider;

        private String receiverName;
        private String receiverPhone;
        private String shippingAddress;
    }

    public enum LogisticsProvider {
        HCT,
        TCAT
    }

    // ========== Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogisticsResponse {
        private UUID logisticsId;
        private UUID orderId;
        private String logisticsProvider;
        private String trackingNumber;
        private String status;
        private LocalDateTime pickupTime;
        private LocalDateTime deliveryTime;
        private String shippingAddress;
        private String receiverName;
        private String receiverPhone;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ========== Status Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusResponse {
        private UUID logisticsId;
        private String trackingNumber;
        private String status;
        private String statusMessage;
        private String location;
        private LocalDateTime eventTime;
        private Instant updatedAt;
    }

    // ========== Tracking Detail ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingDetail {
        private UUID logisticsId;
        private String trackingNumber;
        private String logisticsProvider;
        private String currentStatus;
        private ShippingAddress shippingAddress;
        private java.util.List<TrackingEvent> events;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddress {
        private String receiverName;
        private String phone;
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingEvent {
        private String status;
        private String description;
        private String location;
        private LocalDateTime eventTime;
    }

    // ========== Cancel Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelRequest {
        @NotNull(message = "Logistics ID is required")
        private UUID logisticsId;

        private String reason;
    }

    // ========== Provider Abstractions (US-003) ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentResult {
        private String trackingNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingResult {
        private String trackingNumber;
        private String status;
        private String location;
    }
}
