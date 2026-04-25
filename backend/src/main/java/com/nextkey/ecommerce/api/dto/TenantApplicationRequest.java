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

    @NotBlank(message = "Store name is required")
    @Size(min = 2, max = 100, message = "Store name must be between 2 and 100 characters")
    private String storeName;

    @Size(max = 1000, message = "Store description must not exceed 1000 characters")
    private String storeDescription;

    @NotBlank(message = "Business type is required")
    @Pattern(regexp = "^(RETAIL_ONLY|BOOKING_ONLY|HYBRID)$", message = "Business type must be RETAIL_ONLY, BOOKING_ONLY, or HYBRID")
    private String businessType;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;

    @Size(max = 20, message = "Contact phone must not exceed 20 characters")
    private String contactPhone;

    @Size(max = 500, message = "Business license URL must not exceed 500 characters")
    private String businessLicenseUrl;
}