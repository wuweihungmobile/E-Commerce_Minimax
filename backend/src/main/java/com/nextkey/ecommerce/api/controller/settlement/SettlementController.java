package com.nextkey.ecommerce.api.controller.settlement;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.core.settlement.SettlementService;
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

    private final SettlementService settlementService;

    /**
     * 取得結算單列表 (商家)
     */
    @GetMapping("/settlements")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<SettlementStatementListResponse>> getSettlements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SettlementStatementListResponse response = settlementService.getStatementsByTenant(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得結算單詳情 (商家)
     */
    @GetMapping("/settlements/{statementId}")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<SettlementStatementResponse>> getStatementById(
            @PathVariable UUID statementId) {
        SettlementStatementResponse response = settlementService.getStatementById(statementId);
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
        SettlementStatementResponse response = settlementService.submitForReview(statementId);
        return ResponseEntity.ok(ApiResponse.success("Settlement statement submitted for review", response));
    }

    /**
     * Admin: 取得待審核結算單列表
     */
    @GetMapping("/admin/settlements/pending")
    @PreAuthorize("hasAuthority('admin:read')")
    public ResponseEntity<ApiResponse<SettlementStatementListResponse>> getPendingReviewStatements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SettlementStatementListResponse response = settlementService.getPendingReviewStatements(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Admin: 批准結算單
     */
    @PutMapping("/admin/settlements/{statementId}/approve")
    @PreAuthorize("hasAuthority('admin:write')")
    public ResponseEntity<ApiResponse<SettlementStatementResponse>> approveStatement(
            @PathVariable UUID statementId) {
        log.info("Approve settlement statement: statementId={}", statementId);
        UUID adminId = TenantContext.getCurrentUser();
        SettlementStatementResponse response = settlementService.approveStatement(statementId, adminId);
        return ResponseEntity.ok(ApiResponse.success("Settlement statement approved", response));
    }

    /**
     * Admin: 駁回結算單
     */
    @PutMapping("/admin/settlements/{statementId}/reject")
    @PreAuthorize("hasAuthority('admin:write')")
    public ResponseEntity<ApiResponse<SettlementStatementResponse>> rejectStatement(
            @PathVariable UUID statementId,
            @RequestParam(required = false) String reason) {
        log.info("Reject settlement statement: statementId={}, reason={}", statementId, reason);
        UUID adminId = TenantContext.getCurrentUser();
        SettlementStatementResponse response = settlementService.rejectStatement(statementId, adminId, reason);
        return ResponseEntity.ok(ApiResponse.success("Settlement statement rejected", response));
    }
}