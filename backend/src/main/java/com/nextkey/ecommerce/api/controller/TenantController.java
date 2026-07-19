package com.nextkey.ecommerce.api.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.AddMemberRequest;
import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.FeatureToggleRequest;
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
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.tenant.TenantService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    /**
     * US-M17-001: POST /api/v2/tenants/apply - Apply for store opening
     * Role: Guest (no authentication required)
     */
    @PostMapping("/tenants/apply")
    public ResponseEntity<ApiResponse<TenantApplicationResponse>> createApplication(
            @Valid @RequestBody TenantApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Store application request: {}", request.getStoreName());
        // principal can be null for Guest users
        UUID userId = (principal != null) ? principal.getUserId() : null;
        TenantApplicationResponse response = tenantService.createApplication(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Store application submitted successfully", response));
    }

    /**
     * US-M17-005: GET /api/v2/tenants/my - Get my stores list
     * Role: StoreOwner (authenticated)
     */
    @GetMapping("/tenants/my")
    @PreAuthorize("hasAuthority('SCOPE_store:read') or hasAuthority('ROLE_STORE_OWNER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyTenants(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] getMyTenants called, principal: {}",
                principal != null ? principal.getUserId() : "null");

        if (principal == null) {
            log.error("[TenantController] getMyTenants: principal is null!");
            throw new BusinessException(ErrorCode.E_1000, "Authentication required");
        }

        log.info("[TenantController] getMyTenants: user={}", principal.getUserId());
        List<TenantListResponse> tenants = tenantService.getTenantsListByUser();
        return ResponseEntity.ok(ApiResponse.success(Map.of("tenants", tenants)));
    }

    /**
     * US-M17-003: GET /api/v2/tenants/:id - Get store details
     * Role: Guest+ (public access)
     */
    @GetMapping("/tenants/{id}")
    @PreAuthorize("isAuthenticated() or true")
    public ResponseEntity<ApiResponse<TenantDetailsResponse>> getTenantDetails(
            @PathVariable UUID id) {
        log.info("Get tenant details request: {}", id);
        TenantDetailsResponse response = tenantService.getTenantDetails(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * US-M17-004: PUT /api/v2/tenants/:id - Update store information
     * Role: StoreOwner
     */
    @PutMapping("/tenants/{id}")
    @PreAuthorize("hasAuthority('SCOPE_store:write') or hasAuthority('ROLE_STORE_OWNER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TenantUpdateResponse>> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody TenantUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] updateTenant called: id={}, principal={}",
                id, principal != null ? principal.getUserId() : "null");

        if (principal == null) {
            log.error("[TenantController] updateTenant: principal is null!");
            throw new BusinessException(ErrorCode.E_1000, "Authentication required");
        }

        log.info("[TenantController] updateTenant: id={}, user={}", id, principal.getUserId());
        TenantUpdateResponse response = tenantService.updateTenant(id, request, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Store information updated successfully", response));
    }

    /**
     * US-M17-005: GET /api/v2/dashboard/tenants/features - Get feature toggles
     * Role: StoreOwner+
     */
    @GetMapping("/dashboard/tenants/features")
    @PreAuthorize("hasAuthority('SCOPE_store:read') or hasAuthority('ROLE_STORE_OWNER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<FeatureToggleResponse>> getFeatureToggles(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] getFeatureToggles called, principal: {}",
                principal != null ? principal.getUserId() : "null");

        if (principal == null) {
            log.error("[TenantController] getFeatureToggles: principal is null!");
            throw new BusinessException(ErrorCode.E_1000, "Authentication required");
        }

        // Get tenant from context (user may belong to multiple tenants)
        UUID tenantId = getTenantIdFromContext(principal);
        if (tenantId == null) {
            log.warn("[TenantController] getFeatureToggles: tenantId is null for user: {}", principal.getUserId());
            throw new BusinessException(ErrorCode.E_2003, "Tenant context ambiguous");
        }

        log.info("[TenantController] getFeatureToggles: user={}, tenantId={}", principal.getUserId(), tenantId);
        FeatureToggleResponse response = tenantService.getFeatureToggles(tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * US-M17-006: PUT /api/v2/dashboard/tenants/features/:feature - Update feature toggle
     * Role: StoreOwner
     */
    @PutMapping("/dashboard/tenants/features/{feature}")
    @PreAuthorize("hasAuthority('SCOPE_store:write') or hasAuthority('ROLE_STORE_OWNER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<FeatureToggleUpdateResponse>> updateFeatureToggle(
            @PathVariable String feature,
            @Valid @RequestBody FeatureToggleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] updateFeatureToggle called: feature={}, principal={}",
                feature, principal != null ? principal.getUserId() : "null");

        if (principal == null) {
            log.error("[TenantController] updateFeatureToggle: principal is null!");
            throw new BusinessException(ErrorCode.E_1000, "Authentication required");
        }

        UUID tenantId = getTenantIdFromContext(principal);
        if (tenantId == null) {
            log.warn("[TenantController] updateFeatureToggle: tenantId is null for user: {}", principal.getUserId());
            throw new BusinessException(ErrorCode.E_2003, "Tenant context ambiguous");
        }

        log.info("[TenantController] updateFeatureToggle: user={}, tenantId={}, feature={}", principal.getUserId(), tenantId, feature);
        FeatureToggleUpdateResponse response = tenantService.updateFeatureToggle(tenantId, feature, request.getEnabled());
        return ResponseEntity.ok(ApiResponse.success("Feature request submitted successfully", response));
    }

    /**
     * Helper method to get tenant ID from user context
     * Uses X-Tenant-ID header or user's primary tenant
     */
    private UUID getTenantIdFromContext(UserPrincipal principal) {
        if (principal == null) {
            log.warn("[TenantController] getTenantIdFromContext called with null principal");
            return null;
        }

        // First check if user has a direct tenant_id in their user record
        // This is a simplified approach; in production, you might have a separate tenant selection mechanism
        if (principal.getTenantId() != null) {
            try {
                return UUID.fromString(principal.getTenantId());
            } catch (IllegalArgumentException e) {
                log.warn("[TenantController] Invalid tenant ID format: {}", principal.getTenantId());
            }
        }
        log.debug("[TenantController] No tenant ID found for user: {}", principal.getUserId());
        return null;
    }

    /**
     * US-M17-006: GET /api/v2/tenants/:id/members - Get members list
     * Role: StoreOwner+
     */
    @GetMapping("/tenants/{id}/members")
    @PreAuthorize("hasAuthority('SCOPE_store:read') or hasAuthority('ROLE_STORE_OWNER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTenantMembers(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] getTenantMembers called: tenantId={}, user={}", id, principal.getUserId());

        List<TenantMemberResponse> members = tenantService.getTenantMembers(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("members", members)));
    }

    /**
     * PRD §9.11: POST /api/v2/tenants/:id/members/invite - Invite member（Sprint 98：
     * 需被邀請人呼叫 accept 確認後才真正生效，取代先前「直接新增即生效」的 Phase 1 簡化）
     * Role: StoreOwner
     */
    @PostMapping("/tenants/{id}/members/invite")
    @PreAuthorize("hasAuthority('SCOPE_store:write') or hasAuthority('ROLE_STORE_OWNER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TenantMemberResponse>> inviteMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] inviteMember called: tenantId={}, user={}", id, principal.getUserId());

        TenantMemberResponse response =
                tenantService.inviteMember(id, request.getUserId(), request.getRole(), principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invitation sent successfully", response));
    }

    /**
     * 我的待確認邀請列表（跨租戶，Sprint 98）。
     */
    @GetMapping("/tenants/invites/my")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyPendingInvites(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] getMyPendingInvites called: user={}", principal.getUserId());

        List<TenantInviteResponse> invites = tenantService.getMyPendingInvites();
        return ResponseEntity.ok(ApiResponse.success(Map.of("invites", invites)));
    }

    /**
     * 接受邀請 → 正式成為店鋪成員（Sprint 98）。
     */
    @PostMapping("/tenants/{id}/members/invite/accept")
    public ResponseEntity<ApiResponse<TenantMemberResponse>> acceptInvite(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] acceptInvite called: tenantId={}, user={}", id, principal.getUserId());

        TenantMemberResponse response = tenantService.acceptInvite(id);
        return ResponseEntity.ok(ApiResponse.success("Invitation accepted", response));
    }

    /**
     * 拒絕邀請（Sprint 98）。
     */
    @PostMapping("/tenants/{id}/members/invite/decline")
    public ResponseEntity<ApiResponse<Void>> declineInvite(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] declineInvite called: tenantId={}, user={}", id, principal.getUserId());

        tenantService.declineInvite(id);
        return ResponseEntity.ok(ApiResponse.success("Invitation declined", null));
    }

    /**
     * US-M17-006: PUT /api/v2/tenants/:id/members/:userId/role - Update member role
     * Role: StoreOwner
     */
    @PutMapping("/tenants/{id}/members/{userId}/role")
    @PreAuthorize("hasAuthority('SCOPE_store:write') or hasAuthority('ROLE_STORE_OWNER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TenantMemberResponse>> updateMemberRole(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @Valid @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] updateMemberRole called: tenantId={}, targetUserId={}, user={}",
                id, userId, principal.getUserId());

        String newRole = request.get("role");
        if (newRole == null || newRole.isBlank()) {
            throw new BusinessException(ErrorCode.E_1001, "Role is required");
        }

        TenantMemberResponse response = tenantService.updateMemberRole(id, userId, newRole);
        return ResponseEntity.ok(ApiResponse.success("Member role updated successfully", response));
    }

    /**
     * US-M17-006: DELETE /api/v2/tenants/:id/members/:userId - Remove member
     * Role: StoreOwner
     */
    @DeleteMapping("/tenants/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('SCOPE_store:write') or hasAuthority('ROLE_STORE_OWNER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("[TenantController] removeMember called: tenantId={}, targetUserId={}, user={}",
                id, userId, principal.getUserId());

        tenantService.removeMember(id, userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Member removed successfully")));
    }
}