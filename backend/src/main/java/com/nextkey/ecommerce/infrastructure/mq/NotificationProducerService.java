package com.nextkey.ecommerce.infrastructure.mq;

import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.domain.model.notification.Notification;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知 MQ 生產者服務
 * 使用 Redis Stream 發送非同步通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * 發送通知到 MQ（廣播用：沒有預先建立的 Notification 列，消費者會新增一列）
     */
    public void sendToQueue(NotificationDto.SendRequest request) {
        sendToQueue(request, null);
    }

    /**
     * 發送通知到 MQ。{@code notificationId} 是呼叫端在送出前已預先建立的 Notification 列（單筆通知，見
     * {@code NotificationService.sendNotification}）；帶入後消費者更新那一列而不是再新增一列（Sprint 219，DEF-305：
     * 排程啟用前消費者從未執行，「預建一列＋消費者再建一列」的重複沒有人看過）。
     */
    public void sendToQueue(NotificationDto.SendRequest request, UUID notificationId) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_1006, "User not found"));

        Notification.NotificationChannel channel = request.getChannel() != null
                ? Notification.NotificationChannel.valueOf(request.getChannel().name())
                : Notification.NotificationChannel.IN_APP;

        String recipient = request.getRecipient();
        if (recipient == null && channel != Notification.NotificationChannel.IN_APP) {
            recipient = getRecipientForChannel(user, channel);
        }

        NotificationMessage message = NotificationMessage.create(
                user.getId(),
                request.getNotificationType().name(),
                request.getTitle(),
                request.getContent(),
                request.getData(),
                channel.name(),
                recipient
        );
        message.setNotificationId(notificationId);

        sendMessage(message);
        log.info("Notification sent to queue: messageId={}, userId={}, type={}",
                message.getMessageId(), user.getId(), request.getNotificationType());
    }

    /**
     * 發送訊息到 Redis Stream
     */
    private void sendMessage(NotificationMessage message) {
        try {
            // 推入 Redis List（與 NotificationConsumerService.consumeNotifications 的 rightPop 對齊，
            // leftPush + rightPop 構成 FIFO 佇列）。
            // 🔴 DEF-013：原先誤用 opsForStream().add()（Redis Stream），但 consumer 以 opsForList().rightPop()
            // 讀同一 key（notification:stream）→ 型別不相容（WRONGTYPE）被吞，訊息永不被消費。
            // 改為兩端一致使用 List，端到端傳遞才成立。
            redisTemplate.opsForList().leftPush(
                    RedisStreamConfig.NOTIFICATION_STREAM,
                    objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification message: {}", message.getMessageId(), e);
            throw new BusinessException(ErrorCode.E_9000, "Failed to send notification to queue");
        }
    }

    /**
     * 發送多個通知到佇列
     */
    public int broadcastToQueue(NotificationDto.BroadcastRequest request) {
        var users = request.getTenantId() != null
                ? userRepository.findByTenantId(request.getTenantId())
                : userRepository.findAll();

        int count = 0;
        for (User user : users) {
            try {
                NotificationDto.SendRequest sendRequest = NotificationDto.SendRequest.builder()
                        .userId(user.getId())
                        .notificationType(request.getNotificationType())
                        .title(request.getTitle())
                        .content(request.getContent())
                        .data(request.getData())
                        .channel(request.getChannel())
                        .build();
                sendToQueue(sendRequest);
                count++;
            } catch (RuntimeException e) {
                log.warn("Failed to queue notification for user: {}", user.getId(), e);
            }
        }

        log.info("Broadcast queued: type={}, count={}", request.getNotificationType(), count);
        return count;
    }

    private String getRecipientForChannel(final User user, final Notification.NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> user.getEmail();
            case SMS -> user.getPhone();
            default -> null;
        };
    }
}