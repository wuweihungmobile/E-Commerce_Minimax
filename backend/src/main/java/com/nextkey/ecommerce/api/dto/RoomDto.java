package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.*;

public class RoomDto {

    // Validation constraints
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 5000;
    private static final int LOCATION_MAX_LENGTH = 200;

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

        @NotBlank(message = "Location is required")
        @Size(max = LOCATION_MAX_LENGTH, message = "Location must be less than 200 characters")
        private String location;

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        private Double latitude;

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        private Double longitude;

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.01", message = "Base price must be greater than 0")
        @DecimalMax(value = "999999999.99", message = "Base price is too large")
        private BigDecimal basePrice;

        @Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
                message = "Cover image URL must not use javascript/data/vbscript/file protocol")
        private String coverImageUrl;

        @Size(max = 10, message = "Maximum 10 tags allowed")
        private List<String> tags;

        @Min(value = 1, message = "Max guests must be at least 1")
        private Integer maxGuests;

        private List<String> amenities;

        private LocalTime checkInTime;

        private LocalTime checkOutTime;

        @Min(value = 1, message = "Room count must be at least 1")
        private Integer roomCount;

        // 開放窗（Sprint 47 AI-2202e）：開放至某固定日；null = 無限制
        private LocalDate openUntilDate;

        // 開放窗（Sprint 47 AI-2202e）：開放未來 N 天（滾動）；null = 無限制
        @Min(value = 1, message = "Booking window days must be at least 1")
        private Integer bookingWindowDays;
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

        @Size(max = LOCATION_MAX_LENGTH, message = "Location must be less than 200 characters")
        private String location;

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        private Double latitude;

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        private Double longitude;

        @DecimalMin(value = "0.01", message = "Base price must be greater than 0")
        @DecimalMax(value = "999999999.99", message = "Base price is too large")
        private BigDecimal basePrice;

        @Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
                message = "Cover image URL must not use javascript/data/vbscript/file protocol")
        private String coverImageUrl;

        @Size(max = 10, message = "Maximum 10 tags allowed")
        private List<String> tags;

        @Min(value = 1, message = "Max guests must be at least 1")
        private Integer maxGuests;

        private List<String> amenities;

        private LocalTime checkInTime;

        private LocalTime checkOutTime;

        @Min(value = 1, message = "Room count must be at least 1")
        private Integer roomCount;

        // 開放窗（Sprint 47 AI-2202e）：開放至某固定日；null = 無限制
        private LocalDate openUntilDate;

        // 開放窗（Sprint 47 AI-2202e）：開放未來 N 天（滾動）；null = 無限制
        @Min(value = 1, message = "Booking window days must be at least 1")
        private Integer bookingWindowDays;

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
        private String location;
        private Double latitude;
        private Double longitude;
        private BigDecimal basePrice;
        private String currency;
        private String coverImageUrl;
        private String status;
        private List<String> tags;
        private Integer maxGuests;
        private List<String> amenities;
        private LocalTime checkInTime;
        private LocalTime checkOutTime;
        private Integer roomCount;
        // 開放窗（Sprint 47 AI-2202e）：null = 無限制
        private LocalDate openUntilDate;
        private Integer bookingWindowDays;
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
        private String location;
        private BigDecimal basePrice;
        private String currency;
        private String coverImageUrl;
        private String status;
        private Integer maxGuests;
        private Integer roomCount;
        private java.time.Instant createdAt;
    }
}