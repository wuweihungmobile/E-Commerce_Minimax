package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.*;

/**
 * 統一建立 Listing 的請求 DTO
 * T-DEF-001-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateListingRequest {

    // Validation constraints
    private static final int NAME_MAX_LENGTH = 200;
    private static final int DESCRIPTION_MAX_LENGTH = 5000;
    private static final int CATEGORY_MAX_LENGTH = 50;
    private static final int BRAND_MAX_LENGTH = 100;
    private static final int DIMENSIONS_MAX_LENGTH = 50;
    private static final int LOCATION_MAX_LENGTH = 200;

    // ========== 通用欄位 ==========

    @NotBlank(message = "Listing type is required (PRODUCT or ROOM)")
    @Pattern(regexp = "^(PRODUCT|ROOM)$", message = "Listing type must be PRODUCT or ROOM")
    private String listingType;

    @NotBlank(message = "Name is required")
    @Size(max = NAME_MAX_LENGTH, message = "Name must be less than 200 characters")
    private String name;

    @Size(max = DESCRIPTION_MAX_LENGTH, message = "Description must be less than 5000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Price is too large")
    private BigDecimal price;

    @Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
            message = "Cover image URL must not use javascript/data/vbscript/file protocol")
    private String coverImageUrl;

    @Size(max = 10, message = "Maximum 10 tags allowed")
    private List<String> tags;

    // ========== Product 特定欄位 ==========

    @Size(max = CATEGORY_MAX_LENGTH, message = "Category must be less than 50 characters")
    private String category;

    @Size(max = BRAND_MAX_LENGTH, message = "Brand must be less than 100 characters")
    private String brand;

    @Min(value = 1, message = "Weight must be positive")
    private Integer weightGrams;

    @Size(max = DIMENSIONS_MAX_LENGTH, message = "Dimensions must be less than 50 characters")
    private String dimensionsCm;

    // ========== Room 特定欄位 ==========

    @Size(max = LOCATION_MAX_LENGTH, message = "Location must be less than 200 characters")
    private String location;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    @Min(value = 1, message = "Max guests must be at least 1")
    private Integer maxGuests;

    private List<String> amenities;

    private LocalTime checkInTime;

    private LocalTime checkOutTime;

    @Min(value = 1, message = "Room count must be at least 1")
    private Integer roomCount;

    // ========== 擴展欄位 (用於未來擴展) ==========
    private Map<String, Object> additionalFields;
}