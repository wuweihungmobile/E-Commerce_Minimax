package com.nextkey.ecommerce.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;

/**
 * 通知 DTO
 */
public class NotificationDto {

    // ========== Send Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendRequest {
        @NotNull(message = "User ID is required")
        private UUID userId;

        @NotNull(message = "Notification type is required")
        private NotificationType notificationType;

        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Content is required")
        private String content;

        private Map<String, Object> data;

        private Channel channel;

        private String recipient;
    }

    public enum NotificationType {
        ORDER_CONFIRMED,
        ORDER_PAID,
        ORDER_SHIPPED,
        ORDER_DELIVERED,
        ORDER_COMPLETED,
        ORDER_CANCELLED,
        BOOKING_CONFIRMED,
        BOOKING_REMINDER,
        PAYMENT_SUCCESS,
        PAYMENT_FAILED,
        REVIEW_REQUEST,
        NEW_MESSAGE,
        SYSTEM_ANNOUNCEMENT
    }

    public enum Channel {
        IN_APP,
        EMAIL,
        SMS,
        PUSH
    }

    // ========== Broadcast Request ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BroadcastRequest {
        @NotNull(message = "Notification type is required")
        private NotificationType notificationType;

        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Content is required")
        private String content;

        private Map<String, Object> data;

        private Channel channel;

        private UUID tenantId; // null for platform-wide broadcast
    }

    // ========== Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationResponse {
        private UUID notificationId;
        private UUID userId;
        private String notificationType;
        private String title;
        private String content;
        private Map<String, Object> data;
        private String channel;
        private String recipient;
        private Boolean isSent;
        private Boolean isRead;
        private Instant sentAt;
        private Instant readAt;
        private Integer retryCount;
        private String errorMessage;
        private Instant createdAt;
    }

    // ========== List Response ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationListResponse {
        private List<NotificationResponse> notifications;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private int unreadCount;
    }

    // ========== Unread Count ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnreadCountResponse {
        private UUID userId;
        private int unreadCount;
    }

    // ========== Mark Read ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarkReadRequest {
        private List<UUID> notificationIds; // null means mark all as read
    }

    // ========== Email Template ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmailTemplate {
        private String templateId;
        private String subject;
        private String body;
        private Map<String, String> placeholders;
    }

    // ========== SMS Template ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SmsTemplate {
        private String templateId;
        private String content;
        private Map<String, String> placeholders;
    }
}
