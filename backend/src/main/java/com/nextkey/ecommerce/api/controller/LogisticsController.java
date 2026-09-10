package com.nextkey.ecommerce.api.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.LogisticsDto;
import com.nextkey.ecommerce.core.logistics.LogisticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



/**
 * 物流 REST API (Mock Implementation)
 */
@Slf4j
@RestController
@RequestMapping("/v2/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final LogisticsService logisticsService;

    /**
     * 建立物流單
     *
     * <p>DEF-191（Sprint 152）：權限由 {@code order:create} 改為 {@code order:update}。
     * 建立物流單語意上是「管理既有訂單的履約進度」而非「建立新訂單」，
     * {@code SELLER} 角色原本就有 {@code order:update}（可把訂單確認到 CONFIRMED）
     * 卻沒有 {@code order:create}，導致能推進到 CONFIRMED 卻無法建立物流單完成出貨——
     * 相鄰兩步驟所需最低角色不一致。改用 {@code order:update} 後不需擴大
     * {@code RolePermissionMapping} 的 SELLER 權限清單即可補上此缺口
     * （{@code STORE_OWNER}/{@code ADMIN} 本就同時具備兩個權限，行為不受影響）。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('order:update')")
    public ResponseEntity<ApiResponse<LogisticsDto.LogisticsResponse>> createLogistics(
            @Valid @RequestBody LogisticsDto.CreateRequest request) {
        log.info("Create logistics request: orderId={}, provider={}",
                request.getOrderId(), request.getLogisticsProvider());
        LogisticsDto.LogisticsResponse response = logisticsService.createLogistics(request);
        return ResponseEntity.ok(ApiResponse.success("Logistics created successfully", response));
    }

    /**
     * 取得物流資訊
     */
    @GetMapping("/{logisticsId}")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<LogisticsDto.LogisticsResponse>> getLogistics(
            @PathVariable UUID logisticsId) {
        LogisticsDto.LogisticsResponse response = logisticsService.getLogistics(logisticsId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 依訂單取得物流列表
     */
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<List<LogisticsDto.LogisticsResponse>>> getLogisticsByOrderId(
            @PathVariable UUID orderId) {
        List<LogisticsDto.LogisticsResponse> response = logisticsService.getLogisticsByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 追蹤物流
     */
    @GetMapping("/{logisticsId}/track")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<LogisticsDto.StatusResponse>> trackLogistics(
            @PathVariable UUID logisticsId) {
        LogisticsDto.StatusResponse response = logisticsService.trackLogistics(logisticsId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 取得詳細追蹤歷史
     */
    @GetMapping("/{logisticsId}/tracking-detail")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<LogisticsDto.TrackingDetail>> getTrackingDetail(
            @PathVariable UUID logisticsId) {
        LogisticsDto.TrackingDetail response = logisticsService.getTrackingDetail(logisticsId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 更新物流狀態 (Mock - for testing)
     */
    @PutMapping("/{logisticsId}/status")
    @PreAuthorize("hasAuthority('order:update')")
    public ResponseEntity<ApiResponse<LogisticsDto.LogisticsResponse>> updateLogisticsStatus(
            @PathVariable UUID logisticsId,
            @RequestParam String status) {
        var logisticsStatus = com.nextkey.ecommerce.domain.model.logistics.Logistics.LogisticsStatus.valueOf(status);
        LogisticsDto.LogisticsResponse response = logisticsService.updateLogisticsStatus(logisticsId, logisticsStatus);
        return ResponseEntity.ok(ApiResponse.success("Logistics status updated", response));
    }

    /**
     * 取消物流
     */
    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('order:update')")
    public ResponseEntity<ApiResponse<LogisticsDto.LogisticsResponse>> cancelLogistics(
            @Valid @RequestBody LogisticsDto.CancelRequest request) {
        log.info("Cancel logistics request: logisticsId={}, reason={}",
                request.getLogisticsId(), request.getReason());
        LogisticsDto.LogisticsResponse response = logisticsService.cancelLogistics(
                request.getLogisticsId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Logistics cancelled", response));
    }
}
