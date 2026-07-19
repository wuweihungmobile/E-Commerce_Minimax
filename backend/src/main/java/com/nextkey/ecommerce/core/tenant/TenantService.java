package com.nextkey.ecommerce.core.tenant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.FeatureToggleResponse;
import com.nextkey.ecommerce.api.dto.FeatureToggleUpdateResponse;
import com.nextkey.ecommerce.api.dto.TenantApplicationRequest;
import com.nextkey.ecommerce.api.dto.TenantApplicationResponse;
import com.nextkey.ecommerce.api.dto.TenantDetailsResponse;
import com.nextkey.ecommerce.api.dto.TenantInviteResponse;
import com.nextkey.ecommerce.api.dto.TenantListResponse;
import com.nextkey.ecommerce.api.dto.TenantMemberResponse;
import com.nextkey.ecommerce.api.dto.TenantUpdateRequest;
import com.nextkey.ecommerce.api.dto.TenantUpdateResponse;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantApplication;
import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.TenantApplicationRepository;
import com.nextkey.ecommerce.domain.repository.TenantFeatureToggleRepository;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    // Numeric toggle defaults
    private static final int DEFAULT_MAX_PRODUCTS = 100;
    private static final int DEFAULT_MAX_ROOMS = 20;
    private static final int DEFAULT_MAX_POSTS = 50;
    private static final double DEFAULT_COMMISSION_RATE = 0.05;

    static {
        // REF: Sprint 7 Plan L109-120, Sprint 7 User Stories L131-142
        // Default values based on HYBRID (all toggles enabled by default for new stores)
        FEATURE_DEFINITIONS.put("RETAIL_ENABLED", new FeatureDefinition("零售功能", "可上架實體商品", false, true));
        FEATURE_DEFINITIONS.put("BOOKING_ENABLED", new FeatureDefinition("民宿預訂功能", "可上架民宿房間", true, true));
        FEATURE_DEFINITIONS.put("CMS_ENABLED", new FeatureDefinition("CMS 貼文功能", "可發布 CMS 貼文", false, true));
        FEATURE_DEFINITIONS.put("ERP_ENABLED", new FeatureDefinition("進銷存功能", "可使用進銷存管理", false, true));
        FEATURE_DEFINITIONS.put("DYNAMIC_PRICING_ENABLED", new FeatureDefinition("動態定價功能", "可使用動態定價引擎", true, false));
        FEATURE_DEFINITIONS.put("PROMO_ENABLED", new FeatureDefinition("促銷活動功能", "可建立促銷活動", true, false));
        // Numeric toggles (stored as JSONB config)
        FEATURE_DEFINITIONS.put("MAX_PRODUCTS", new FeatureDefinition("最大商品數", "店鋪可上架商品數上限", false, DEFAULT_MAX_PRODUCTS));
        FEATURE_DEFINITIONS.put("MAX_ROOMS", new FeatureDefinition("最大房源數", "店鋪可上架房源數上限", false, DEFAULT_MAX_ROOMS));
        FEATURE_DEFINITIONS.put("MAX_POSTS", new FeatureDefinition("最大貼文數", "店鋪可發布貼文數上限", false, DEFAULT_MAX_POSTS));
        FEATURE_DEFINITIONS.put("COMMISSION_RATE", new FeatureDefinition("抽佣比例", "平台抽佣比例", false, DEFAULT_COMMISSION_RATE));
    }

    /**
     * US-M17-001: Create tenant application (store application)
     * Note: This endpoint allows Guest access (no authentication required)
     * Authenticated users cannot submit duplicate pending applications
     */
    @Transactional
    public TenantApplicationResponse createApplication(final TenantApplicationRequest request, final UUID userId) {
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
    public TenantDetailsResponse getTenantDetails(final UUID tenantId) {
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
                .purchaseOrderApprovalThreshold(tenant.getPurchaseOrderApprovalThreshold())
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
    public TenantUpdateResponse updateTenant(final UUID tenantId, final TenantUpdateRequest request, final UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        // Verify user is owner of this tenant
        if (!tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(tenantId, userId, TenantMember.StoreRole.STORE_OWNER)) {
            throw new BusinessException(ErrorCode.E_4031, "Not authorized to update this store");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000));

        applyTenantUpdates(tenant, request);

        tenant = tenantRepository.save(tenant);
        log.info("Tenant updated: {}", tenantId);

        return TenantUpdateResponse.builder()
                .tenantId(tenant.getId().toString())
                .storeName(tenant.getName())
                .storeDescription(tenant.getDescription())
                .contactEmail(tenant.getContactEmail())
                .purchaseOrderApprovalThreshold(tenant.getPurchaseOrderApprovalThreshold())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    /**
     * 套用 {@link TenantUpdateRequest} 中非 null 的欄位到 {@link Tenant}（US-M17-004）。
     */
    private void applyTenantUpdates(final Tenant tenant, final TenantUpdateRequest request) {
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
        if (request.getPurchaseOrderApprovalThreshold() != null) {
            tenant.setPurchaseOrderApprovalThreshold(request.getPurchaseOrderApprovalThreshold());
        }
    }

    /**
     * US-M17-005: Get feature toggles for current tenant
     */
    @Transactional(readOnly = true)
    public FeatureToggleResponse getFeatureToggles(final UUID tenantId) {
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
                    .isEnabled(toggle != null ? toggle.getIsEnabled() : def.booleanDefault)
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
    public FeatureToggleUpdateResponse updateFeatureToggle(final UUID tenantId, final String featureKey, final Boolean enabled) {
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

        Boolean previousState = toggle != null ? toggle.getIsEnabled() : featureDef.booleanDefault;

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
        StatusInfo statusInfo = determineToggleStatus(enabled, featureDef.requiresApproval, previousState);

        return FeatureToggleUpdateResponse.builder()
                .featureKey(featureKey)
                .previousState(previousState)
                .newState(toggle.getIsEnabled())
                .status(statusInfo.status)
                .statusDescription(statusInfo.description)
                .build();
    }

    private record StatusInfo(String status, String description) {}

    private StatusInfo determineToggleStatus(boolean enabled, boolean requiresApproval, Boolean previousState) {
        if (enabled) {
            if (requiresApproval && !previousState) {
                return new StatusInfo("PENDING_APPROVAL", "Feature request submitted, pending platform approval");
            }
            return new StatusInfo("ENABLED", "Feature enabled");
        } else {
            if (previousState) {
                return new StatusInfo("DISABLED", "Feature disabled");
            }
            return new StatusInfo("PENDING_APPROVAL", "Feature request submitted");
        }
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
                FeatureDefinition def = entry.getValue();
                features.put(entry.getKey(), def.isBoolean ? def.booleanDefault : false);
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
        final String name;
        final String description;
        final boolean requiresApproval;
        final boolean isBoolean;  // true for boolean toggle, false for numeric toggle
        final boolean booleanDefault;  // default value for boolean toggles

        // Boolean toggle constructor
        FeatureDefinition(String name, String description, boolean requiresApproval, boolean defaultEnabled) {
            this.name = name;
            this.description = description;
            this.requiresApproval = requiresApproval;
            this.isBoolean = true;
            this.booleanDefault = defaultEnabled;
        }

        // Numeric toggle constructor
        FeatureDefinition(String name, String description, boolean requiresApproval, double defaultValue) {
            this.name = name;
            this.description = description;
            this.requiresApproval = requiresApproval;
            this.isBoolean = false;
            this.booleanDefault = false;
        }
    }

    /**
     * US-M17-006: Get members of a tenant（含待確認邀請，狀態見 status 欄位；已移除者不回傳）
     */
    @Transactional(readOnly = true)
    public List<TenantMemberResponse> getTenantMembers(final UUID tenantId) {
        // Verify user belongs to this tenant
        UUID userId = TenantContext.getCurrentUser();
        if (userId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        if (!tenantMemberRepository.existsByTenantIdAndUserId(tenantId, userId)) {
            throw new BusinessException(ErrorCode.E_4031, "Not authorized to view this store's members");
        }

        List<TenantMember> members = tenantMemberRepository.findByTenantIdAndStatusNot(
                tenantId, TenantMember.MemberStatus.REMOVED);
        List<TenantMemberResponse> responseList = new ArrayList<>();

        for (TenantMember member : members) {
            User user = userRepository.findById(member.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            responseList.add(toMemberResponse(member, user));
        }

        return responseList;
    }

    /**
     * PRD §7.4/§9.11 邀請成員加入店鋪：建立 status=INVITED 的成員紀錄，需被邀請人呼叫
     * {@link #acceptInvite} 確認後才真正生效（ACTIVE）。若曾被移除/拒絕過（REMOVED），
     * 更新既有紀錄而非新增（{@code (tenant_id, user_id)} 有 UNIQUE 約束）。
     */
    @Transactional
    public TenantMemberResponse inviteMember(final UUID tenantId, final UUID userId, final String requestedRole,
                                              final UUID invitedBy) {
        UUID currentUserId = TenantContext.getCurrentUser();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        if (!tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(tenantId, currentUserId, TenantMember.StoreRole.STORE_OWNER)) {
            throw new BusinessException(ErrorCode.E_4031, "Not authorized to invite members");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2001, "User not found"));

        TenantMember.StoreRole storeRole = parseInviteRole(requestedRole);

        Optional<TenantMember> existing = tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId);
        TenantMember member;
        if (existing.isPresent()) {
            member = existing.get();
            if (member.getStatus() != TenantMember.MemberStatus.REMOVED) {
                throw new BusinessException(ErrorCode.E_4092, "User is already a member or has a pending invite");
            }
            member.setStatus(TenantMember.MemberStatus.INVITED);
            member.setStoreRole(storeRole);
            member.setInvitedBy(invitedBy);
            member.setInvitedAt(Instant.now());
            member.setJoinedAt(null);
        } else {
            member = TenantMember.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .storeRole(storeRole)
                    .status(TenantMember.MemberStatus.INVITED)
                    .invitedBy(invitedBy)
                    .build();
        }

        member = tenantMemberRepository.save(member);
        log.info("Member invited to tenant: tenantId={}, userId={}, role={}, invitedBy={}",
                tenantId, userId, storeRole, invitedBy);

        return toMemberResponse(member, user);
    }

    /**
     * 被邀請人接受邀請 → 狀態轉為 ACTIVE，正式成為店鋪成員。
     */
    @Transactional
    public TenantMemberResponse acceptInvite(final UUID tenantId) {
        UUID currentUserId = TenantContext.getCurrentUser();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenantId, currentUserId)
                .filter(m -> m.getStatus() == TenantMember.MemberStatus.INVITED)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2002, "Invite not found"));

        member.setStatus(TenantMember.MemberStatus.ACTIVE);
        member.setJoinedAt(Instant.now());
        member = tenantMemberRepository.save(member);
        log.info("Member accepted invite: tenantId={}, userId={}", tenantId, currentUserId);

        User user = userRepository.findById(currentUserId).orElse(null);
        // PRD §7.4.1：JWT roles 需包含新角色，供下次登入/換發 token 時正確授權
        // （比照 Sprint 98 對 approveTenantApplication 的修復：關聯表更新不等於 JWT 授權來源同步）。
        if (user != null && member.getStoreRole() == TenantMember.StoreRole.STORE_STAFF) {
            user.setRole(User.UserRole.STORE_STAFF);
            userRepository.save(user);
        }
        return toMemberResponse(member, user);
    }

    /**
     * 被邀請人拒絕邀請 → 軟刪除（狀態轉為 REMOVED），保留紀錄供未來重新邀請時更新同一列。
     */
    @Transactional
    public void declineInvite(final UUID tenantId) {
        UUID currentUserId = TenantContext.getCurrentUser();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenantId, currentUserId)
                .filter(m -> m.getStatus() == TenantMember.MemberStatus.INVITED)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2002, "Invite not found"));

        member.setStatus(TenantMember.MemberStatus.REMOVED);
        tenantMemberRepository.save(member);
        log.info("Member declined invite: tenantId={}, userId={}", tenantId, currentUserId);
    }

    /**
     * 我的待確認邀請列表（跨租戶）。
     */
    @Transactional(readOnly = true)
    public List<TenantInviteResponse> getMyPendingInvites() {
        UUID currentUserId = TenantContext.getCurrentUser();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        List<TenantMember> invites = tenantMemberRepository.findByUserIdAndStatus(
                currentUserId, TenantMember.MemberStatus.INVITED);

        List<TenantInviteResponse> responseList = new ArrayList<>();
        for (TenantMember member : invites) {
            Tenant tenant = tenantRepository.findById(member.getTenantId()).orElse(null);
            responseList.add(TenantInviteResponse.builder()
                    .memberId(member.getId().toString())
                    .tenantId(member.getTenantId().toString())
                    .tenantName(tenant != null ? tenant.getName() : null)
                    .role(member.getStoreRole().name())
                    .invitedAt(member.getInvitedAt())
                    .build());
        }
        return responseList;
    }

    private TenantMember.StoreRole parseInviteRole(final String requestedRole) {
        if (requestedRole == null || requestedRole.isBlank()) {
            return TenantMember.StoreRole.STORE_STAFF;
        }
        TenantMember.StoreRole storeRole;
        try {
            storeRole = TenantMember.StoreRole.valueOf(requestedRole);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_1001, "Invalid role: " + requestedRole);
        }
        if (storeRole != TenantMember.StoreRole.STORE_STAFF) {
            // STORE_OWNER 僅能透過開店審核流程產生（Sprint 97）；STORE_MANAGER 在 User.UserRole
            // 沒有對應值也未定義於 RolePermissionMapping（PRD 亦未提及），邀請後 JWT 永遠拿不到
            // 對應權限，等同重現 Sprint 98/99 修復的「角色授予未同步 JWT」問題，故一併拒絕。
            throw new BusinessException(ErrorCode.E_1001, "Only STORE_STAFF can be invited via this endpoint");
        }
        return storeRole;
    }

    private TenantMemberResponse toMemberResponse(final TenantMember member, final User user) {
        return TenantMemberResponse.builder()
                .userId(member.getUserId().toString())
                .displayName(user != null ? user.getFullName() : "")
                .email(user != null ? user.getEmail() : "")
                .avatarUrl(user != null ? user.getAvatarUrl() : null)
                .role(member.getStoreRole().name())
                .status(member.getStatus().name())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    /**
     * US-M17-006: Update member role
     */
    @Transactional
    public TenantMemberResponse updateMemberRole(final UUID tenantId, final UUID userId, final String newRole) {
        // Verify current user is StoreOwner of this tenant
        UUID currentUserId = TenantContext.getCurrentUser();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        if (!tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(tenantId, currentUserId, TenantMember.StoreRole.STORE_OWNER)) {
            throw new BusinessException(ErrorCode.E_4031, "Not authorized to update member roles");
        }

        TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .filter(m -> m.getStatus() != TenantMember.MemberStatus.REMOVED)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2002, "Member not found"));

        // Parse and validate role
        TenantMember.StoreRole storeRole;
        try {
            storeRole = TenantMember.StoreRole.valueOf(newRole);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.E_1001, "Invalid role: " + newRole);
        }

        // Cannot change the original StoreOwner's role
        if (member.getStoreRole() == TenantMember.StoreRole.STORE_OWNER) {
            throw new BusinessException(ErrorCode.E_4031, "Cannot change the owner's role");
        }

        member.setStoreRole(storeRole);
        member = tenantMemberRepository.save(member);
        log.info("Member role updated: tenantId={}, userId={}, newRole={}", tenantId, userId, newRole);

        User user = userRepository.findById(userId).orElse(null);
        return toMemberResponse(member, user);
    }

    /**
     * US-M17-006: Remove member from tenant（軟刪除：狀態轉為 REMOVED，保留紀錄供未來重新邀請）
     */
    @Transactional
    public void removeMember(final UUID tenantId, final UUID userId) {
        // Verify current user is StoreOwner of this tenant
        UUID currentUserId = TenantContext.getCurrentUser();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.E_1000);
        }

        if (!tenantMemberRepository.existsByTenantIdAndUserIdAndStoreRole(tenantId, currentUserId, TenantMember.StoreRole.STORE_OWNER)) {
            throw new BusinessException(ErrorCode.E_4031, "Not authorized to remove members");
        }

        TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId)
                .filter(m -> m.getStatus() != TenantMember.MemberStatus.REMOVED)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2002, "Member not found"));

        // Cannot remove yourself
        if (member.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.E_4031, "Cannot remove yourself from the store");
        }

        // Cannot remove the StoreOwner
        if (member.getStoreRole() == TenantMember.StoreRole.STORE_OWNER) {
            throw new BusinessException(ErrorCode.E_4031, "Cannot remove the store owner");
        }

        member.setStatus(TenantMember.MemberStatus.REMOVED);
        tenantMemberRepository.save(member);
        log.info("Member removed from tenant: tenantId={}, userId={}, removedBy={}", tenantId, userId, currentUserId);
    }

    /**
     * Initialize feature toggles for a tenant based on business type
     * Called when tenant status changes to ACTIVE
     */
    @Transactional
    public void initializeFeatureToggles(final UUID tenantId, final String businessType) {
        log.info("Initializing feature toggles for tenant: {}, businessType: {}", tenantId, businessType);

        List<TenantFeatureToggle> toggles = new ArrayList<>();

        // Set boolean toggles based on business type
        boolean retailEnabled = "RETAIL_ONLY".equals(businessType) || "HYBRID".equals(businessType);
        boolean bookingEnabled = "BOOKING_ONLY".equals(businessType) || "HYBRID".equals(businessType);

        toggles.add(createToggle(tenantId, "RETAIL_ENABLED", retailEnabled));
        toggles.add(createToggle(tenantId, "BOOKING_ENABLED", bookingEnabled));
        toggles.add(createToggle(tenantId, "CMS_ENABLED", true));
        toggles.add(createToggle(tenantId, "ERP_ENABLED", true));
        toggles.add(createToggle(tenantId, "DYNAMIC_PRICING_ENABLED", false));
        toggles.add(createToggle(tenantId, "PROMO_ENABLED", false));

        // Numeric toggles
        int maxProducts = "RETAIL_ONLY".equals(businessType) ? DEFAULT_MAX_PRODUCTS
                : ("BOOKING_ONLY".equals(businessType) ? 0 : DEFAULT_MAX_PRODUCTS);
        int maxRooms = "RETAIL_ONLY".equals(businessType) ? 0
                : ("BOOKING_ONLY".equals(businessType) ? DEFAULT_MAX_ROOMS : DEFAULT_MAX_ROOMS);
        toggles.add(createNumericToggle(tenantId, "MAX_PRODUCTS", maxProducts));
        toggles.add(createNumericToggle(tenantId, "MAX_ROOMS", maxRooms));
        toggles.add(createNumericToggle(tenantId, "MAX_POSTS", DEFAULT_MAX_POSTS));
        toggles.add(createNumericToggle(tenantId, "COMMISSION_RATE", DEFAULT_COMMISSION_RATE));

        tenantFeatureToggleRepository.saveAll(toggles);
        log.info("Feature toggles initialized for tenant: {}, count: {}", tenantId, toggles.size());
    }

    private TenantFeatureToggle createToggle(final UUID tenantId, final String featureKey, final boolean enabled) {
        return TenantFeatureToggle.builder()
                .tenantId(tenantId)
                .featureKey(featureKey)
                .isEnabled(enabled)
                .enabledAt(enabled ? Instant.now() : null)
                .createdAt(Instant.now())
                .build();
    }

    private TenantFeatureToggle createNumericToggle(final UUID tenantId, final String featureKey, final double value) {
        Map<String, Object> configMap = Map.of("value", value);
        return TenantFeatureToggle.builder()
                .tenantId(tenantId)
                .featureKey(featureKey)
                .isEnabled(true)  // Numeric toggles are always enabled
                .config(configMap)
                .createdAt(Instant.now())
                .build();
    }
}