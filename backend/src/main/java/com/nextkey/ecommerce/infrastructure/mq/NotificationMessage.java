package com.nextkey.ecommerce.infrastructure.mq;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知訊息格式
 * 用於 MQ 非同步通知發送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private UUID messageId;
    /**
     * 送出前已預先建立的 {@code Notification} 列（單筆通知，見 {@code NotificationService.sendNotification}）的 id；
     * 廣播沒有預建列，為 null（Sprint 219，DEF-305）。消費者據此更新那一列，而不是再新增第二列。
     */
    private UUID notificationId;
    private UUID userId;
    private String notificationType;
    private String title;
    private String content;
    private Map<String, Object> data;
    private String channel;
    private String recipient;
    private Instant createdAt;
    private int retryCount;
    private String errorMessage;

    public static NotificationMessage create(
            final UUID userId,
            final String notificationType,
            final String title,
            final String content,
            final Map<String, Object> data,
            final String channel,
            final String recipient) {
        return NotificationMessage.builder()
                .messageId(UUID.randomUUID())
                .userId(userId)
                .notificationType(notificationType)
                .title(title)
                .content(content)
                .data(data)
                .channel(channel)
                .recipient(recipient)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void setError(String error) {
        this.errorMessage = error;
    }
}