package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
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
public class TenantUpdateRequest {

    // Validation constraints
    private static final int STORE_NAME_MAX_LENGTH = 100;
    private static final int STORE_DESCRIPTION_MAX_LENGTH = 1000;
    private static final int CONTACT_PHONE_MAX_LENGTH = 20;
    private static final int URL_MAX_LENGTH = 500;

    @Size(min = 2, max = STORE_NAME_MAX_LENGTH, message = "Store name must be between 2 and 100 characters")
    private String storeName;

    @Size(max = STORE_DESCRIPTION_MAX_LENGTH, message = "Store description must not exceed 1000 characters")
    private String storeDescription;

    @Email(message = "Invalid email format")
    private String contactEmail;

    @Size(max = CONTACT_PHONE_MAX_LENGTH, message = "Contact phone must not exceed 20 characters")
    private String contactPhone;

    @Size(max = URL_MAX_LENGTH, message = "Logo URL must not exceed 500 characters")
    private String logoUrl;

    @Size(max = URL_MAX_LENGTH, message = "Cover image URL must not exceed 500 characters")
    private String coverImageUrl;

    // Sprint 146：值域比照 TenantApplicationRequest，此欄位先前完全未被 applyTenantUpdates 讀取
    // （見 TenantService），本輪一併補上寫入邏輯，故也補上與申請表單一致的值域驗證。
    @Pattern(regexp = "^(RETAIL_ONLY|BOOKING_ONLY|HYBRID)$", message = "Business type must be RETAIL_ONLY, BOOKING_ONLY, or HYBRID")
    private String businessType;

    private String status;

    /**
     * 採購單審批金額上限（PRD §6.7.2，Sprint 85）。null = 不變更此設定。
     */
    @DecimalMin(value = "0.0", message = "Purchase order approval threshold must not be negative")
    private BigDecimal purchaseOrderApprovalThreshold;
}