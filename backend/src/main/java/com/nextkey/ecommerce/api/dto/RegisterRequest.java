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
public class RegisterRequest {

    // Validation constraints
    private static final int PASSWORD_MAX_LENGTH = 128;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = PASSWORD_MAX_LENGTH, message = "Password must be between 8 and 128 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "Password must contain uppercase, lowercase and numeric characters"
    )
    private String password;

    private String fullName;

    private String phone;

    /**
     * 會員類型：BUYER / SELLER / HOST，預設 BUYER。
     *
     * <p>DEF-244：先前也接受 STORE_OWNER/STORE_STAFF，且另有一個 {@code tenantId} 欄位——只要
     * 帶入任一已存在的租戶 UUID，{@code AuthService.register} 會無條件在 tenant_members 寫入一筆
     * {@code storeRole=STORE_OWNER} 的真實成員紀錄，等同任何未經驗證的訪客都能透過這個完全公開、
     * 無需登入的端點直接奪取任一店鋪的真實管理權限。STORE_OWNER 只能透過既有的
     * {@code TenantApplicationRequest} → {@code AdminService.approveTenantApplication}
     * 審核流程取得；STORE_STAFF 只能透過既有店主的邀請流程加入，皆不應由此公開端點直接授予。
     */
    @Pattern(regexp = "^(BUYER|SELLER|HOST)$", message = "userType must be BUYER, SELLER, or HOST")
    private String userType;
}
