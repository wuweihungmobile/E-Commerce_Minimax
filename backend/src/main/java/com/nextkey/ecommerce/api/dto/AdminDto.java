package com.nextkey.ecommerce.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;

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
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
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
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
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

    // ========== Tenant Status Update ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantStatusUpdateRequest {
        @NotBlank(message = "Status is required")
        @jakarta.validation.constraints.Pattern(regexp = "^(SUSPENDED|ACTIVE|TERMINATED)$",
                message = "Status must be SUSPENDED, ACTIVE, or TERMINATED")
        private String status;

        private String reason; // Required when terminating
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantStatusUpdateResponse {
        private UUID tenantId;
        private String previousStatus;
        private String newStatus;
        private String updatedAt;
        private String updatedBy;
    }

    // ========== Tenant Stats ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantStatsResponse {
        private UUID tenantId;
        private long orderCount30d;
        private long activeUserCount;
        private long activeListingCount;
        private Instant lastOrderAt;
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

    // ========== Audit Log（Sprint 61 US-001，DEF-016 後續） ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogResponse {
        private UUID id;
        private UUID tenantId;
        private UUID userId;
        private String action;
        private String entityType;
        private UUID entityId;
        private String oldValue;
        private String newValue;
        private String reason;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogListResponse {
        private List<AuditLogResponse> logs;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    // ========== Purchase Order Approval (Sprint 85, PRD §6.7.2) ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderSummaryResponse {
        private UUID id;
        private UUID tenantId;
        private String poNumber;
        private String status;
        private BigDecimal totalAmount;
        private String currency;
        private Instant submittedAt;
        private UUID reviewedBy;
        private Instant reviewedAt;
        private String rejectionReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderPendingListResponse {
        private List<PurchaseOrderSummaryResponse> purchaseOrders;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    // ========== MAINTENANCE Warnings（PRD §5.5.3，M09 通知系統上線前的替代方案）==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaintenanceWarningResponse {
        private UUID bookingId;
        private LocalDate checkInDate;
        private String guestEmail;
        private boolean urgent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaintenanceWarningListResponse {
        private List<MaintenanceWarningResponse> warnings;
    }

    // ========== Tenant Application Review（PRD §7.4.1，Sprint 97）==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantApplicationSummaryResponse {
        private UUID applicationId;
        private UUID userId;
        private String storeName;
        private String storeDescription;
        private String businessType;
        private String contactEmail;
        private String contactPhone;
        private String status;
        private Instant submittedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantApplicationListResponse {
        private List<TenantApplicationSummaryResponse> applications;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantApplicationApproveResponse {
        private UUID applicationId;
        private UUID tenantId;
        private String status;
        private Instant approvedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantApplicationRejectRequest {
        @NotBlank(message = "Rejection reason is required")
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantApplicationRejectResponse {
        private UUID applicationId;
        private String status;
        private Instant rejectedAt;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderRejectRequest {
        @NotBlank(message = "Reason is required")
        private String reason;
    }
}
