package com.nextkey.ecommerce.core.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.AdminDto;
import com.nextkey.ecommerce.domain.model.audit.AuditLog;
import com.nextkey.ecommerce.domain.model.inventory.PurchaseOrder;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.room.RoomCalendar;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.tenant.TenantApplication;
import com.nextkey.ecommerce.domain.model.tenant.TenantFeatureToggle;
import com.nextkey.ecommerce.domain.model.tenant.TenantMember;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PurchaseOrderRepository;
import com.nextkey.ecommerce.domain.repository.RoomCalendarRepository;
import com.nextkey.ecommerce.domain.repository.TenantApplicationRepository;
import com.nextkey.ecommerce.domain.repository.TenantFeatureToggleRepository;
import com.nextkey.ecommerce.domain.repository.TenantMemberRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.domain.repository.audit.AuditLogRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final AuditLogRepository auditLogRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final BookingRepository bookingRepository;
    private final RoomCalendarRepository roomCalendarRepository;
    private final TenantApplicationRepository tenantApplicationRepository;
    private final TenantMemberRepository tenantMemberRepository;

    /**
     * 記錄一筆管理操作稽核（DEF-016）。持久化到 audit_log，與既有 log.info 並存。
     * 稽核失敗不應中斷主要業務流程。
     */
    private void recordAudit(String action, String entityType, UUID entityId, UUID tenantId,
                             String oldValue, String newValue, String reason) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .tenantId(tenantId)
                    .userId(TenantContext.getCurrentUser())
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .reason(reason)
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to persist audit log: action={}, entityId={}, error={}", action, entityId, e.getMessage());
        }
    }

    // Mock values
    private static final BigDecimal MOCK_AVERAGE_ORDER_VALUE = BigDecimal.valueOf(1500);

    private static final long STATS_ORDER_WINDOW_DAYS = 30L;

    // ========== Tenant Management ==========

    /**
     * 取得租戶列表 (平台管理員)
     */
    @Transactional(readOnly = true)
    public AdminDto.TenantListResponse getTenants(int page, int size) {
        return getTenants(page, size, null, null);
    }

    /**
     * 取得租戶列表 (平台管理員)，支援 status/關鍵字（name/slug）篩選 + 真分頁（Sprint 64 修正）。
     * 修正前 page/size 參數未被使用，實際上是 findAll() 取回全部資料後才在記憶體組裝分頁回應。
     */
    @Transactional(readOnly = true)
    public AdminDto.TenantListResponse getTenants(int page, int size, Tenant.TenantStatus status, String keyword) {
        Specification<Tenant> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("slug")), pattern)));
        }

        Page<Tenant> result = tenantRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<AdminDto.TenantResponse> tenantResponses = result.getContent().stream()
                .map(this::toTenantResponse)
                .collect(Collectors.toList());

        return AdminDto.TenantListResponse.builder()
                .tenants(tenantResponses)
                .totalCount((int) result.getTotalElements())
                .page(page)
                .size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
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

        String oldStatus = tenant.getStatus().name();
        String auditAction;
        if ("APPROVE".equals(request.getDecision())) {
            tenant.setStatus(Tenant.TenantStatus.ACTIVE);
            auditAction = "TENANT_APPROVED";
        } else if ("REJECT".equals(request.getDecision())) {
            tenant.setStatus(Tenant.TenantStatus.REJECTED);
            auditAction = "TENANT_REJECTED";
        } else {
            throw new BusinessException(ErrorCode.E_9000, "Invalid decision");
        }

        tenantRepository.save(tenant);
        recordAudit(auditAction, "TENANT", tenant.getId(), tenant.getId(), oldStatus, tenant.getStatus().name(), null);

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
        recordAudit("TENANT_APPROVED", "TENANT", tenantId, tenantId, "PENDING_REVIEW", "ACTIVE", null);

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
        recordAudit("TENANT_REJECTED", "TENANT", tenantId, tenantId, "PENDING_REVIEW", "REJECTED", request.getReason());

        return AdminDto.TenantRejectResponse.builder()
                .tenantId(tenantId)
                .status("REJECTED")
                .rejectedAt(Instant.now())
                .rejectedBy("SYSTEM_ADMIN")
                .reason(request.getReason())
                .build();
    }

    // ========== Purchase Order Approval (Sprint 85, PRD §6.7.2) ==========

    /**
     * 取得待審批採購單列表（跨租戶）。僅供 SUPER_ADMIN 使用，此方法本身不做租戶篩選——
     * 呼叫路徑已由 Controller 層 {@code @PreAuthorize("hasRole('SUPER_ADMIN')")} 鎖死，
     * 跨租戶查看正是本功能存在的目的，非權限檢查遺漏。
     */
    @Transactional(readOnly = true)
    public AdminDto.PurchaseOrderPendingListResponse getPendingApprovalPurchaseOrders(int page, int size) {
        Page<PurchaseOrder> result = purchaseOrderRepository.findByStatus(
                PurchaseOrder.POStatus.PENDING_APPROVAL,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "submittedAt")));

        List<AdminDto.PurchaseOrderSummaryResponse> summaries = result.getContent().stream()
                .map(this::toPurchaseOrderSummary)
                .collect(Collectors.toList());

        return AdminDto.PurchaseOrderPendingListResponse.builder()
                .purchaseOrders(summaries)
                .page(page)
                .size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /**
     * 核准採購單（PENDING_APPROVAL → APPROVED）。僅 SUPER_ADMIN 可呼叫。
     */
    @Transactional
    public AdminDto.PurchaseOrderSummaryResponse approvePurchaseOrder(UUID poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7001));

        if (!po.canReview()) {
            throw new BusinessException(ErrorCode.E_7002,
                    String.format("Cannot approve PO in status: %s", po.getStatus()));
        }

        String oldStatus = po.getStatus().name();
        UUID reviewedBy = TenantContext.getCurrentUser();
        Instant reviewedAt = Instant.now();

        // 🔴 併發防護：approve 與 reject（或兩個併發 approve）都可能同時通過上面的 canReview() 檢查，
        // 改用條件式原子 UPDATE（WHERE status = PENDING_APPROVAL）確保只有一邊真的轉換成功，
        // 另一邊影響 0 列並被拒絕，而非各自用舊快照互相覆寫審批結果（含 reviewedBy/reviewedAt 稽核紀錄）。
        int updated = purchaseOrderRepository.reviewIfStatus(poId, PurchaseOrder.POStatus.PENDING_APPROVAL,
                PurchaseOrder.POStatus.APPROVED, reviewedBy, reviewedAt, null);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.E_7002,
                    String.format("Cannot approve PO in status: %s", po.getStatus()));
        }
        po.setStatus(PurchaseOrder.POStatus.APPROVED);
        po.setReviewedBy(reviewedBy);
        po.setReviewedAt(reviewedAt);

        log.info("Purchase order approved: poId={}, tenantId={}", poId, po.getTenantId());
        recordAudit("PURCHASE_ORDER_APPROVED", "PURCHASE_ORDER", poId, po.getTenantId(), oldStatus, "APPROVED", null);

        return toPurchaseOrderSummary(po);
    }

    /**
     * 駁回採購單（PENDING_APPROVAL → REJECTED）。僅 SUPER_ADMIN 可呼叫。
     */
    @Transactional
    public AdminDto.PurchaseOrderSummaryResponse rejectPurchaseOrder(UUID poId, AdminDto.PurchaseOrderRejectRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_7001));

        if (!po.canReview()) {
            throw new BusinessException(ErrorCode.E_7002,
                    String.format("Cannot reject PO in status: %s", po.getStatus()));
        }

        String oldStatus = po.getStatus().name();
        UUID reviewedBy = TenantContext.getCurrentUser();
        Instant reviewedAt = Instant.now();

        // 🔴 併發防護：見 approvePurchaseOrder 同一段說明。
        int updated = purchaseOrderRepository.reviewIfStatus(poId, PurchaseOrder.POStatus.PENDING_APPROVAL,
                PurchaseOrder.POStatus.REJECTED, reviewedBy, reviewedAt, request.getReason());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.E_7002,
                    String.format("Cannot reject PO in status: %s", po.getStatus()));
        }
        po.setStatus(PurchaseOrder.POStatus.REJECTED);
        po.setReviewedBy(reviewedBy);
        po.setReviewedAt(reviewedAt);
        po.setRejectionReason(request.getReason());

        log.info("Purchase order rejected: poId={}, tenantId={}, reason={}", poId, po.getTenantId(), request.getReason());
        recordAudit("PURCHASE_ORDER_REJECTED", "PURCHASE_ORDER", poId, po.getTenantId(),
                oldStatus, "REJECTED", request.getReason());

        return toPurchaseOrderSummary(po);
    }

    private AdminDto.PurchaseOrderSummaryResponse toPurchaseOrderSummary(PurchaseOrder po) {
        return AdminDto.PurchaseOrderSummaryResponse.builder()
                .id(po.getId())
                .tenantId(po.getTenantId())
                .poNumber(po.getPoNumber())
                .status(po.getStatus().name())
                .totalAmount(po.getTotalAmount())
                .currency(po.getCurrency())
                .submittedAt(po.getSubmittedAt())
                .reviewedBy(po.getReviewedBy())
                .reviewedAt(po.getReviewedAt())
                .rejectionReason(po.getRejectionReason())
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

        // 🔴 併發防護（Sprint 137 DEF-117）：見 TenantRepository.updateStatusIfCurrent 說明。
        int updated = tenantRepository.updateStatusIfCurrent(tenantId, currentStatus, newStatus);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.E_9000,
                    "Tenant status changed concurrently, please retry: " + tenantId);
        }
        tenant.setStatus(newStatus);

        // 如果是 SUSPENDED，自動下架所有 Listings
        if (newStatus == Tenant.TenantStatus.SUSPENDED) {
            deactivateTenantListings(tenantId);
        }

        // 寫入 audit log (mock - log only since no AuditLog entity exists)
        log.info("AUDIT_LOG: Tenant status updated - tenantId={}, previousStatus={}, newStatus={}, reason={}, updatedBy=SUPER_ADMIN",
                tenantId, previousStatus, newStatus.name(), request.getReason());
        recordAudit("TENANT_STATUS_UPDATED", "TENANT", tenantId, tenantId, previousStatus, newStatus.name(), request.getReason());

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
    private void validateStatusTransition(final Tenant.TenantStatus currentStatus, final Tenant.TenantStatus newStatus, final String reason) {
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
    private void deactivateTenantListings(final UUID tenantId) {
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
    private List<String> initializeFeatureToggles(final UUID tenantId) {
        // 先查詢 Tenant 實體（用於 ManyToOne 關聯）
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        // ✅ 強制 flush 確保 tenant INSERT 觸發的 PostgreSQL trigger 已建立 toggles
        // 避免 Hibernate 一級緩存認為 toggle 不存在而重複 INSERT 造成 UNIQUE 約束衝突
        tenantRepository.flush();

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

            // 檢查是否已存在（冪等性：避免測試 trigger 或重複 approve 造成重複建立）
            if (featureToggleRepository.findByTenantIdAndFeatureKey(tenantId, featureKey).isPresent()) {
                log.debug("Feature toggle already exists: tenantId={}, featureKey={}", tenantId, featureKey);
                if (isEnabled) {
                    enabledFeatures.add(featureKey);
                }
                continue;
            }

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
        return getUsers(page, size, tenantId, role, null, null);
    }

    /**
     * 取得用戶列表 (平台管理員)，支援 status/關鍵字（email/fullName）篩選 + 真分頁（Sprint 64 修正）。
     * 修正前 page/size 參數未被使用，實際上是取回全部資料後才在記憶體組裝分頁回應。
     */
    @Transactional(readOnly = true)
    public AdminDto.UserListResponse getUsers(int page, int size, UUID tenantId, String role,
                                               String status, String keyword) {
        Specification<User> spec = Specification.where(null);
        if (tenantId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), tenantId));
        }
        if (role != null && !role.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), User.UserRole.valueOf(role)));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("fullName")), pattern)));
        }

        Page<User> result = userRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<AdminDto.UserManagementResponse> userResponses = result.getContent().stream()
                .map(this::toUserManagementResponse)
                .collect(Collectors.toList());

        return AdminDto.UserListResponse.builder()
                .users(userResponses)
                .totalCount((int) result.getTotalElements())
                .page(page)
                .size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /**
     * 更新用戶狀態 (平台管理員)
     */
    @Transactional
    public AdminDto.UserManagementResponse updateUserStatus(UUID userId, String newStatus) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006));

        String oldStatus = user.getStatus();
        user.setStatus(newStatus);
        user = userRepository.save(user);

        log.info("Admin updated user status: userId={}, newStatus={}", userId, newStatus);
        recordAudit("USER_STATUS_UPDATED", "USER", userId, user.getTenantId(), oldStatus, newStatus, null);
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

        Optional<TenantFeatureToggle> existingToggle = featureToggleRepository
                .findByTenantIdAndFeatureKey(request.getTenantId(), request.getFeatureKey());
        boolean isNewToggle = existingToggle.isEmpty();
        TenantFeatureToggle toggle = existingToggle
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

        // 併發防護（DEF-148）：首次建立 toggle 是 check-then-act TOCTOU，DB 已有
        // UNIQUE(tenant_id, feature_key) 約束兜底，這裡改用 saveAndFlush 捕捉違反約束，
        // 轉譯為乾淨的 E_9000 而非讓呼叫端收到原始的 DataIntegrityViolationException。
        try {
            toggle = featureToggleRepository.saveAndFlush(toggle);
        } catch (DataIntegrityViolationException e) {
            if (!isNewToggle) {
                throw e;
            }
            throw new BusinessException(ErrorCode.E_9000,
                    "Feature toggle was concurrently created, please retry: " + request.getFeatureKey());
        }

        log.info("Feature toggle updated: tenantId={}, feature={}, enabled={}",
                request.getTenantId(), request.getFeatureKey(), request.getIsEnabled());
        recordAudit("FEATURE_TOGGLE_SET", "FEATURE_TOGGLE", request.getTenantId(), request.getTenantId(),
                null, request.getFeatureKey() + "=" + request.getIsEnabled(), null);

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
    public void deleteFeatureToggle(final UUID tenantId, final String featureKey) {
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
        recordAudit("FEATURE_TOGGLE_UPDATED", "FEATURE_TOGGLE", tenantId, tenantId,
                null, featureKey + "=" + enabled, null);

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
        BigDecimal totalGMV = BigDecimal.valueOf(totalOrders).multiply(MOCK_AVERAGE_ORDER_VALUE);

        // 待審核店鋪數＝待審核的「開店申請」（tenant_applications.status=PENDING）。
        // 不可改回以 tenants.status=PENDING_REVIEW 計數：Sprint 97 起開店申請一律先寫入
        // tenant_applications，待 Admin 核准時才建立 status=ACTIVE 的 Tenant
        // （見 approveTenantApplication），生產環境沒有任何路徑會把 Tenant 建成 PENDING_REVIEW，
        // 以該狀態計數會恆為 0——與同一個 Service 的 getPendingTenantApplications()
        // 回傳 N 筆待審核申請自相矛盾（Sprint 108 修復，DEF-059）。
        int pendingReviews = (int) tenantApplicationRepository
                .countByStatus(TenantApplication.ApplicationStatus.PENDING);

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

    // ========== Tenant Stats ==========

    /**
     * US-005 M14: 取得租戶活躍統計
     * 包含：近 30 天訂單數、活躍用戶數、活躍商品數、最後訂單時間
     */
    @Transactional(readOnly = true)
    public AdminDto.TenantStatsResponse getTenantStats(UUID tenantId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        Instant thirtyDaysAgo = Instant.now().minus(STATS_ORDER_WINDOW_DAYS, ChronoUnit.DAYS);
        long orderCount30d = orderRepository.countByTenantIdAndCreatedAtAfter(tenantId, thirtyDaysAgo);
        long activeUserCount = userRepository.countByTenantId(tenantId);
        long activeListingCount = listingRepository.countByTenantIdAndStatus(tenantId, Listing.ListingStatus.ACTIVE);

        Instant lastOrderAt = orderRepository
                .findTopByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 1))
                .stream().findFirst()
                .map(order -> order.getCreatedAt())
                .orElse(null);

        return AdminDto.TenantStatsResponse.builder()
                .tenantId(tenantId)
                .orderCount30d(orderCount30d)
                .activeUserCount(activeUserCount)
                .activeListingCount(activeListingCount)
                .lastOrderAt(lastOrderAt)
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

    // ========== MAINTENANCE Warnings（PRD §5.5.3，v0.9_R02_Loop_02 Q-LO2-106）==========

    private static final int MAINTENANCE_URGENT_WINDOW_DAYS = 1;

    private static final int RANDOM_SUFFIX_LENGTH = 8;

    /**
     * MaintenanceWarnings 列表：M09（通知系統，Phase 2）上線前的替代方案，
     * 供 Admin 人工通知受 MAINTENANCE 影響的房客（TC-LO2-M17-003）。
     * 入住日期在今日或明日（近似 24 小時內，Booking 僅有日期粒度無精確時刻）視為緊急。
     */
    @Transactional(readOnly = true)
    public AdminDto.MaintenanceWarningListResponse getMaintenanceWarnings() {
        List<RoomCalendar> maintenanceCalendars = roomCalendarRepository
                .findByStatusAndBookingIdIsNotNull(RoomCalendar.RoomCalendarStatus.MAINTENANCE);

        List<UUID> bookingIds = maintenanceCalendars.stream()
                .map(RoomCalendar::getBookingId)
                .distinct()
                .collect(Collectors.toList());

        if (bookingIds.isEmpty()) {
            return AdminDto.MaintenanceWarningListResponse.builder().warnings(List.of()).build();
        }

        LocalDate urgentCutoff = LocalDate.now().plusDays(MAINTENANCE_URGENT_WINDOW_DAYS);

        List<AdminDto.MaintenanceWarningResponse> warnings = bookingRepository.findAllById(bookingIds).stream()
                .map(booking -> AdminDto.MaintenanceWarningResponse.builder()
                        .bookingId(booking.getId())
                        .checkInDate(booking.getCheckInDate())
                        .guestEmail(booking.getGuestEmail())
                        .urgent(!booking.getCheckInDate().isAfter(urgentCutoff))
                        .build())
                .sorted(Comparator.comparing(AdminDto.MaintenanceWarningResponse::getCheckInDate))
                .collect(Collectors.toList());

        return AdminDto.MaintenanceWarningListResponse.builder()
                .warnings(warnings)
                .build();
    }

    // ========== Tenant Application Review（PRD §7.4.1，Sprint 97）==========

    /**
     * 待審核開店申請列表。
     */
    @Transactional(readOnly = true)
    public AdminDto.TenantApplicationListResponse getPendingTenantApplications() {
        List<TenantApplication> applications = tenantApplicationRepository
                .findByStatus(TenantApplication.ApplicationStatus.PENDING);

        List<AdminDto.TenantApplicationSummaryResponse> summaries = applications.stream()
                .map(this::toTenantApplicationSummary)
                .collect(Collectors.toList());

        return AdminDto.TenantApplicationListResponse.builder().applications(summaries).build();
    }

    /**
     * 核准開店申請（PRD §7.4.1 Buyer → StoreOwner 角色授予流程）：
     * 1. 建立 Tenant（ACTIVE）並初始化 Feature Toggle。
     * 2. 於 tenant_members 建立該申請人的 StoreOwner 記錄。
     * 3. 更新申請狀態為 APPROVED，並回填 tenantId。
     */
    @Transactional
    public AdminDto.TenantApplicationApproveResponse approveTenantApplication(
            final UUID applicationId, final UUID reviewedBy) {
        TenantApplication application = tenantApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2006));

        if (application.getStatus() != TenantApplication.ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.E_2007);
        }
        if (application.getUserId() == null) {
            throw new BusinessException(ErrorCode.E_2008);
        }

        // 🔴 併發防護（Sprint 137 DEF-114）：先原子搶占審核權（PENDING→APPROVED），成功才建立
        // Tenant/membership；避免兩個併發核准請求都通過上面的快照檢查，各自建立出兩個獨立的
        // ACTIVE Tenant + STORE_OWNER membership（孤兒資源）。
        int claimed = tenantApplicationRepository.updateStatusIfCurrent(applicationId,
                TenantApplication.ApplicationStatus.PENDING, TenantApplication.ApplicationStatus.APPROVED);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.E_2007);
        }
        application.setStatus(TenantApplication.ApplicationStatus.APPROVED);

        Tenant tenant = Tenant.builder()
                .name(application.getStoreName())
                .slug(generateTenantSlug(application.getStoreName()))
                .description(application.getStoreDescription())
                .status(Tenant.TenantStatus.ACTIVE)
                .contactEmail(application.getContactEmail())
                .contactPhone(application.getContactPhone())
                .build();
        tenant = tenantRepository.save(tenant);

        List<String> enabledFeatures = initializeFeatureToggles(tenant.getId());

        tenantMemberRepository.save(TenantMember.builder()
                .tenantId(tenant.getId())
                .userId(application.getUserId())
                .storeRole(TenantMember.StoreRole.STORE_OWNER)
                .joinedAt(Instant.now())
                .build());

        // PRD §7.4.1：JWT roles 需包含 StoreOwner，供下次登入/換發 token 時正確授權
        // （Sprint 97 遺漏：僅建立 tenant_members 紀錄，User.role 從未同步更新，
        // 導致核准後使用者重新登入仍拿不到 StoreOwner 權限）。
        userRepository.findById(application.getUserId()).ifPresent(user -> {
            user.setRole(User.UserRole.STORE_OWNER);
            userRepository.save(user);
        });

        application.setTenantId(tenant.getId());
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(reviewedBy);
        tenantApplicationRepository.save(application);

        log.info("Tenant application approved: applicationId={}, tenantId={}, userId={}, enabledFeatures={}",
                applicationId, tenant.getId(), application.getUserId(), enabledFeatures);
        recordAudit("TENANT_APPLICATION_APPROVED", "TENANT_APPLICATION", applicationId, tenant.getId(),
                "PENDING", "APPROVED", null);

        return AdminDto.TenantApplicationApproveResponse.builder()
                .applicationId(applicationId)
                .tenantId(tenant.getId())
                .status(TenantApplication.ApplicationStatus.APPROVED.name())
                .approvedAt(application.getReviewedAt())
                .build();
    }

    /**
     * 駁回開店申請。不建立 Tenant，僅更新申請狀態與駁回原因。
     */
    @Transactional
    public AdminDto.TenantApplicationRejectResponse rejectTenantApplication(
            final UUID applicationId, final UUID reviewedBy, final AdminDto.TenantApplicationRejectRequest request) {
        TenantApplication application = tenantApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2006));

        if (application.getStatus() != TenantApplication.ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.E_2007);
        }

        // 🔴 併發防護（Sprint 137 DEF-115）：與 approveTenantApplication 同一原則，見該處說明。
        int claimed = tenantApplicationRepository.updateStatusIfCurrent(applicationId,
                TenantApplication.ApplicationStatus.PENDING, TenantApplication.ApplicationStatus.REJECTED);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.E_2007);
        }

        application.setStatus(TenantApplication.ApplicationStatus.REJECTED);
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(reviewedBy);
        application.setRejectionReason(request.getReason());
        tenantApplicationRepository.save(application);

        log.info("Tenant application rejected: applicationId={}, reason={}", applicationId, request.getReason());
        recordAudit("TENANT_APPLICATION_REJECTED", "TENANT_APPLICATION", applicationId, null,
                "PENDING", "REJECTED", request.getReason());

        return AdminDto.TenantApplicationRejectResponse.builder()
                .applicationId(applicationId)
                .status(TenantApplication.ApplicationStatus.REJECTED.name())
                .rejectedAt(application.getReviewedAt())
                .reason(request.getReason())
                .build();
    }

    private AdminDto.TenantApplicationSummaryResponse toTenantApplicationSummary(final TenantApplication application) {
        return AdminDto.TenantApplicationSummaryResponse.builder()
                .applicationId(application.getId())
                .userId(application.getUserId())
                .storeName(application.getStoreName())
                .storeDescription(application.getStoreDescription())
                .businessType(application.getBusinessType())
                .contactEmail(application.getContactEmail())
                .contactPhone(application.getContactPhone())
                .status(application.getStatus().name())
                .submittedAt(application.getSubmittedAt())
                .build();
    }

    /** 依店名產生唯一 slug（比照 PostService/PostCategoryService 既有慣例：碰撞時附加隨機字尾）。 */
    private String generateTenantSlug(final String storeName) {
        String base = storeName.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.isEmpty()) {
            base = "store";
        }
        String slug = base;
        while (tenantRepository.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, RANDOM_SUFFIX_LENGTH);
        }
        return slug;
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

    // ========== Audit Log（Sprint 61 US-001，DEF-016 後續） ==========

    /**
     * 平台管理者查詢稽核紀錄，支援分頁與時間範圍/操作類型篩選。
     * 以 Specification 動態組合可選條件，只有實際提供的篩選條件才會出現在查詢中，
     * 避免對未提供的參數送出「IS NULL」比較（PostgreSQL 對純 null 參數型別推斷有已知限制）。
     */
    @Transactional(readOnly = true)
    public AdminDto.AuditLogListResponse getAuditLogs(int page, int size, String action,
                                                       Instant startDate, Instant endDate) {
        Specification<AuditLog> spec = Specification.where(null);
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (startDate != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
        }
        if (endDate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
        }

        Page<AuditLog> result = auditLogRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<AdminDto.AuditLogResponse> logs = result.getContent().stream()
                .map(this::toAuditLogResponse)
                .collect(Collectors.toList());

        return AdminDto.AuditLogListResponse.builder()
                .logs(logs)
                .page(page)
                .size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    private AdminDto.AuditLogResponse toAuditLogResponse(AuditLog auditLog) {
        return AdminDto.AuditLogResponse.builder()
                .id(auditLog.getId())
                .tenantId(auditLog.getTenantId())
                .userId(auditLog.getUserId())
                .action(auditLog.getAction())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .oldValue(auditLog.getOldValue())
                .newValue(auditLog.getNewValue())
                .reason(auditLog.getReason())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
