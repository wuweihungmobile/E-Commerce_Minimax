package com.nextkey.ecommerce.api.controller.settlement;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.settlement.SettlementGenerator;
import com.nextkey.ecommerce.core.settlement.SettlementReviewer;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementListResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 結算單 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class SettlementController {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final SettlementGenerator settlementGenerator;
    private final SettlementReviewer settlementReviewer;

    /**
     * 取得結算單列表 (商家)
     */
    @GetMapping("/settlements")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<SettlementStatementListResponse>> getSettlements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SettlementStatementListResponse response = settlementGenerator.getStatementsByTenant(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得結算單詳情 (商家)
     */
    @GetMapping("/settlements/{statementId}")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<SettlementStatementResponse>> getStatementById(
            @PathVariable UUID statementId) {
        SettlementStatementResponse response = settlementGenerator.getStatementById(statementId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 提交結算單審核 (商家)
     */
    @PutMapping("/settlements/{statementId}/submit")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<SettlementStatementResponse>> submitForReview(
            @PathVariable UUID statementId) {
        log.info("Submit settlement statement for review: statementId={}", statementId);
        SettlementStatementResponse response = settlementReviewer.submitForReview(statementId);
        return ResponseEntity.ok(ApiResponse.success("Settlement statement submitted for review", response));
    }

    /**
     * Admin: 取得待審核結算單列表。非 SUPER_ADMIN 僅能查自己租戶；
     * SUPER_ADMIN 可加 tenantId 查指定租戶，未指定則跨租戶總覽（Sprint 81，DEF-040 修復）。
     */
    @GetMapping("/admin/settlements/pending")
    @PreAuthorize("hasAuthority('admin:read')")
    public ResponseEntity<ApiResponse<SettlementStatementListResponse>> getPendingReviewStatements(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        SettlementStatementListResponse response = settlementReviewer.getPendingReviewStatements(
                page, size, isSuperAdmin, tenantId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Admin: 批准結算單。非 SUPER_ADMIN 僅能批准自己租戶（Sprint 81，DEF-040 修復）。
     */
    @PutMapping("/admin/settlements/{statementId}/approve")
    @PreAuthorize("hasAuthority('admin:write')")
    public ResponseEntity<ApiResponse<SettlementStatementResponse>> approveStatement(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID statementId) {
        log.info("Approve settlement statement: statementId={}", statementId);
        UUID adminId = TenantContext.getCurrentUser();
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        SettlementStatementResponse response = settlementReviewer.approveStatement(statementId, adminId, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success("Settlement statement approved", response));
    }

    /**
     * Admin: 駁回結算單。非 SUPER_ADMIN 僅能駁回自己租戶（Sprint 81，DEF-040 修復）。
     */
    @PutMapping("/admin/settlements/{statementId}/reject")
    @PreAuthorize("hasAuthority('admin:write')")
    public ResponseEntity<ApiResponse<SettlementStatementResponse>> rejectStatement(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID statementId,
            @RequestParam(required = false) String reason) {
        log.info("Reject settlement statement: statementId={}, reason={}", statementId, reason);
        UUID adminId = TenantContext.getCurrentUser();
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        SettlementStatementResponse response = settlementReviewer.rejectStatement(statementId, adminId, reason, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success("Settlement statement rejected", response));
    }
}