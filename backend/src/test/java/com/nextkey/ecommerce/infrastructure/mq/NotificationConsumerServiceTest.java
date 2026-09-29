package com.nextkey.ecommerce.infrastructure.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
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
import org.springframework.data.redis.core.ValueOperations;

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

    // ========== TC-C003（Sprint 188 紅燈：重試佇列 key 碰撞導致訊息遺失） ==========

    @Test
    @DisplayName("TC-C003: 兩則不同訊息在同一重試層級（retryCount）失敗時，各自的重試佇列 key 不應互相覆蓋")
    void handleFailedMessage_twoDifferentMessagesAtSameRetryLevel_mustNotOverwriteEachOther()
            throws JsonProcessingException {
        // Arrange：用一個真正會保存 key/value 的假 Redis String store 取代單純的 mock 驗證，
        // 才能觀察到「後寫入的 key 覆蓋先前的 key」這種真實的資料遺失行為。
        Map<String, Object> fakeRedisStringStore = new HashMap<>();
        ValueOperations<String, Object> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().doAnswer(invocation -> {
            fakeRedisStringStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        UUID otherMessageId = UUID.randomUUID();
        NotificationMessage messageA = buildMessage();
        NotificationMessage messageB = NotificationMessage.builder()
                .messageId(otherMessageId)
                .userId(USER_ID)
                .notificationType("ORDER_CONFIRMED")
                .title("另一則通知")
                .content("內容 B")
                .channel("IN_APP")
                .build();

        String jsonA = "{\"messageId\":\"" + MESSAGE_ID + "\"}";
        String jsonB = "{\"messageId\":\"" + otherMessageId + "\"}";

        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPop(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(jsonA, jsonB);
        when(objectMapper.readValue(jsonA, NotificationMessage.class)).thenReturn(messageA);
        when(objectMapper.readValue(jsonB, NotificationMessage.class)).thenReturn(messageB);
        when(objectMapper.writeValueAsString(messageA)).thenReturn("SERIALIZED_A");
        when(objectMapper.writeValueAsString(messageB)).thenReturn("SERIALIZED_B");
        when(notificationRepository.save(any())).thenThrow(new RuntimeException("boom"));

        // Act：兩則「不同」訊息各自處理失敗一次，皆從 retryCount 0 -> 1（同一重試層級）
        consumerService.consumeNotifications();
        consumerService.consumeNotifications();

        // Assert：兩則訊息都必須能在重試佇列中各自找到，不可因 key 相同而互相覆蓋遺失
        assertThat(fakeRedisStringStore.values()).contains("SERIALIZED_A", "SERIALIZED_B");
    }

    // ========== TC-C004~006（Sprint 219，DEF-305：單筆通知更新預建列，不再新增第二列） ==========

    private NotificationMessage buildMessageWithNotificationId(final UUID notificationId) {
        NotificationMessage message = buildMessage();
        message.setNotificationId(notificationId);
        return message;
    }

    @Test
    @DisplayName("TC-C004: 訊息帶預建列 id → 更新那一列（isSent=true）而不是新增，歷史仍只寫一筆")
    void consumeNotifications_withNotificationId_updatesPrecreatedRow() throws JsonProcessingException {
        UUID notificationId = UUID.randomUUID();
        String json = "{\"messageId\":\"" + MESSAGE_ID + "\"}";
        stubRedisAndParser(json, buildMessageWithNotificationId(notificationId));
        Notification precreated = Notification.builder()
                .id(notificationId)
                .userId(USER_ID)
                .notificationType(Notification.NotificationType.ORDER_CONFIRMED)
                .title("訂單已確認")
                .channel(Notification.NotificationChannel.IN_APP)
                .isSent(false)
                .build();
        when(notificationRepository.findById(notificationId)).thenReturn(java.util.Optional.of(precreated));

        consumerService.consumeNotifications();

        assertThat(precreated.getIsSent()).isTrue();
        assertThat(precreated.getSentAt()).isNotNull();
        verify(notificationRepository, times(1)).save(precreated);
        verify(notificationHistoryService, times(1)).createHistory(
                eq(USER_ID), isNull(), eq("ORDER_CONFIRMED"), eq("IN_APP"), eq("訂單已確認"), eq("您的訂單已確認"));
    }

    @Test
    @DisplayName("TC-C005: 預建列還看不到（交易尚未提交）→ 不新增重複列、不寫歷史，走重試佇列")
    void consumeNotifications_precreatedRowNotVisible_retriesInsteadOfInserting() throws JsonProcessingException {
        UUID notificationId = UUID.randomUUID();
        String json = "{\"messageId\":\"" + MESSAGE_ID + "\"}";
        NotificationMessage message = buildMessageWithNotificationId(notificationId);
        stubRedisAndParser(json, message);
        when(notificationRepository.findById(notificationId)).thenReturn(java.util.Optional.empty());
        ValueOperations<String, Object> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(message)).thenReturn("SERIALIZED");

        consumerService.consumeNotifications();

        verify(notificationRepository, never()).save(any());
        verify(notificationHistoryService, never()).createHistory(any(), any(), any(), any(), any(), any());
        verify(valueOperations).set(eq("notification:retry:" + MESSAGE_ID), eq("SERIALIZED"), anyLong(),
                any(TimeUnit.class));
        assertThat(message.getRetryCount()).as("重試次數遞增").isEqualTo(1);
    }

    @Test
    @DisplayName("TC-C006: 沒有預建列 id（廣播）→ 照舊新增一列，不查詢既有列")
    void consumeNotifications_withoutNotificationId_insertsNewRow() throws JsonProcessingException {
        String json = "{\"messageId\":\"" + MESSAGE_ID + "\"}";
        stubRedisAndParser(json, buildMessage());
        when(notificationRepository.save(any())).thenReturn(buildSavedNotification());

        consumerService.consumeNotifications();

        verify(notificationRepository, times(1)).save(any());
        verify(notificationRepository, never()).findById(any());
    }
}
