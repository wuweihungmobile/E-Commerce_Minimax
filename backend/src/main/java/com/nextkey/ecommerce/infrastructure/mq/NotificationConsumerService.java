package com.nextkey.ecommerce.infrastructure.mq;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.domain.model.notification.Notification;
import com.nextkey.ecommerce.domain.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知 MQ 消費者服務
 * 使用 Redis List (BRPOP) 消費通知訊息
 * Phase 2 實現：支援非同步訊息處理和重試機制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConsumerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_SECONDS = 30;

    /**
     * 定時從佇列消費通知訊息
     * 使用 BRPOP 模式從 Redis List 取訊息
     */
    @Scheduled(fixedDelay = 1000)
    public void consumeNotifications() {
        try {
            // 嘗試從佇列取得訊息（非阻塞，1秒超時）
            Object messageObj = redisTemplate.opsForList().rightPop(
                    RedisStreamConfig.NOTIFICATION_STREAM, 1, TimeUnit.SECONDS);

            if (messageObj == null) {
                return;
            }

            NotificationMessage message = parseMessage(messageObj);
            if (message == null) {
                log.warn("Failed to parse message: {}", messageObj);
                return;
            }

            processMessage(message);

        } catch (RuntimeException e) {
            log.warn("Error consuming notifications: {}", e.getMessage());
        }
    }

    /**
     * 解析訊息
     */
    private NotificationMessage parseMessage(Object messageObj) {
        try {
            if (messageObj instanceof String messageStr) {
                return objectMapper.readValue(messageStr, NotificationMessage.class);
            } else if (messageObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) messageObj;
                Object data = map.get("data");
                if (data instanceof String dataStr) {
                    return objectMapper.readValue(dataStr, NotificationMessage.class);
                }
                return objectMapper.convertValue(data, NotificationMessage.class);
            }
            return objectMapper.convertValue(messageObj, NotificationMessage.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to parse notification message: {}", messageObj, e);
            return null;
        }
    }

    /**
     * 處理訊息
     */
    private void processMessage(NotificationMessage message) {
        try {
            log.info("Processing notification: messageId={}, userId={}, type={}",
                    message.getMessageId(), message.getUserId(), message.getNotificationType());

            // 創建通知記錄
            Notification notification = Notification.builder()
                    .userId(message.getUserId())
                    .notificationType(Notification.NotificationType.valueOf(message.getNotificationType()))
                    .title(message.getTitle())
                    .content(message.getContent())
                    .data(message.getData())
                    .channel(Notification.NotificationChannel.valueOf(message.getChannel()))
                    .recipient(message.getRecipient())
                    .isSent(true)
                    .sentAt(message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now())
                    .retryCount(message.getRetryCount())
                    .errorMessage(message.getErrorMessage())
                    .build();

            notificationRepository.save(notification);

            log.info("Notification processed successfully: messageId={}", message.getMessageId());

        } catch (RuntimeException e) {
            log.error("Failed to process notification message: {}", message.getMessageId(), e);
            handleFailedMessage(message);
        }
    }

    /**
     * 處理失敗的訊息（重新排隊或移入 DLQ）
     */
    private void handleFailedMessage(NotificationMessage message) {
        message.incrementRetryCount();

        if (message.getRetryCount() < MAX_RETRY_COUNT) {
            log.warn("Requeueing notification: messageId={}, retryCount={}",
                    message.getMessageId(), message.getRetryCount());

            // 延遲重新排隊（使用 sorted set 实现延迟）
            try {
                String retryKey = "notification:retry:" + message.getRetryCount();
                redisTemplate.opsForValue().set(
                        retryKey,
                        objectMapper.writeValueAsString(message),
                        RETRY_DELAY_SECONDS * message.getRetryCount(),
                        TimeUnit.SECONDS
                );
            } catch (JsonProcessingException | DataAccessException e) {
                log.error("Failed to requeue notification: messageId={}", message.getMessageId(), e);
            }
        } else {
            log.error("Moving to DLQ after {} retries: messageId={}",
                    MAX_RETRY_COUNT, message.getMessageId());
            moveToDeadLetterQueue(message);
        }
    }

    /**
     * 移入 Dead Letter Queue
     */
    private void moveToDeadLetterQueue(NotificationMessage message) {
        try {
            String dlqKey = "notification:dlq:" + message.getMessageId();
            redisTemplate.opsForValue().set(dlqKey, objectMapper.writeValueAsString(message));
            log.info("Message moved to DLQ: messageId={}", message.getMessageId());
        } catch (JsonProcessingException | DataAccessException e) {
            log.error("Failed to move message to DLQ: messageId={}", message.getMessageId(), e);
        }
    }

    /**
     * 處理重試佇列中的訊息
     */
    @Scheduled(fixedDelay = 5000)
    public void processRetryQueue() {
        try {
            // 檢查各重試等級的佇列
            for (int retryCount = 1; retryCount <= MAX_RETRY_COUNT; retryCount++) {
                String retryKey = "notification:retry:" + retryCount;
                Object messageObj = redisTemplate.opsForValue().get(retryKey);

                if (messageObj != null) {
                    NotificationMessage message = parseMessage(messageObj);
                    if (message != null && message.getRetryCount() >= retryCount) {
                        redisTemplate.delete(retryKey);
                        processMessage(message);
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Error processing retry queue: {}", e.getMessage());
        }
    }
}