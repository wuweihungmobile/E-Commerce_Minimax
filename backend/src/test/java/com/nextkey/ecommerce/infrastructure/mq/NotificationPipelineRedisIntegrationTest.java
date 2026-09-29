package com.nextkey.ecommerce.infrastructure.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.api.dto.NotificationDto;
import com.nextkey.ecommerce.core.notification.NotificationHistoryService;
import com.nextkey.ecommerce.domain.model.notification.Notification;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.NotificationRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.redis.RedisConfig;

/**
 * 通知管線在真實 Redis 上端到端走一遍（Sprint 219，DEF-305）。
 *
 * <p>通知消費者從來沒有真的執行過（全專案沒有啟用排程），既有的 {@code NotificationProduceConsumeTest} 用一條
 * 記憶體佇列取代 Redis。這裡用真實的 Redis 與生產相同的序列化器（{@code RedisConfig}），驗證：訊息經過真實的
 * LPUSH／BRPOP 與 JSON 序列化往返後，消費者更新的是「送出前預先建立的那一列」而不是再新增一列；廣播沒有預建列
 * 時仍新增；預建列尚未提交時消費者不重複新增、走重試。只 mock 資料庫。
 * 需求：執行前須先 {@code make test-db-up}（不啟動 Spring context：{@code IntegrationTestConfiguration} 會把 Redis 換成 mock）。
 */
@DisplayName("IT-NOTIF-PIPELINE: 通知管線在真實 Redis 上端到端（單筆更新預建列、廣播新增、預建列未提交時重試）")
class NotificationPipelineRedisIntegrationTest {

    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private UserRepository userRepository;
    private NotificationRepository notificationRepository;
    private NotificationHistoryService historyService;
    private NotificationProducerService producer;
    private NotificationConsumerService consumer;
    private UUID userId;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration("localhost", 6379);
        redisConfig.setPassword(RedisPassword.of("redis-dev-password"));
        connectionFactory = new LettuceConnectionFactory(redisConfig);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new RedisConfig().redisTemplate(connectionFactory);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        clearQueues();
        userId = UUID.randomUUID();
        userRepository = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        notificationRepository = mock(NotificationRepository.class);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        historyService = mock(NotificationHistoryService.class);
        producer = new NotificationProducerService(redisTemplate, userRepository, objectMapper);
        consumer = new NotificationConsumerService(redisTemplate, notificationRepository, objectMapper, historyService);
    }

    @AfterEach
    void tearDown() {
        clearQueues();
    }

    private static void clearQueues() {
        redisTemplate.delete(RedisStreamConfig.NOTIFICATION_STREAM);
        Set<String> retryKeys = redisTemplate.keys("notification:retry:*");
        if (retryKeys != null && !retryKeys.isEmpty()) {
            redisTemplate.delete(retryKeys);
        }
    }

    private NotificationDto.SendRequest request() {
        return NotificationDto.SendRequest.builder()
                .userId(userId)
                .notificationType(NotificationDto.NotificationType.ORDER_CANCELLED)
                .title("訂單已取消")
                .content("您的訂單因逾時未付款已取消")
                .data(Map.of("orderId", "ORD-1"))
                .channel(NotificationDto.Channel.IN_APP)
                .build();
    }

    private Notification precreated(final UUID id) {
        return Notification.builder()
                .id(id)
                .userId(userId)
                .notificationType(Notification.NotificationType.ORDER_CANCELLED)
                .title("訂單已取消")
                .content("您的訂單因逾時未付款已取消")
                .channel(Notification.NotificationChannel.IN_APP)
                .isSent(false)
                .retryCount(0)
                .build();
    }

    @Test
    @DisplayName("單筆通知：消費者更新送出前預建的那一列（isSent=true），不再新增第二列，歷史只寫一筆")
    void singleNotification_updatesPrecreatedRowInsteadOfInsertingAnother() {
        UUID notificationId = UUID.randomUUID();
        Notification existing = precreated(notificationId);
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(existing));

        producer.sendToQueue(request(), notificationId);
        assertThat(redisTemplate.opsForList().size(RedisStreamConfig.NOTIFICATION_STREAM))
                .as("訊息確實進了 Redis").isEqualTo(1L);

        consumer.consumeNotifications();

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue()).as("更新的是預建的那一個實體，不是新建的").isSameAs(existing);
        assertThat(existing.getIsSent()).isTrue();
        assertThat(existing.getSentAt()).isNotNull();
        verify(historyService, times(1)).createHistory(eq(userId), isNull(), eq("ORDER_CANCELLED"), eq("IN_APP"),
                eq("訂單已取消"), eq("您的訂單因逾時未付款已取消"));
        assertThat(redisTemplate.opsForList().size(RedisStreamConfig.NOTIFICATION_STREAM)).isZero();
    }

    @Test
    @DisplayName("廣播沒有預建列：消費者照舊新增一列（isSent=true），內容跨 Redis 序列化往返仍正確")
    void broadcast_insertsNewRow() {
        producer.sendToQueue(request());

        consumer.consumeNotifications();

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(saved.capture());
        Notification inserted = saved.getValue();
        assertThat(inserted.getUserId()).isEqualTo(userId);
        assertThat(inserted.getTitle()).isEqualTo("訂單已取消");
        assertThat(inserted.getData()).containsEntry("orderId", "ORD-1");
        assertThat(inserted.getIsSent()).isTrue();
        verify(notificationRepository, never()).findById(any());
    }

    @Test
    @DisplayName("預建列的交易還沒提交就被消費：不新增重複列，改走重試；列出現後重試只更新一次")
    void precreatedRowNotVisibleYet_isRetriedThenUpdatedOnce() {
        UUID notificationId = UUID.randomUUID();
        Notification existing = precreated(notificationId);
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        producer.sendToQueue(request(), notificationId);
        consumer.consumeNotifications();

        verify(notificationRepository, never()).save(any(Notification.class));
        Set<String> retryKeys = redisTemplate.keys("notification:retry:*");
        assertThat(retryKeys).as("訊息進了重試佇列而不是被丟掉").hasSize(1);

        consumer.processRetryQueue();

        verify(notificationRepository, times(1)).save(existing);
        assertThat(existing.getIsSent()).isTrue();
        assertThat(redisTemplate.keys("notification:retry:*")).isEmpty();
        verify(historyService, times(1)).createHistory(any(), any(), any(), any(), any(), any());
    }
}
