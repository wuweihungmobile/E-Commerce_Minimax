package com.nextkey.ecommerce.api.controller.settlement;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.filter.UserPrincipal;
import com.nextkey.ecommerce.core.settlement.TransferService;
import com.nextkey.ecommerce.core.settlement.TransferService.TransferListResponse;
import com.nextkey.ecommerce.core.settlement.TransferService.TransferResponse;
import com.nextkey.ecommerce.domain.model.settlement.Transfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Transfer（結算單審核通過後分潤）對帳查詢與管理端重試 API（Sprint 80，AI-2416 Phase D-2）
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class TransferController {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final TransferService transferService;

    /**
     * 取得 transfer 記錄（對帳）：一般角色/ADMIN 僅能查自己租戶；SUPER_ADMIN 可加 tenantId 查任意租戶。
     */
    @GetMapping("/transfers")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<TransferListResponse>> getTransfers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());

        Page<Transfer> transfers;
        if (isSuperAdmin && tenantId != null) {
            transfers = transferService.getTransfersForTenant(tenantId, pageable);
        } else {
            transfers = transferService.getTransfersForCurrentTenant(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success(TransferService.toListResponse(transfers)));
    }

    /**
     * Admin: 重試失敗/前置條件未滿足的 transfer（限自己租戶；SUPER_ADMIN 可跨租戶）。
     */
    @PostMapping("/admin/transfers/{statementId}/retry")
    @PreAuthorize("hasAuthority('admin:write')")
    public ResponseEntity<ApiResponse<TransferResponse>> retryTransfer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID statementId) {
        log.info("Retry transfer: statementId={}", statementId);
        boolean isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole());
        Transfer transfer = transferService.retryFailedTransfer(statementId, isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success("Transfer retry attempted", TransferService.toResponse(transfer)));
    }
}
