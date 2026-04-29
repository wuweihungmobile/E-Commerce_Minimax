package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 平台管理 DTO
 */
public class AdminDto {

    // ========== Tenant Management ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantResponse {
        private UUID tenantId;
        private String name;
        private String status;
        private Integer userCount;
        private Integer listingCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantListResponse {
        private List<TenantResponse> tenants;
        private int totalCount;
    }

    // ========== User Management ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserManagementResponse {
        private UUID userId;
        private String email;
        private String fullName;
        private String role;
        private String status;
        private UUID tenantId;
        private String tenantName;
        private Instant createdAt;
        private Instant lastLoginAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserListResponse {
        private List<UserManagementResponse> users;
        private int totalCount;
    }

    // ========== Feature Toggle ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureToggleRequest {
        @NotNull(message = "Tenant ID is required")
        private UUID tenantId;

        @NotBlank(message = "Feature key is required")
        private String featureKey;

        private Boolean isEnabled;

        private Map<String, Object> config;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureToggleResponse {
        private UUID toggleId;
        private UUID tenantId;
        private String tenantName;
        private String featureKey;
        private Boolean isEnabled;
        private Map<String, Object> config;
        private Instant enabledAt;
        private Instant disabledAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantFeatureTogglesResponse {
        private UUID tenantId;
        private String tenantName;
        private List<FeatureToggleResponse> toggles;
    }

    // ========== Platform Stats ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformStatsResponse {
        private Integer totalTenants;
        private Integer activeTenants;
        private Integer totalUsers;
        private Integer totalListings;
        private Integer totalOrders;
        private BigDecimal totalPlatformGMV;
        private Integer pendingTenantReviews;
    }

    // ========== Tenant Review ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantReviewRequest {
        @NotNull(message = "Tenant ID is required")
        private UUID tenantId;

        @NotBlank(message = "Decision is required")
        private String decision; // APPROVE, REJECT

        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantReviewResponse {
        private UUID tenantId;
        private String status;
        private String reviewedAt;
        private String reviewedBy;
    }

    // ========== Tenant Approve ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantApproveRequest {
        private List<String> approvedFeatures; // Optional: specific features to enable beyond defaults

        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantApproveResponse {
        private UUID tenantId;
        private String status;
        private Instant approvedAt;
        private String approvedBy;
        private List<String> enabledFeatures;
    }

    // ========== Tenant Reject ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantRejectRequest {
        @NotBlank(message = "Reason is required")
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantRejectResponse {
        private UUID tenantId;
        private String status;
        private Instant rejectedAt;
        private String rejectedBy;
        private String reason;
    }

    // ========== System Config ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemConfigResponse {
        private String platformName;
        private String version;
        private String environment;
        private Map<String, String> settings;
    }
}
