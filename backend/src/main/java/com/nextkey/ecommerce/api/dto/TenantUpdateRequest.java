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

    // DEF-194（Sprint 155）：與 DEF-104（CmsDto Banner linkUrl）同型——有寫入/持久化（Tenant.logoUrl）
    // 且經公開端點 GET /v2/tenants/{id} 回傳，目前雖零前端消費端（非可利用），仍比照同批防禦性修復。
    @Size(max = URL_MAX_LENGTH, message = "Logo URL must not exceed 500 characters")
    @Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
            message = "Logo URL must not use javascript/data/vbscript/file protocol")
    private String logoUrl;

    // DEF-193（Sprint 155）：此欄位從未被 applyTenantUpdates 讀取、Tenant 實體無對應欄位，
    // getTenantDetails 恆回傳 null——完全死欄位（送出值必被靜默丟棄），非協定驗證缺口，
    // 故刻意不加 @Pattern（驗證一個永遠被丟棄的值沒有意義）。僅維持既有 @Size，如實記錄於此。
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