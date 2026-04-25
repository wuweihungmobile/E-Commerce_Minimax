package com.nextkey.ecommerce.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * User Info Response DTO
 * 用於 GET /v2/auth/me 回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    /**
     * 用戶 ID
     */
    private UUID userId;

    /**
     * 用戶 Email
     */
    private String email;

    /**
     * 用戶類型 (BUYER/SELLER/HOST)
     */
    private String userType;

    /**
     * 帳號狀態 (ACTIVE/SUSPENDED)
     */
    private String status;

    /**
     * Email 是否已驗證
     */
    private Boolean emailVerified;

    /**
     * 用戶 profile 資訊
     */
    private Profile profile;

    /**
     * 用戶所屬的租戶列表
     */
    private List<TenantInfo> tenants;

    /**
     * 用戶創建時間
     */
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Profile {
        /**
         * 顯示名稱
         */
        private String displayName;

        /**
         * 電話
         */
        private String phone;

        /**
         * 頭像 URL
         */
        private String avatarUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantInfo {
        /**
         * 租戶 ID
         */
        private UUID tenantId;

        /**
         * 租戶名稱
         */
        private String tenantName;

        /**
         * 用戶在該租戶中的角色
         */
        private String role;
    }
}
