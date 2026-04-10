package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.ApiResponse;
import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.core.notification.NotificationService;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 通知 REST API (Mock Implementation)
 */
@Slf4j
@RestController
@RequestMapping("/v2/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 發送通知 (Admin only)
     */
    @PostMapping("/send")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('notification:create')")
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
}
