package com.nextkey.ecommerce.core.admin;

import com.nextkey.ecommerce.api.dto.AdminDto;
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
            tenant.setStatus(Tenant.TenantStatus.SUSPENDED);
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
        int listingCount = listingRepository.findByTenantId(tenant.getId()).size();

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
