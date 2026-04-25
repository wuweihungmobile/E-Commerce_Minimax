package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.core.order.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 訂單 REST API
 */
@Slf4j
@RestController
@RequestMapping("/v2/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 建立訂單（從購物車）
     */
    @PostMapping
    @PreAuthorize("hasAuthority('order:create')")
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> createOrder(
            @Valid @RequestBody OrderDto.CreateRequest request) {
        OrderDto.OrderResponse order = orderService.createOrderFromCart(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", order));
    }

    /**
     * 取得用戶訂單列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<Page<OrderDto.OrderListResponse>>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        Page<OrderDto.OrderListResponse> orders = orderService.getUserOrders(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    /**
     * 取得訂單詳情
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('order:read')")
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> getOrder(
            @PathVariable UUID orderId) {
        OrderDto.OrderResponse order = orderService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    /**
     * 更新訂單狀態
     */
    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAuthority('order:update')")
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> updateStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderDto.UpdateStatusRequest request) {
        OrderDto.OrderResponse order = orderService.updateOrderStatus(
                orderId, request.getTargetStatus(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Order status updated", order));
    }

    /**
     * 取消訂單
     * 允許訂單擁有者取消自己的訂單，或 ADMIN/SUPER_ADMIN 取消任何訂單
     * 注意：實際授權邏輯在 service 層處理
     */
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderDto.OrderResponse>> cancelOrder(
            @PathVariable UUID orderId,
            @RequestParam(required = false) String reason) {
        OrderDto.OrderResponse order = orderService.cancelOrder(orderId, reason);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", order));
    }

    /**
     * 取得訂單狀態日誌
     * 允許訂單擁有者查看自己的訂單日誌，或 ADMIN/SUPER_ADMIN 查看任何訂單日誌
     * 注意：實際授權邏輯在 service 層處理
     */
    @GetMapping("/{orderId}/logs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OrderDto.StateLogResponse>>> getOrderLogs(
            @PathVariable UUID orderId) {
        List<OrderDto.StateLogResponse> logs = orderService.getOrderStateLogs(orderId);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}