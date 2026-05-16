package com.nextkey.ecommerce.infrastructure.mq;

import java.util.HashMap;
import java.util.Map;

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
     * 發送通知到 MQ
     */
    public void sendToQueue(NotificationDto.SendRequest request) {
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

        sendMessage(message);
        log.info("Notification sent to queue: messageId={}, userId={}, type={}",
                message.getMessageId(), user.getId(), request.getNotificationType());
    }

    /**
     * 發送訊息到 Redis Stream
     */
    private void sendMessage(NotificationMessage message) {
        try {
            Map<String, String> messageMap = new HashMap<>();
            messageMap.put("data", objectMapper.writeValueAsString(message));

            redisTemplate.opsForStream().add(RedisStreamConfig.NOTIFICATION_STREAM, messageMap);
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
            } catch (Exception e) {
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