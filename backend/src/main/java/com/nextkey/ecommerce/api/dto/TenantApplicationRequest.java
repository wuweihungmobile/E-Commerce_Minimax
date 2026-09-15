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

    // DEF-195（Sprint 155 發現 → Sprint 157 修復）：與 DEF-104/194/198 同型——
    // POST /tenant/apply 是前端 TenantApplyForm.tsx 實際會呼叫的活流程，TenantService 會將此欄位
    // 寫入並持久化至 TenantApplication.businessLicenseUrl；雖然官方表單本身未暴露此欄位輸入框、
    // 也沒有任何 Response DTO 讀回此值，但後端 API 對直接送出的 JSON payload 一視同仁，任何能呼叫
    // 此端點的使用者仍可繞過前端表單帶入任意字串，故仍比照同批防禦性修復。
    @Size(max = BUSINESS_LICENSE_URL_MAX_LENGTH, message = "Business license URL must not exceed 500 characters")
    @Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
            message = "Business license URL must not use javascript/data/vbscript/file protocol")
    private String businessLicenseUrl;
}