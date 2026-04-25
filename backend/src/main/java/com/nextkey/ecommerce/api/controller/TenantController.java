package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.*;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.tenant.TenantService;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
     * US-M17-002: GET /api/v2/tenants - Get my stores list
     * Role: StoreOwner (authenticated)
     */
    @GetMapping("/tenants")
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
}