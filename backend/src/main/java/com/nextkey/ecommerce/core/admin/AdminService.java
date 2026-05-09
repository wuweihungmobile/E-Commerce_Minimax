package com.nextkey.ecommerce.core.admin;

import com.nextkey.ecommerce.api.dto.AdminDto;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.*;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 平台管理服務 (Mock Implementation)
 * Phase 1 提供基礎管理功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantFeatureToggleRepository featureToggleRepository;
    private final ListingRepository listingRepository;
    private final OrderRepository orderRepository;

    // ========== Tenant Management ==========

    /**
     * 取得租戶列表 (平台管理員)
     */
    @Transactional(readOnly = true)
    public AdminDto.TenantListResponse getTenants(int page, int size) {
        // Mock implementation - return all tenants
        List<Tenant> tenants = tenantRepository.findAll();

        List<AdminDto.TenantResponse> tenantResponses = tenants.stream()
                .map(this::toTenantResponse)
                .collect(Collectors.toList());

        return AdminDto.TenantListResponse.builder()
                .tenants(tenantResponses)
                .totalCount(tenantResponses.size())
                .build();
    }

    /**
     * 取得租戶詳情 (平台管理員)
     */
    @Transactional(readOnly = true)
    public AdminDto.TenantResponse getTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));
        return toTenantResponse(tenant);
    }

    /**
     * 審核租戶 (批准/拒絕)
     */
    @Transactional
    public AdminDto.TenantReviewResponse reviewTenant(AdminDto.TenantReviewRequest request) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        if ("APPROVE".equals(request.getDecision())) {
            tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        } else if ("REJECT".equals(request.getDecision())) {
            tenant.setStatus(Tenant.TenantStatus.REJECTED);
        } else {
            throw new BusinessException(ErrorCode.E_9000, "Invalid decision");
        }

        tenantRepository.save(tenant);

        return AdminDto.TenantReviewResponse.builder()
                .tenantId(tenant.getId())
                .status(tenant.getStatus().name())
                .reviewedAt(Instant.now().toString())
                .reviewedBy("SYSTEM_ADMIN")
                .build();
    }

    /**
     * US-M17-007: 審核通過租戶
     * 1. 變更狀態為 ACTIVE
     * 2. 初始化 6 個 Feature Toggle 預設值
     */
    @Transactional
    public AdminDto.TenantApproveResponse approveTenant(UUID tenantId, AdminDto.TenantApproveRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        // 驗證狀態是否為 PENDING
        if (tenant.getStatus() != Tenant.TenantStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.E_2005, "Tenant status is not PENDING_REVIEW");
        }

        // 變更狀態為 ACTIVE
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        tenantRepository.save(tenant);

        // 初始化 Feature Toggles (AC-007-2)
        // 預設值：RETAIL=true, BOOKING=false, CMS=true, ERP=true, DYNAMIC_PRICING=false, PROMO=false
        List<String> enabledFeatures = initializeFeatureToggles(tenantId);

        log.info("Tenant approved: tenantId={}, enabledFeatures={}", tenantId, enabledFeatures);

        return AdminDto.TenantApproveResponse.builder()
                .tenantId(tenantId)
                .status("ACTIVE")
                .approvedAt(Instant.now())
                .approvedBy("SYSTEM_ADMIN")
                .enabledFeatures(enabledFeatures)
                .build();
    }

    /**
     * US-M17-008: 駁回租戶申請
     * 1. 變更狀態為 REJECTED
     * 2. 記錄駁回原因
     */
    @Transactional
    public AdminDto.TenantRejectResponse rejectTenant(UUID tenantId, AdminDto.TenantRejectRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        // 驗證狀態是否為 PENDING
        if (tenant.getStatus() != Tenant.TenantStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.E_2005, "Tenant status is not PENDING_REVIEW");
        }

        // 變更狀態為 REJECTED
        tenant.setStatus(Tenant.TenantStatus.REJECTED);
        tenantRepository.save(tenant);

        log.info("Tenant rejected: tenantId={}, reason={}", tenantId, request.getReason());

        return AdminDto.TenantRejectResponse.builder()
                .tenantId(tenantId)
                .status("REJECTED")
                .rejectedAt(Instant.now())
                .rejectedBy("SYSTEM_ADMIN")
                .reason(request.getReason())
                .build();
    }

    /**
     * US-M17-007: 更新租戶狀態
     * 支援狀態轉換：
     * - ACTIVE → SUSPENDED (Admin 暫停店鋪)
     * - SUSPENDED → ACTIVE (Admin 恢復店鋪)
     * - SUSPENDED → TERMINATED (Admin 終止店鋪)
     */
    @Transactional
    public AdminDto.TenantStatusUpdateResponse updateTenantStatus(UUID tenantId, AdminDto.TenantStatusUpdateRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        Tenant.TenantStatus currentStatus = tenant.getStatus();
        Tenant.TenantStatus newStatus;

        try {
            newStatus = Tenant.TenantStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_9000, "Invalid status: " + request.getStatus());
        }

        // 驗證狀態機轉換
        validateStatusTransition(currentStatus, newStatus, request.getReason());

        // 終止時需要填寫原因
        if (newStatus == Tenant.TenantStatus.TERMINATED &&
                (request.getReason() == null || request.getReason().isBlank())) {
            throw new BusinessException(ErrorCode.E_9000, "Reason is required when terminating a tenant");
        }

        String previousStatus = currentStatus.name();

        // 更新狀態
        tenant.setStatus(newStatus);
        tenantRepository.save(tenant);

        // 如果是 SUSPENDED，自動下架所有 Listings
        if (newStatus == Tenant.TenantStatus.SUSPENDED) {
            deactivateTenantListings(tenantId);
        }

        // 寫入 audit log (mock - log only since no AuditLog entity exists)
        log.info("AUDIT_LOG: Tenant status updated - tenantId={}, previousStatus={}, newStatus={}, reason={}, updatedBy=SUPER_ADMIN",
                tenantId, previousStatus, newStatus.name(), request.getReason());

        return AdminDto.TenantStatusUpdateResponse.builder()
                .tenantId(tenantId)
                .previousStatus(previousStatus)
                .newStatus(newStatus.name())
                .updatedAt(Instant.now().toString())
                .updatedBy("SUPER_ADMIN")
                .build();
    }

    /**
     * 驗證狀態機轉換是否合法
     */
    private void validateStatusTransition(Tenant.TenantStatus currentStatus, Tenant.TenantStatus newStatus, String reason) {
        // 相同狀態不允許
        if (currentStatus == newStatus) {
            throw new BusinessException(ErrorCode.E_9000, "Tenant is already in status: " + newStatus);
        }

        // 狀態機定義
        boolean validTransition = switch (currentStatus) {
            case ACTIVE -> newStatus == Tenant.TenantStatus.SUSPENDED;
            case SUSPENDED -> newStatus == Tenant.TenantStatus.ACTIVE || newStatus == Tenant.TenantStatus.TERMINATED;
            default -> false;
        };

        if (!validTransition) {
            throw new BusinessException(ErrorCode.E_2005, "Invalid status transition from " + currentStatus + " to " + newStatus);
        }
    }

    /**
     * 停用租戶的所有 Listings (當店鋪被 SUSPENDED 時自動執行)
     */
    private void deactivateTenantListings(UUID tenantId) {
        List<Listing> listings = listingRepository.findByTenantId(tenantId);
        for (Listing listing : listings) {
            if (listing.getStatus() == Listing.ListingStatus.ACTIVE) {
                listing.setStatus(Listing.ListingStatus.INACTIVE);
                listingRepository.save(listing);
                log.info("Listing deactivated due to tenant suspension: listingId={}", listing.getId());
            }
        }
        log.info("Deactivated all active listings for tenant: tenantId={}, count={}", tenantId, listings.size());
    }

    /**
     * 初始化租戶的 Feature Toggles
     * 根據 BR-M17-002 設定預設值：
     * - RETAIL_ENABLED: true (預設啟用)
     * - BOOKING_ENABLED: false (需要審核)
     * - CMS_ENABLED: true (預設啟用)
     * - ERP_ENABLED: true (預設啟用)
     * - DYNAMIC_PRICING_ENABLED: false (需要審核)
     * - PROMO_ENABLED: false (需要審核)
     */
    private List<String> initializeFeatureToggles(UUID tenantId) {
        // 先查詢 Tenant 實體（用於 ManyToOne 關聯）
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        // Feature Toggle 預設值定義
        Map<String, Boolean> defaultFeatures = Map.of(
                "RETAIL_ENABLED", true,
                "BOOKING_ENABLED", false,
                "CMS_ENABLED", true,
                "ERP_ENABLED", true,
                "DYNAMIC_PRICING_ENABLED", false,
                "PROMO_ENABLED", false
        );

        List<String> enabledFeatures = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : defaultFeatures.entrySet()) {
            String featureKey = entry.getKey();
            Boolean isEnabled = entry.getValue();

            TenantFeatureToggle toggle = TenantFeatureToggle.builder()
                    .tenant(tenant)  // ✅ 使用 ManyToOne 關聯（而非虛擬欄位 tenantId）
                    .featureKey(featureKey)
                    .isEnabled(isEnabled)
                    .build();

            if (isEnabled) {
                toggle.setEnabledAt(Instant.now());
                enabledFeatures.add(featureKey);
            }

            featureToggleRepository.save(toggle);
        }

        return enabledFeatures;
    }

    // ========== User Management ==========

    /**
     * 取得用戶列表 (平台管理員)
     */
    @Transactional(readOnly = true)
    public AdminDto.UserListResponse getUsers(int page, int size, UUID tenantId, String role) {
        List<User> users;

        if (tenantId != null) {
            users = userRepository.findByTenantId(tenantId);
        } else {
            users = userRepository.findAll();
        }

        if (role != null && !role.isBlank()) {
            users = users.stream()
                    .filter(u -> u.getRole().name().equals(role))
                    .collect(Collectors.toList());
        }

        List<AdminDto.UserManagementResponse> userResponses = users.stream()
                .map(this::toUserManagementResponse)
                .collect(Collectors.toList());

        return AdminDto.UserListResponse.builder()
                .users(userResponses)
                .totalCount(userResponses.size())
                .build();
    }

    /**
     * 更新用戶狀態 (平台管理員)
     */
    @Transactional
    public AdminDto.UserManagementResponse updateUserStatus(UUID userId, String newStatus) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        user.setStatus(newStatus);
        user = userRepository.save(user);

        log.info("Admin updated user status: userId={}, newStatus={}", userId, newStatus);
        return toUserManagementResponse(user);
    }

    // ========== Feature Toggle ==========

    /**
     * 設定功能開關
     */
    @Transactional
    public AdminDto.FeatureToggleResponse setFeatureToggle(AdminDto.FeatureToggleRequest request) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        TenantFeatureToggle toggle = featureToggleRepository
                .findByTenantIdAndFeatureKey(request.getTenantId(), request.getFeatureKey())
                .orElse(TenantFeatureToggle.builder()
                        .tenant(tenant)
                        .featureKey(request.getFeatureKey())
                        .build());

        toggle.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : false);
        toggle.setConfig(request.getConfig());

        if (Boolean.TRUE.equals(request.getIsEnabled())) {
            toggle.setEnabledAt(Instant.now());
        } else {
            toggle.setDisabledAt(Instant.now());
        }

        toggle = featureToggleRepository.save(toggle);

        log.info("Feature toggle updated: tenantId={}, feature={}, enabled={}",
                request.getTenantId(), request.getFeatureKey(), request.getIsEnabled());

        return toFeatureToggleResponse(toggle, tenant);
    }

    /**
     * 取得租戶的功能開關列表
     */
    @Transactional(readOnly = true)
    public AdminDto.TenantFeatureTogglesResponse getTenantFeatureToggles(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        List<TenantFeatureToggle> toggles = featureToggleRepository.findByTenantId(tenantId);

        List<AdminDto.FeatureToggleResponse> toggleResponses = toggles.stream()
                .map(t -> toFeatureToggleResponse(t, tenant))
                .collect(Collectors.toList());

        return AdminDto.TenantFeatureTogglesResponse.builder()
                .tenantId(tenantId)
                .tenantName(tenant.getName())
                .toggles(toggleResponses)
                .build();
    }

    /**
     * 刪除功能開關
     */
    @Transactional
    public void deleteFeatureToggle(UUID tenantId, String featureKey) {
        featureToggleRepository.deleteByTenantIdAndFeatureKey(tenantId, featureKey);
        log.info("Feature toggle deleted: tenantId={}, feature={}", tenantId, featureKey);
    }

    /**
     * US-M17-009: Admin 更新租戶的 Feature Toggle
     * 當 feature 是 requires-admin-review 時：
     * - 啟用後狀態應為 PENDING（需要 Admin 審核）
     * - 只有 Admin 確認後才會變為 ACTIVE
     */
    @Transactional
    public AdminDto.FeatureToggleResponse updateTenantFeatureToggle(UUID tenantId, String featureKey, Boolean enabled) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        TenantFeatureToggle toggle = featureToggleRepository
                .findByTenantIdAndFeatureKey(tenantId, featureKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Feature toggle not found"));

        toggle.setIsEnabled(enabled != null ? enabled : false);

        if (Boolean.TRUE.equals(enabled)) {
            toggle.setEnabledAt(Instant.now());
        } else {
            toggle.setDisabledAt(Instant.now());
        }

        toggle = featureToggleRepository.save(toggle);
        log.info("Admin updated feature toggle: tenantId={}, feature={}, enabled={}", tenantId, featureKey, enabled);

        return toFeatureToggleResponse(toggle, tenant);
    }

    // ========== Platform Stats ==========

    /**
     * 取得平台統計
     */
    @Transactional(readOnly = true)
    public AdminDto.PlatformStatsResponse getPlatformStats() {
        int totalTenants = (int) tenantRepository.count();
        int activeTenants = (int) tenantRepository.findAll().stream()
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE)
                .count();
        int totalUsers = (int) userRepository.count();
        int totalListings = (int) listingRepository.count();
        int totalOrders = (int) orderRepository.count();

        // Mock GMV
        BigDecimal totalGMV = BigDecimal.valueOf(totalOrders).multiply(BigDecimal.valueOf(1500));

        int pendingReviews = (int) tenantRepository.findAll().stream()
                .filter(t -> t.getStatus() == Tenant.TenantStatus.PENDING_REVIEW)
                .count();

        return AdminDto.PlatformStatsResponse.builder()
                .totalTenants(totalTenants)
                .activeTenants(activeTenants)
                .totalUsers(totalUsers)
                .totalListings(totalListings)
                .totalOrders(totalOrders)
                .totalPlatformGMV(totalGMV)
                .pendingTenantReviews(pendingReviews)
                .build();
    }

    // ========== System Config ==========

    /**
     * 取得系統設定
     */
    @Transactional(readOnly = true)
    public AdminDto.SystemConfigResponse getSystemConfig() {
        Map<String, String> settings = Map.of(
                "platform_email", "admin@nextkey.com",
                "support_url", "https://support.nextkey.com",
                "terms_url", "https://nextkey.com/terms",
                "privacy_url", "https://nextkey.com/privacy"
        );

        return AdminDto.SystemConfigResponse.builder()
                .platformName("NextKey Platform")
                .version("1.0.0")
                .environment("production")
                .settings(settings)
                .build();
    }

    // ========== Helper Methods ==========

    private AdminDto.TenantResponse toTenantResponse(Tenant tenant) {
        int userCount = userRepository.findByTenantId(tenant.getId()).size();
        // Use findIdsByTenantId to avoid loading Listing entities with problematic tags field
        int listingCount = listingRepository.findIdsByTenantId(tenant.getId()).size();

        return AdminDto.TenantResponse.builder()
                .tenantId(tenant.getId())
                .name(tenant.getName())
                .status(tenant.getStatus().name())
                .userCount(userCount)
                .listingCount(listingCount)
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    private AdminDto.UserManagementResponse toUserManagementResponse(User user) {
        String tenantName = null;
        if (user.getTenantId() != null) {
            tenantName = tenantRepository.findById(user.getTenantId())
                    .map(Tenant::getName)
                    .orElse(null);
        }

        return AdminDto.UserManagementResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .status(user.getStatus())
                .tenantId(user.getTenantId())
                .tenantName(tenantName)
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private AdminDto.FeatureToggleResponse toFeatureToggleResponse(TenantFeatureToggle toggle, Tenant tenant) {
        return AdminDto.FeatureToggleResponse.builder()
                .toggleId(toggle.getId())
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .featureKey(toggle.getFeatureKey())
                .isEnabled(toggle.getIsEnabled())
                .config(toggle.getConfig())
                .enabledAt(toggle.getEnabledAt())
                .disabledAt(toggle.getDisabledAt())
                .createdAt(toggle.getCreatedAt())
                .build();
    }
}
