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