package com.nextkey.ecommerce.api.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.AdminDto;
import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.core.admin.AdminService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 平台管理 REST API (Mock Implementation)
 * 僅限 SUPER_ADMIN 權限
 */
@Slf4j
@RestController
@RequestMapping("/v2/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ========== Tenant Management ==========

    /**
     * 取得租戶列表
     */
    @GetMapping("/tenants")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.TenantListResponse>> getTenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) com.nextkey.ecommerce.domain.model.tenant.Tenant.TenantStatus status,
            @RequestParam(required = false) String keyword) {
        AdminDto.TenantListResponse response = adminService.getTenants(page, size, status, keyword);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得租戶詳情
     */
    @GetMapping("/tenants/{tenantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.TenantResponse>> getTenant(
            @PathVariable UUID tenantId) {
        AdminDto.TenantResponse response = adminService.getTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 審核租戶
     */
    @PostMapping("/tenants/review")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.TenantReviewResponse>> reviewTenant(
            @Valid @RequestBody AdminDto.TenantReviewRequest request) {
        log.info("Tenant review request: tenantId={}, decision={}",
                request.getTenantId(), request.getDecision());
        AdminDto.TenantReviewResponse response = adminService.reviewTenant(request);
        return ResponseEntity.ok(ApiResponse.success("Tenant reviewed successfully", response));
    }

    /**
     * 審核通過租戶 (US-M17-007)
     */
    @PostMapping("/tenants/{tenantId}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.TenantApproveResponse>> approveTenant(
            @PathVariable UUID tenantId,
            @RequestBody(required = false) AdminDto.TenantApproveRequest request) {
        log.info("Tenant approve request: tenantId={}", tenantId);
        AdminDto.TenantApproveResponse response = adminService.approveTenant(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Tenant approved successfully", response));
    }

    /**
     * 駁回租戶申請 (US-M17-008)
     */
    @PostMapping("/tenants/{tenantId}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.TenantRejectResponse>> rejectTenant(
            @PathVariable UUID tenantId,
            @Valid @RequestBody AdminDto.TenantRejectRequest request) {
        log.info("Tenant reject request: tenantId={}, reason={}", tenantId, request.getReason());
        AdminDto.TenantRejectResponse response = adminService.rejectTenant(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Tenant rejected successfully", response));
    }

    /**
     * 更新租戶狀態 (US-M17-007)
     * 支援：ACTIVE → SUSPENDED、SUSPENDED → ACTIVE、SUSPENDED → TERMINATED
     */
    @PutMapping("/tenants/{tenantId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.TenantStatusUpdateResponse>> updateTenantStatus(
            @PathVariable UUID tenantId,
            @Valid @RequestBody AdminDto.TenantStatusUpdateRequest request) {
        log.info("Update tenant status: tenantId={}, newStatus={}", tenantId, request.getStatus());
        AdminDto.TenantStatusUpdateResponse response = adminService.updateTenantStatus(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Tenant status updated successfully", response));
    }

    // ========== User Management ==========

    /**
     * 取得用戶列表
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.UserListResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        AdminDto.UserListResponse response = adminService.getUsers(page, size, tenantId, role, status, keyword);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 更新用戶狀態
     */
    @PutMapping("/users/{userId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.UserManagementResponse>> updateUserStatus(
            @PathVariable UUID userId,
            @RequestParam String status) {
        log.info("Update user status: userId={}, status={}", userId, status);
        AdminDto.UserManagementResponse response = adminService.updateUserStatus(userId, status);
        return ResponseEntity.ok(ApiResponse.success("User status updated", response));
    }

    // ========== Feature Toggle ==========

    /**
     * 設定功能開關
     */
    @PostMapping("/feature-toggles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.FeatureToggleResponse>> setFeatureToggle(
            @Valid @RequestBody AdminDto.FeatureToggleRequest request) {
        log.info("Set feature toggle: tenantId={}, feature={}, enabled={}",
                request.getTenantId(), request.getFeatureKey(), request.getIsEnabled());
        AdminDto.FeatureToggleResponse response = adminService.setFeatureToggle(request);
        return ResponseEntity.ok(ApiResponse.success("Feature toggle updated", response));
    }

    /**
     * US-M17-008: 取得租戶的功能開關 (Admin)
     * 路徑: GET /v2/admin/tenants/{tenantId}/features
     */
    @GetMapping("/tenants/{tenantId}/features")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.TenantFeatureTogglesResponse>> getTenantFeatureToggles(
            @PathVariable UUID tenantId) {
        AdminDto.TenantFeatureTogglesResponse response = adminService.getTenantFeatureToggles(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 刪除功能開關
     */
    @DeleteMapping("/tenants/{tenantId}/feature-toggles/{featureKey}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteFeatureToggle(
            @PathVariable UUID tenantId,
            @PathVariable String featureKey) {
        log.info("Delete feature toggle: tenantId={}, feature={}", tenantId, featureKey);
        adminService.deleteFeatureToggle(tenantId, featureKey);
        return ResponseEntity.ok(ApiResponse.success("Feature toggle deleted", null));
    }

    /**
     * US-M17-009: Admin 更新租戶的 Feature Toggle
     */
    @PutMapping("/tenants/{tenantId}/features/{feature}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.FeatureToggleResponse>> updateTenantFeatureToggle(
            @PathVariable UUID tenantId,
            @PathVariable String feature,
            @RequestBody Map<String, Boolean> request) {
        log.info("Admin update feature toggle: tenantId={}, feature={}, enabled={}",
                tenantId, feature, request.get("enabled"));
        Boolean enabled = request.get("enabled");
        AdminDto.FeatureToggleResponse response = adminService.updateTenantFeatureToggle(tenantId, feature, enabled);
        return ResponseEntity.ok(ApiResponse.success("Feature toggle updated", response));
    }

    // ========== Tenant Stats ==========

    /**
     * US-005 M14: 取得租戶活躍統計
     * 路徑: GET /v2/admin/tenants/{tenantId}/stats
     */
    @GetMapping("/tenants/{tenantId}/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.TenantStatsResponse>> getTenantStats(
            @PathVariable UUID tenantId) {
        AdminDto.TenantStatsResponse response = adminService.getTenantStats(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== Platform Stats ==========

    /**
     * 取得平台統計
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.PlatformStatsResponse>> getPlatformStats() {
        AdminDto.PlatformStatsResponse response = adminService.getPlatformStats();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== System Config ==========

    /**
     * 取得系統設定
     */
    @GetMapping("/system-config")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.SystemConfigResponse>> getSystemConfig() {
        AdminDto.SystemConfigResponse response = adminService.getSystemConfig();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== MAINTENANCE Warnings（PRD §5.5.3，Sprint 96） ==========

    /**
     * MaintenanceWarnings 列表：顯示所有受 MAINTENANCE 狀態影響的 Booking，供 Admin 人工通知房客
     * （M09 通知系統上線前的替代方案，TC-LO2-M17-003）。
     */
    @GetMapping("/maintenance-warnings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.MaintenanceWarningListResponse>> getMaintenanceWarnings() {
        AdminDto.MaintenanceWarningListResponse response = adminService.getMaintenanceWarnings();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== Audit Log（Sprint 61 US-001，DEF-016 後續） ==========

    /**
     * 查詢稽核紀錄（可依操作類型/時間範圍篩選、分頁）
     */
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.AuditLogListResponse>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        Instant startInstant = startDate != null ? startDate.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant endInstant = endDate != null ? endDate.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant() : null;
        AdminDto.AuditLogListResponse response = adminService.getAuditLogs(
                page, size, action, startInstant, endInstant);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== Purchase Order Approval（Sprint 85，PRD §6.7.2） ==========

    /**
     * 取得待審批採購單列表（跨租戶）
     */
    @GetMapping("/purchase-orders/pending")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.PurchaseOrderPendingListResponse>> getPendingApprovalPurchaseOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminDto.PurchaseOrderPendingListResponse response = adminService.getPendingApprovalPurchaseOrders(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 核准採購單
     */
    @PostMapping("/purchase-orders/{poId}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.PurchaseOrderSummaryResponse>> approvePurchaseOrder(
            @PathVariable UUID poId) {
        log.info("Purchase order approve request: poId={}", poId);
        AdminDto.PurchaseOrderSummaryResponse response = adminService.approvePurchaseOrder(poId);
        return ResponseEntity.ok(ApiResponse.success("Purchase order approved successfully", response));
    }

    /**
     * 駁回採購單
     */
    @PostMapping("/purchase-orders/{poId}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminDto.PurchaseOrderSummaryResponse>> rejectPurchaseOrder(
            @PathVariable UUID poId,
            @Valid @RequestBody AdminDto.PurchaseOrderRejectRequest request) {
        log.info("Purchase order reject request: poId={}, reason={}", poId, request.getReason());
        AdminDto.PurchaseOrderSummaryResponse response = adminService.rejectPurchaseOrder(poId, request);
        return ResponseEntity.ok(ApiResponse.success("Purchase order rejected successfully", response));
    }
}
