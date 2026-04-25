package com.nextkey.ecommerce.core.tenant;

import com.nextkey.ecommerce.api.dto.*;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantApplication;
import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.*;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantApplicationRepository tenantApplicationRepository;
    private final TenantFeatureToggleRepository tenantFeatureToggleRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserRepository userRepository;

    // Feature toggle definitions
    private static final Map<String, FeatureDefinition> FEATURE_DEFINITIONS = new LinkedHashMap<>();

    static {
        FEATURE_DEFINITIONS.put("RETAIL_ENABLED", new FeatureDefinition("RETAIL_ENABLED", "零售功能", "可上架實體商品", false, true));
        FEATURE_DEFINITIONS.put("BOOKING_ENABLED", new FeatureDefinition("BOOKING_ENABLED", "民宿預訂功能", "可上架民宿房間", true, false));
        FEATURE_DEFINITIONS.put("CMS_ENABLED", new FeatureDefinition("CMS_ENABLED", "CMS 貼文功能", "可發布 CMS 貼文", false, true));
        FEATURE_DEFINITIONS.put("ERP_ENABLED", new FeatureDefinition("ERP_ENABLED", "進銷存功能", "可使用進銷存管理", false, true));
        FEATURE_DEFINITIONS.put("DYNAMIC_PRICING_ENABLED", new FeatureDefinition("DYNAMIC_PRICING_ENABLED", "動態定價功能", "可使用動態定價引擎", true, false));
        FEATURE_DEFINITIONS.put("PROMO_ENABLED", new FeatureDefinition("PROMO_ENABLED", "促銷活動功能", "可建立促銷活動", true, false));
    }

    /**
     * US-M17-001: Create tenant application (store application)
     * Note: This endpoint allows Guest access (no authentication required)
     * Authenticated users cannot submit duplicate pending applications
     */
    @Transactional
    public TenantApplicationResponse createApplication(TenantApplicationRequest request, UUID userId) {
        // If user is authenticated (not Guest), check for duplicate pending applications
        if (userId != null) {
            if (tenantApplicationRepository.existsByUserIdAndStatusIn(userId,
                    List.of(TenantApplication.ApplicationStatus.PENDING))) {
                throw new BusinessException(ErrorCode.E_4092, "Store application already exists");
            }
        }

        // Create the application
        TenantApplication application = TenantApplication.builder()
                .userId(userId)
                .storeName(request.getStoreName())
                .storeDescription(request.getStoreDescription())
                .businessType(request.getBusinessType())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .businessLicenseUrl(request.getBusinessLicenseUrl())
                .status(TenantApplication.ApplicationStatus.PENDING)
                .submittedAt(Instant.now())
                .build();

        application = tenantApplicationRepository.save(application);
        log.info("Tenant application created: {} for user: {}", application.getId(), userId);

        return TenantApplicationResponse.builder()
                .applicationId(application.getId().toString())
                .storeName(application.getStoreName())
                .businessType(application.getBusinessType())
                .status(application.getStatus().name())
                .statusDescription(getStatusDescription(application.getStatus()))
                .submittedAt(application.getSubmittedAt())
                .estimatedReviewDays(3)
                .build();
    }

    /**
     * US-M17-002: Get my tenants list (tenants that user belongs to)
     */
    @Transactional(readOnly = true)
    public TenantListResponse getTenantsByUser() {
        UUID userId = TenantContext.getCurrentUser();
        if (userId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        // Get tenant memberships for this user
        List<TenantMember> memberships = tenantMemberRepository.findByUserId(userId);
        List<TenantListResponse> tenantList = new ArrayList<>();

        for (TenantMember member : memberships) {
            Tenant tenant = tenantRepository.findById(member.getTenantId()).orElse(null);
            if (tenant == null) {
                continue;
            }

            // Get feature toggles
            Map<String, Boolean> features = getFeatureMap(tenant.getId());

            TenantListResponse response = TenantListResponse.builder()
                    .tenantId(tenant.getId().toString())
                    .storeName(tenant.getName())
                    .businessType(tenant.getMetadata() != null && tenant.getMetadata().containsKey("businessType")
                            ? tenant.getMetadata().get("businessType").toString() : "RETAIL_ONLY")
                    .status(tenant.getStatus().name())
                    .role(member.getStoreRole().name())
                    .memberCount((int) tenantMemberRepository.countByTenantId(tenant.getId()))
                    .features(features)
                    .createdAt(tenant.getCreatedAt())
                    .build();

            tenantList.add(response);
        }

        return TenantListResponse.builder()
                .tenantId(null) // null indicates this is a list response
                .build();
    }

    /**
     * Alternative method that returns list directly
     */
    @Transactional(readOnly = true)
    public List<TenantListResponse> getTenantsListByUser() {
        UUID userId = TenantContext.getCurrentUser();
        if (userId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        // Get tenant memberships for this user
        List<TenantMember> memberships = tenantMemberRepository.findByUserId(userId);
        List<TenantListResponse> tenantList = new ArrayList<>();

        for (TenantMember member : memberships) {
            Tenant tenant = tenantRepository.findById(member.getTenantId()).orElse(null);
            if (tenant == null) {
                continue;
            }

            // Get feature toggles
            Map<String, Boolean> features = getFeatureMap(tenant.getId());

            String businessType = "RETAIL_ONLY";
            if (tenant.getMetadata() != null && tenant.getMetadata().containsKey("businessType")) {
                businessType = tenant.getMetadata().get("businessType").toString();
            }

            TenantListResponse response = TenantListResponse.builder()
                    .tenantId(tenant.getId().toString())
                    .storeName(tenant.getName())
                    .businessType(businessType)
                    .status(tenant.getStatus().name())
                    .role(member.getStoreRole().name())
                    .memberCount((int) tenantMemberRepository.countByTenantId(tenant.getId()))
                    .features(features)
                    .createdAt(tenant.getCreatedAt())
                    .build();

            tenantList.add(response);
        }

        return tenantList;
    }

    /**
     * US-M17-003: Get tenant details
     * For non-APPROVED/ACTIVE tenants, only return basic information
     */
    @Transactional(readOnly = true)
    public TenantDetailsResponse getTenantDetails(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        // For PENDING_REVIEW, REJECTED, SUSPENDED, TERMINATED status, return limited info
        boolean isApproved = tenant.getStatus() == Tenant.TenantStatus.ACTIVE;

        if (!isApproved) {
            return TenantDetailsResponse.builder()
                    .tenantId(tenant.getId().toString())
                    .storeName(tenant.getName())
                    .businessType(tenant.getMetadata() != null && tenant.getMetadata().containsKey("businessType")
                            ? tenant.getMetadata().get("businessType").toString() : "RETAIL_ONLY")
                    .status(tenant.getStatus().name())
                    .createdAt(tenant.getCreatedAt())
                    .build();
        }

        // Get tenant owner from tenant_members
        Optional<TenantMember> ownerMember = tenantMemberRepository.findByTenantId(tenantId).stream()
                .filter(m -> m.getStoreRole() == TenantMember.StoreRole.STORE_OWNER)
                .findFirst();

        TenantDetailsResponse.MemberInfo memberInfo = null;
        if (ownerMember.isPresent()) {
            User owner = userRepository.findById(ownerMember.get().getUserId()).orElse(null);
            if (owner != null) {
                memberInfo = TenantDetailsResponse.MemberInfo.builder()
                        .displayName(owner.getFullName())
                        .avatarUrl(owner.getAvatarUrl())
                        .joinedAt(ownerMember.get().getJoinedAt())
                        .build();
            }
        }

        return TenantDetailsResponse.builder()
                .tenantId(tenant.getId().toString())
                .storeName(tenant.getName())
                .storeDescription(tenant.getDescription())
                .businessType(tenant.getMetadata() != null && tenant.getMetadata().containsKey("businessType")
                        ? tenant.getMetadata().get("businessType").toString() : "RETAIL_ONLY")
                .status(tenant.getStatus().name())
                .contactEmail(tenant.getContactEmail())
                .logoUrl(tenant.getLogoUrl())
                .coverImageUrl(null) // tenants table doesn't have cover_image_url
                .member(memberInfo)
                .stats(null) // Stats would require additional queries
                .createdAt(tenant.getCreatedAt())
                .build();
    }

    /**
     * US-M17-004: Update tenant information
     * Note: Requires authentication (userId must not be null)
     */
    @Transactional
    public TenantUpdateResponse updateTenant(UUID tenantId, TenantUpdateRequest request, UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        // Verify user is owner of this tenant
        if (!tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(tenantId, userId, TenantMember.StoreRole.STORE_OWNER)) {
            throw new BusinessException(ErrorCode.E_4031, "Not authorized to update this store");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        // Update fields if provided
        if (request.getStoreName() != null) {
            tenant.setName(request.getStoreName());
        }
        if (request.getStoreDescription() != null) {
            tenant.setDescription(request.getStoreDescription());
        }
        if (request.getContactEmail() != null) {
            tenant.setContactEmail(request.getContactEmail());
        }
        if (request.getContactPhone() != null) {
            tenant.setContactPhone(request.getContactPhone());
        }
        if (request.getLogoUrl() != null) {
            tenant.setLogoUrl(request.getLogoUrl());
        }

        tenant = tenantRepository.save(tenant);
        log.info("Tenant updated: {}", tenantId);

        return TenantUpdateResponse.builder()
                .tenantId(tenant.getId().toString())
                .storeName(tenant.getName())
                .storeDescription(tenant.getDescription())
                .contactEmail(tenant.getContactEmail())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    /**
     * US-M17-005: Get feature toggles for current tenant
     */
    @Transactional(readOnly = true)
    public FeatureToggleResponse getFeatureToggles(UUID tenantId) {
        // Verify user belongs to this tenant
        UUID userId = TenantContext.getCurrentUser();
        if (userId != null && !tenantMemberRepository.existsByTenantIdAndUserId(tenantId, userId)) {
            throw new BusinessException(ErrorCode.E_4031, "Not authorized to view this store's features");
        }

        List<TenantFeatureToggle> toggles = tenantFeatureToggleRepository.findByTenantId(tenantId);
        Map<String, TenantFeatureToggle> toggleMap = new HashMap<>();
        for (TenantFeatureToggle toggle : toggles) {
            toggleMap.put(toggle.getFeatureKey(), toggle);
        }

        List<FeatureToggleResponse.FeatureInfo> features = new ArrayList<>();
        for (Map.Entry<String, FeatureDefinition> entry : FEATURE_DEFINITIONS.entrySet()) {
            String key = entry.getKey();
            FeatureDefinition def = entry.getValue();
            TenantFeatureToggle toggle = toggleMap.get(key);

            features.add(FeatureToggleResponse.FeatureInfo.builder()
                    .featureKey(key)
                    .featureName(def.name)
                    .description(def.description)
                    .isEnabled(toggle != null ? toggle.getIsEnabled() : def.defaultEnabled)
                    .enabledAt(toggle != null ? toggle.getEnabledAt() : null)
                    .requestedAt(toggle != null && !toggle.getIsEnabled() ? toggle.getCreatedAt() : null)
                    .build());
        }

        return FeatureToggleResponse.builder()
                .tenantId(tenantId.toString())
                .features(features)
                .build();
    }

    /**
     * US-M17-006: Update feature toggle
     */
    @Transactional
    public FeatureToggleUpdateResponse updateFeatureToggle(UUID tenantId, String featureKey, Boolean enabled) {
        UUID userId = TenantContext.getCurrentUser();
        if (userId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        // Verify user is owner of this tenant
        if (!tenantMemberRepository.existsByTenantIdAndUserId(tenantId, userId)) {
            throw new BusinessException(ErrorCode.E_4031, "Not authorized to update this store's features");
        }

        FeatureDefinition featureDef = FEATURE_DEFINITIONS.get(featureKey);
        if (featureDef == null) {
            throw new BusinessException(ErrorCode.E_9000, "Unknown feature: " + featureKey);
        }

        TenantFeatureToggle toggle = tenantFeatureToggleRepository
                .findByTenantIdAndFeatureKey(tenantId, featureKey)
                .orElse(null);

        Boolean previousState = toggle != null ? toggle.getIsEnabled() : featureDef.defaultEnabled;

        if (toggle == null) {
            toggle = TenantFeatureToggle.builder()
                    .tenantId(tenantId)
                    .featureKey(featureKey)
                    .isEnabled(enabled)
                    .build();
        } else {
            toggle.setIsEnabled(enabled);
        }

        // If enabling, set enabledAt; if disabling, set disabledAt
        if (enabled && !previousState) {
            toggle.setEnabledAt(Instant.now());
        }

        toggle = tenantFeatureToggleRepository.save(toggle);

        // Determine status based on enabled state and requiresApproval
        String status;
        String statusDescription;

        if (enabled) {
            // If feature requires approval and was previously disabled/not enabled,
            // status should be PENDING_APPROVAL (waiting for platform review)
            if (featureDef.requiresApproval && !previousState) {
                status = "PENDING_APPROVAL";
                statusDescription = "Feature request submitted, pending platform approval";
            } else {
                status = "ENABLED";
                statusDescription = "Feature enabled";
            }
        } else {
            // If disabling, check if we need to go through approval process
            if (previousState) {
                status = "DISABLED";
                statusDescription = "Feature disabled";
            } else {
                status = "PENDING_APPROVAL";
                statusDescription = "Feature request submitted";
            }
        }

        return FeatureToggleUpdateResponse.builder()
                .featureKey(featureKey)
                .previousState(previousState)
                .newState(toggle.getIsEnabled())
                .status(status)
                .statusDescription(statusDescription)
                .build();
    }

    // Helper methods
    private Map<String, Boolean> getFeatureMap(UUID tenantId) {
        List<TenantFeatureToggle> toggles = tenantFeatureToggleRepository.findByTenantId(tenantId);
        Map<String, Boolean> features = new HashMap<>();

        for (TenantFeatureToggle toggle : toggles) {
            features.put(toggle.getFeatureKey(), toggle.getIsEnabled());
        }

        // Apply defaults for missing features
        for (Map.Entry<String, FeatureDefinition> entry : FEATURE_DEFINITIONS.entrySet()) {
            if (!features.containsKey(entry.getKey())) {
                features.put(entry.getKey(), entry.getValue().defaultEnabled);
            }
        }

        return features;
    }

    private String getStatusDescription(TenantApplication.ApplicationStatus status) {
        return switch (status) {
            case PENDING -> "申請已提交，等待平台審核";
            case APPROVED -> "審核通過";
            case REJECTED -> "審核駁回";
            case SUSPENDED -> "已停權";
        };
    }

    // Inner class for feature definitions
    private static class FeatureDefinition {
        final String key;
        final String name;
        final String description;
        final boolean requiresApproval;
        final boolean defaultEnabled;

        FeatureDefinition(String key, String name, String description, boolean requiresApproval, boolean defaultEnabled) {
            this.key = key;
            this.name = name;
            this.description = description;
            this.requiresApproval = requiresApproval;
            this.defaultEnabled = defaultEnabled;
        }
    }
}