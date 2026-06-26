package com.nextkey.ecommerce.infrastructure.mq;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.core.notification.NotificationHistoryService;
import com.nextkey.ecommerce.domain.model.notification.Notification;
import com.nextkey.ecommerce.domain.repository.NotificationRepository;

@DisplayName("NotificationConsumerService Tests (AI-502)")
@ExtendWith(MockitoExtension.class)
class NotificationConsumerServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private NotificationHistoryService notificationHistoryService;
    @Mock
    private ListOperations<String, Object> listOperations;

    @InjectMocks
    private NotificationConsumerService consumerService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MESSAGE_ID = UUID.randomUUID();

    private NotificationMessage buildMessage() {
        return NotificationMessage.builder()
                .messageId(MESSAGE_ID)
                .userId(USER_ID)
                .notificationType("ORDER_CONFIRMED")
                .title("訂單已確認")
                .content("您的訂單已確認")
                .channel("IN_APP")
                .build();
    }

    private Notification buildSavedNotification() {
        return Notification.builder()
                .userId(USER_ID)
                .notificationType(Notification.NotificationType.ORDER_CONFIRMED)
                .title("訂單已確認")
                .content("您的訂單已確認")
                .channel(Notification.NotificationChannel.IN_APP)
                .isSent(true)
                .retryCount(0)
                .build();
    }

    private void stubRedisAndParser(String json, NotificationMessage msg) throws JsonProcessingException {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPop(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(json);
        when(objectMapper.readValue(json, NotificationMessage.class)).thenReturn(msg);
    }

    // ========== TC-C001 ==========

    @Test
    @DisplayName("TC-C001: 成功處理通知後 historyService.createHistory 被呼叫一次（AI-502 核心驗收）")
    void consumeNotifications_success_writesHistoryOnce() throws JsonProcessingException {
        // Arrange
        String json = "{\"messageId\":\"" + MESSAGE_ID + "\"}";
        NotificationMessage message = buildMessage();

        stubRedisAndParser(json, message);
        when(notificationRepository.save(any())).thenReturn(buildSavedNotification());

        // Act
        consumerService.consumeNotifications();

        // Assert: history 寫入一次，且參數正確
        verify(notificationHistoryService, times(1)).createHistory(
                eq(USER_ID),
                isNull(),                   // tenantId — Consumer 端無 TenantContext，傳 null
                eq("ORDER_CONFIRMED"),
                eq("IN_APP"),
                eq("訂單已確認"),
                eq("您的訂單已確認"));
    }

    // ========== TC-C002 ==========

    @Test
    @DisplayName("TC-C002: historyService.createHistory 拋 RuntimeException 時，processMessage 仍正常完成（ACK 不中斷）")
    void consumeNotifications_historyThrows_doesNotPropagateException() throws JsonProcessingException {
        // Arrange
        String json = "{\"messageId\":\"" + MESSAGE_ID + "\"}";
        NotificationMessage message = buildMessage();

        stubRedisAndParser(json, message);
        when(notificationRepository.save(any())).thenReturn(buildSavedNotification());
        doThrow(new RuntimeException("DB write failure"))
                .when(notificationHistoryService)
                .createHistory(any(), any(), anyString(), anyString(), anyString(), anyString());

        // Act & Assert: consumeNotifications 不應拋出例外（ACK 正常完成）
        assertDoesNotThrow(() -> consumerService.consumeNotifications());

        // notification 仍然被 save（主流程不中斷）
        verify(notificationRepository, times(1)).save(any());
    }
}
