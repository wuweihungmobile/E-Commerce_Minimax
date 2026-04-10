package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.AdminDto;
import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.core.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
            @RequestParam(defaultValue = "20") int size) {
        AdminDto.TenantListResponse response = adminService.getTenants(page, size);
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
            @RequestParam(required = false) String role) {
        AdminDto.UserListResponse response = adminService.getUsers(page, size, tenantId, role);
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
     * 取得租戶的功能開關
     */
    @GetMapping("/tenants/{tenantId}/feature-toggles")
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
}
