package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.core.notification.NotificationHistoryService;
import com.nextkey.ecommerce.core.notification.NotificationService;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



/**
 * 通知 REST API (Mock Implementation)
 */
@Slf4j
@RestController
@RequestMapping("/v2/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationHistoryService notificationHistoryService;

    /**
     * 發送通知 (Admin only)
     *
     * <p>DEF-238（Sprint 180）：先前 notification:create 也授予 STORE_OWNER/ADMIN，但
     * {@link NotificationService#sendNotification} 對目標 userId 完全不做租戶範圍檢查，
     * 任一店主可對系統內任何使用者發送通知。經使用者拍板收斂為僅限 SUPER_ADMIN。
     */
    @PostMapping("/send")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<NotificationDto.NotificationResponse>> sendNotification(
            @Valid @RequestBody NotificationDto.SendRequest request) {
        log.info("Send notification: userId={}, type={}", request.getUserId(), request.getNotificationType());
        NotificationDto.NotificationResponse response = notificationService.sendNotification(request);
        return ResponseEntity.ok(ApiResponse.success("Notification sent", response));
    }

    /**
     * 廣播通知 (Admin only)
     */
    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> broadcastNotification(
            @Valid @RequestBody NotificationDto.BroadcastRequest request) {
        log.info("Broadcast notification: type={}, tenantId={}", request.getNotificationType(), request.getTenantId());
        int count = notificationService.broadcastNotification(request);
        return ResponseEntity.ok(ApiResponse.success("Broadcast completed", count));
    }

    /**
     * 取得用戶通知列表
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationDto.NotificationListResponse>> getUserNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly) {
        UUID userId = TenantContext.getCurrentUser();
        NotificationDto.NotificationListResponse response = notificationService.getUserNotifications(
                userId, page, size, unreadOnly);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 標記已讀
     */
    @PutMapping("/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationDto.NotificationListResponse>> markAsRead(
            @RequestBody(required = false) NotificationDto.MarkReadRequest request) {
        UUID userId = TenantContext.getCurrentUser();
        if (request == null) {
            request = new NotificationDto.MarkReadRequest();
        }
        NotificationDto.NotificationListResponse response = notificationService.markAsRead(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 刪除通知
     */
    @DeleteMapping("/{notificationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable UUID notificationId) {
        UUID userId = TenantContext.getCurrentUser();
        notificationService.deleteNotification(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.success("Notification deleted", null));
    }

    /**
     * 取得未讀計數
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationDto.UnreadCountResponse>> getUnreadCount() {
        UUID userId = TenantContext.getCurrentUser();
        NotificationDto.UnreadCountResponse response = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ========== Sprint 20 US-005: M09 通知歷史查詢 ==========

    /**
     * 取得通知歷史列表（分頁）
     */
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationDto.HistoryListResponse>> getNotificationHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = TenantContext.getCurrentUser();
        NotificationDto.HistoryListResponse response = notificationHistoryService.getHistory(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 標記單筆通知歷史為已讀
     */
    @PutMapping("/history/{historyId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationDto.HistoryResponse>> markHistoryAsRead(
            @PathVariable UUID historyId) {
        UUID userId = TenantContext.getCurrentUser();
        NotificationDto.HistoryResponse response = notificationHistoryService.markOneAsRead(userId, historyId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", response));
    }

    /**
     * 取得通知歷史未讀計數
     */
    @GetMapping("/history/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationDto.HistoryUnreadCountResponse>> getHistoryUnreadCount() {
        UUID userId = TenantContext.getCurrentUser();
        NotificationDto.HistoryUnreadCountResponse response = notificationHistoryService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
