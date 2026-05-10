package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantApplicationRequest {

    // Validation constraints
    private static final int STORE_NAME_MAX_LENGTH = 100;
    private static final int STORE_DESCRIPTION_MAX_LENGTH = 1000;
    private static final int CONTACT_PHONE_MAX_LENGTH = 20;
    private static final int BUSINESS_LICENSE_URL_MAX_LENGTH = 500;

    @NotBlank(message = "Store name is required")
    @Size(min = 2, max = STORE_NAME_MAX_LENGTH, message = "Store name must be between 2 and 100 characters")
    private String storeName;

    @Size(max = STORE_DESCRIPTION_MAX_LENGTH, message = "Store description must not exceed 1000 characters")
    private String storeDescription;

    @NotBlank(message = "Business type is required")
    @Pattern(regexp = "^(RETAIL_ONLY|BOOKING_ONLY|HYBRID)$", message = "Business type must be RETAIL_ONLY, BOOKING_ONLY, or HYBRID")
    private String businessType;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;

    @Size(max = CONTACT_PHONE_MAX_LENGTH, message = "Contact phone must not exceed 20 characters")
    private String contactPhone;

    @Size(max = BUSINESS_LICENSE_URL_MAX_LENGTH, message = "Business license URL must not exceed 500 characters")
    private String businessLicenseUrl;
}