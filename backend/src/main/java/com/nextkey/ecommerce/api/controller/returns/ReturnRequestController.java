package com.nextkey.ecommerce.api.controller.returns;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.returns.ReturnDto;
import com.nextkey.ecommerce.core.returns.ReturnRequestService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 退貨申請 REST API（Sprint 118，DEF-044）。
 *
 * <p>買家層 {@code /v2/returns}、店家層 {@code /v2/dashboard/returns}，比照 M18 客服工單的分層路由。
 *
 * <p>🔴 權限一律對應到 {@code RolePermissionMapping} 的實際角色（Sprint 86／DEF-042 的教訓：
 * 新增了 {@code @PreAuthorize} 卻沒把權限掛到任何角色，端點會 403 不可達）。
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class ReturnRequestController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ReturnRequestService returnRequestService;

    // ========== 買家層 ==========

    @PostMapping("/returns")
    @PreAuthorize("hasAuthority('return:create')")
    public ResponseEntity<ApiResponse<ReturnDto.Response>> createReturn(
            @Valid @RequestBody final ReturnDto.CreateRequest request) {
        ReturnDto.Response response = returnRequestService.createReturnRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Return request created", response));
    }

    @GetMapping("/returns")
    @PreAuthorize("hasAuthority('return:read')")
    public ResponseEntity<ApiResponse<Page<ReturnDto.Response>>> getMyReturns(
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        Page<ReturnDto.Response> result = returnRequestService
                .getMyReturnRequests(PageRequest.of(page, size > 0 ? Math.min(size, 100) : DEFAULT_PAGE_SIZE));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/returns/{returnId}")
    @PreAuthorize("hasAuthority('return:read')")
    public ResponseEntity<ApiResponse<ReturnDto.Response>> getReturn(
            @PathVariable final UUID returnId) {
        return ResponseEntity.ok(ApiResponse.success(returnRequestService.getReturnRequest(returnId)));
    }

    @PostMapping("/returns/{returnId}/cancel")
    @PreAuthorize("hasAuthority('return:create')")
    public ResponseEntity<ApiResponse<ReturnDto.Response>> cancelReturn(
            @PathVariable final UUID returnId) {
        return ResponseEntity.ok(ApiResponse.success("Return request cancelled",
                returnRequestService.cancelReturnRequest(returnId)));
    }

    // ========== 店家層 ==========

    @GetMapping("/dashboard/returns")
    @PreAuthorize("hasAuthority('return:read')")
    public ResponseEntity<ApiResponse<Page<ReturnDto.Response>>> getTenantReturns(
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size) {
        Page<ReturnDto.Response> result = returnRequestService
                .getTenantReturnRequests(PageRequest.of(page, size > 0 ? Math.min(size, 100) : DEFAULT_PAGE_SIZE));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/dashboard/returns/{returnId}/approve")
    @PreAuthorize("hasAuthority('return:review')")
    public ResponseEntity<ApiResponse<ReturnDto.Response>> approveReturn(
            @PathVariable final UUID returnId) {
        return ResponseEntity.ok(ApiResponse.success("Return request approved",
                returnRequestService.approveReturn(returnId)));
    }

    @PostMapping("/dashboard/returns/{returnId}/reject")
    @PreAuthorize("hasAuthority('return:review')")
    public ResponseEntity<ApiResponse<ReturnDto.Response>> rejectReturn(
            @PathVariable final UUID returnId,
            @Valid @RequestBody(required = false) final ReturnDto.RejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Return request rejected",
                returnRequestService.rejectReturn(returnId, request)));
    }

    /** 🔴 收貨確認——退貨流程中唯一會動到庫存的一步。 */
    @PostMapping("/dashboard/returns/{returnId}/receive")
    @PreAuthorize("hasAuthority('return:review')")
    public ResponseEntity<ApiResponse<ReturnDto.Response>> receiveReturn(
            @PathVariable final UUID returnId,
            @Valid @RequestBody final ReturnDto.ReceiveRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Return received",
                returnRequestService.receiveReturn(returnId, request)));
    }
}
