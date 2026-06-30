package com.nextkey.ecommerce.infrastructure.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.core.notification.NotificationHistoryService;
import com.nextkey.ecommerce.domain.model.notification.Notification;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.NotificationRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * DEF-013：通知 MQ 端到端傳遞測試（觸發 → 佇列 → 消費 → 可觀測結果）。
 *
 * <p>背景（REALTIME_ASYNC_E2E_DOD）：M09 通知為 backend-only 非同步副作用，先前僅有
 * {@code NotificationConsumerServiceTest}（mock listOps）與 {@code NotificationMQIntegrationTest}
 * （ReflectionTestUtils 直呼 processMessage）——兩者都<b>繞過 Producer→佇列→Consumer 的真實傳遞</b>，
 * 因此沒抓到 Producer 用 {@code opsForStream().add()}、Consumer 用 {@code opsForList().rightPop()}
 * 讀同一 key 的型別不相容（訊息永不被消費）。
 *
 * <p>本測試以「單一條共用 list」同時供 Producer 推入與 Consumer 取出，並使用<b>真實 ObjectMapper</b>
 * 走完整序列化/反序列化，端到端驗證：觸發通知 → 訊息經佇列 → 消費 → Notification 落地 + 歷史寫入，
 * 內容跨序列化保持正確。Producer 若回退成 Stream（不同結構），共用 list 將為空、消費為 no-op，本測試即失敗。
 */
@DisplayName("DEF-013: 通知 MQ 端到端傳遞（produce → 佇列 → consume → 可觀測結果）")
@ExtendWith(MockitoExtension.class)
class NotificationProduceConsumeTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ListOperations<String, Object> listOps;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationHistoryService notificationHistoryService;

    /** 真實 ObjectMapper（含 JavaTimeModule），確保 Instant 等欄位真正走序列化往返。 */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 模擬單一條 Redis List：Producer leftPush 入、Consumer rightPop 出（FIFO）。 */
    private final Deque<Object> queue = new ArrayDeque<>();

    private NotificationProducerService producer;
    private NotificationConsumerService consumer;

    @BeforeEach
    void setUp() {
        // 同一條 list 同時供 Producer 與 Consumer —— 這正是端到端傳遞的關鍵（兩端結構必須一致）。
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        lenient().when(listOps.leftPush(eq(RedisStreamConfig.NOTIFICATION_STREAM), any()))
                .thenAnswer(inv -> {
                    queue.addFirst(inv.getArgument(1));
                    return (long) queue.size();
                });
        lenient().when(listOps.rightPop(eq(RedisStreamConfig.NOTIFICATION_STREAM), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> queue.pollLast());

        producer = new NotificationProducerService(redisTemplate, userRepository, objectMapper);
        consumer = new NotificationConsumerService(
                redisTemplate, notificationRepository, objectMapper, notificationHistoryService);
    }

    @Test
    @DisplayName("AT-DEF013-001: 觸發通知 → 經佇列 → 消費 → Notification 落地 + 歷史寫入（跨序列化內容正確）")
    void produceThenConsume_deliversEndToEnd() {
        UUID userId = UUID.randomUUID();
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto.SendRequest request = NotificationDto.SendRequest.builder()
                .userId(userId)
                .notificationType(NotificationDto.NotificationType.ORDER_CONFIRMED)
                .title("訂單已確認")
                .content("您的訂單 #E2E 已確認")
                .data(Map.of("orderId", "ORD-E2E"))
                .channel(NotificationDto.Channel.IN_APP)
                .build();

        // Act 1：觸發（Producer → 佇列）
        producer.sendToQueue(request);
        assertThat(queue)
                .as("Producer 應把訊息推入 Consumer 會讀的同一條 list")
                .hasSize(1);

        // Act 2：消費（Consumer ← 佇列）
        consumer.consumeNotifications();

        // Assert：可觀測結果 —— Notification 落地，內容跨序列化保持正確
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        Notification n = saved.getValue();
        assertThat(n.getUserId()).isEqualTo(userId);
        assertThat(n.getNotificationType()).isEqualTo(Notification.NotificationType.ORDER_CONFIRMED);
        assertThat(n.getChannel()).isEqualTo(Notification.NotificationChannel.IN_APP);
        assertThat(n.getTitle()).isEqualTo("訂單已確認");
        assertThat(n.getContent()).isEqualTo("您的訂單 #E2E 已確認");
        assertThat(n.getData()).containsEntry("orderId", "ORD-E2E");
        assertThat(n.getIsSent()).isTrue();

        // Assert：歷史寫入（ACK 後可觀測副作用）
        verify(notificationHistoryService).createHistory(
                eq(userId), isNull(), eq("ORDER_CONFIRMED"), eq("IN_APP"),
                eq("訂單已確認"), eq("您的訂單 #E2E 已確認"));

        // 消費後佇列應清空（訊息確實被取走，非殘留）
        assertThat(queue).as("消費後佇列應清空").isEmpty();
    }

    @Test
    @DisplayName("AT-DEF013-002: 佇列為空時消費為 no-op（不誤存通知）")
    void consumeEmptyQueue_isNoop() {
        consumer.consumeNotifications();

        verify(notificationRepository, never()).save(any());
    }
}
