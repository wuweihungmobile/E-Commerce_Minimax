package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class ProductDto {

    // ========== Create/Update Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be less than 200 characters")
        private String title;

        @Size(max = 5000, message = "Description must be less than 5000 characters")
        private String description;

        @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category must be less than 50 characters")
        private String category;

        @Size(max = 100, message = "Brand must be less than 100 characters")
        private String brand;

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.01", message = "Base price must be greater than 0")
        @DecimalMax(value = "999999999.99", message = "Base price is too large")
        private BigDecimal basePrice;

        private String coverImageUrl;

        @Size(max = 10, message = "Maximum 10 tags allowed")
        private List<String> tags;

        @Min(value = 1, message = "Weight must be positive")
        private Integer weightGrams;

        @Size(max = 50, message = "Dimensions must be less than 50 characters")
        private String dimensionsCm;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 200, message = "Title must be less than 200 characters")
        private String title;

        @Size(max = 5000, message = "Description must be less than 5000 characters")
        private String description;

        @Size(max = 50, message = "Category must be less than 50 characters")
        private String category;

        @Size(max = 100, message = "Brand must be less than 100 characters")
        private String brand;

        @DecimalMin(value = "0.01", message = "Base price must be greater than 0")
        @DecimalMax(value = "999999999.99", message = "Base price is too large")
        private BigDecimal basePrice;

        private String coverImageUrl;

        @Size(max = 10, message = "Maximum 10 tags allowed")
        private List<String> tags;

        @Min(value = 1, message = "Weight must be positive")
        private Integer weightGrams;

        @Size(max = 50, message = "Dimensions must be less than 50 characters")
        private String dimensionsCm;

        private String status;
    }

    // ========== Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private UUID listingId;
        private UUID tenantId;
        private String title;
        private String description;
        private String category;
        private String brand;
        private BigDecimal basePrice;
        private String currency;
        private String coverImageUrl;
        private String status;
        private List<String> tags;
        private Integer weightGrams;
        private String dimensionsCm;
        private java.time.Instant createdAt;
        private java.time.Instant updatedAt;
    }

    // ========== List Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListResponse {
        private UUID listingId;
        private String title;
        private String category;
        private String brand;
        private BigDecimal basePrice;
        private String currency;
        private String coverImageUrl;
        private String status;
        private java.time.Instant createdAt;
    }
}