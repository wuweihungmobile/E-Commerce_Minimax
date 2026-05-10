package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.*;

public class ProductDto {

    // Validation constraints
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 5000;
    private static final int CATEGORY_MAX_LENGTH = 50;
    private static final int BRAND_MAX_LENGTH = 100;
    private static final int DIMENSIONS_MAX_LENGTH = 50;

    // ========== Create/Update Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Title is required")
        @Size(max = TITLE_MAX_LENGTH, message = "Title must be less than 200 characters")
        private String title;

        @Size(max = DESCRIPTION_MAX_LENGTH, message = "Description must be less than 5000 characters")
        private String description;

        @NotBlank(message = "Category is required")
        @Size(max = CATEGORY_MAX_LENGTH, message = "Category must be less than 50 characters")
        private String category;

        @Size(max = BRAND_MAX_LENGTH, message = "Brand must be less than 100 characters")
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

        @Size(max = DIMENSIONS_MAX_LENGTH, message = "Dimensions must be less than 50 characters")
        private String dimensionsCm;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = TITLE_MAX_LENGTH, message = "Title must be less than 200 characters")
        private String title;

        @Size(max = DESCRIPTION_MAX_LENGTH, message = "Description must be less than 5000 characters")
        private String description;

        @Size(max = CATEGORY_MAX_LENGTH, message = "Category must be less than 50 characters")
        private String category;

        @Size(max = BRAND_MAX_LENGTH, message = "Brand must be less than 100 characters")
        private String brand;

        @DecimalMin(value = "0.01", message = "Base price must be greater than 0")
        @DecimalMax(value = "999999999.99", message = "Base price is too large")
        private BigDecimal basePrice;

        private String coverImageUrl;

        @Size(max = 10, message = "Maximum 10 tags allowed")
        private List<String> tags;

        @Min(value = 1, message = "Weight must be positive")
        private Integer weightGrams;

        @Size(max = DIMENSIONS_MAX_LENGTH, message = "Dimensions must be less than 50 characters")
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