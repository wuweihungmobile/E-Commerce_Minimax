package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.nextkey.ecommerce.domain.model.logistics.ShippingTemplate;

/**
 * 運費模板 DTO
 */
public class ShippingTemplateDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotNull(message = "Fee type is required")
        private ShippingTemplate.FeeType feeType;

        private BigDecimal fixedAmount;
        private BigDecimal freeThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String name;
        private BigDecimal fixedAmount;
        private BigDecimal freeThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateResponse {
        private UUID id;
        private UUID tenantId;
        private String name;
        private ShippingTemplate.FeeType feeType;
        private BigDecimal fixedAmount;
        private BigDecimal freeThreshold;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeCalculationResponse {
        private UUID templateId;
        private String feeType;
        private BigDecimal orderAmount;
        private BigDecimal shippingFee;
    }
}
