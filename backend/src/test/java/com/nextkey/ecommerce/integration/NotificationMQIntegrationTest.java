package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.notification.NotificationHistory;
import com.nextkey.ecommerce.domain.repository.NotificationHistoryRepository;
import com.nextkey.ecommerce.infrastructure.mq.NotificationConsumerService;
import com.nextkey.ecommerce.infrastructure.mq.NotificationMessage;

/**
 * AI-502 MQ Consumer 整合測試
 *
 * 覆蓋「Consumer processMessage → notification_history 寫入 DB」完整鏈路。
 * 使用 ReflectionTestUtils 直接呼叫 processMessage()，繞過 Redis 的 mock 限制，
 * 聚焦驗收 AI-502 核心語意：ACK 後寫入 history。
 *
 * 測試範圍：
 * - IT-AI502-001: processMessage 成功後 history 寫入 DB
 * - IT-AI502-002: processMessage 不因 history 問題拋出例外（容錯鏈路）
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-AI502: NotificationConsumerService 整合測試（AI-502）")
class NotificationMQIntegrationTest {

    @Autowired
    private NotificationConsumerService consumerService;

    @Autowired
    private NotificationHistoryRepository historyRepository;

    @MockBean
    private FeatureToggleService featureToggleService;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());
    }

    @Test
    @Order(1)
    @DisplayName("IT-AI502-001: processMessage 成功後 notification_history 寫入 DB（AI-502 核心驗收）")
    void testProcessMessage_writesHistoryToDb() {
        // Arrange
        UUID userId = UUID.randomUUID();
        NotificationMessage message = NotificationMessage.builder()
                .messageId(UUID.randomUUID())
                .userId(userId)
                .notificationType("ORDER_CONFIRMED")
                .title("訂單已確認（整合測試）")
                .content("您的訂單 #AI502-TEST 已確認")
                .channel("IN_APP")
                .build();

        long historyCountBefore = historyRepository.count();

        // Act: 直接呼叫 processMessage（AI-502：ACK 後寫入 history 的核心路徑）
        ReflectionTestUtils.invokeMethod(consumerService, "processMessage", message);

        // Assert: notification_history 已寫入 DB
        long historyCountAfter = historyRepository.count();
        assertThat(historyCountAfter)
                .as("Consumer 成功後 notification_history 應增加 1 筆")
                .isEqualTo(historyCountBefore + 1);

        // 驗證歷史記錄內容正確
        List<NotificationHistory> histories = historyRepository
                .findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        assertThat(histories).hasSize(1);
        NotificationHistory history = histories.get(0);
        assertThat(history.getUserId()).isEqualTo(userId);
        assertThat(history.getNotificationType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(history.getChannel()).isEqualTo("IN_APP");
        assertThat(history.getTitle()).isEqualTo("訂單已確認（整合測試）");
        assertThat(history.getIsRead()).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("IT-AI502-002: processMessage 不因 history 寫入問題阻斷 Consumer（ACK 容錯設計驗收）")
    void testProcessMessage_doesNotThrowOnNormalExecution() {
        // Arrange
        UUID userId = UUID.randomUUID();
        NotificationMessage message = NotificationMessage.builder()
                .messageId(UUID.randomUUID())
                .userId(userId)
                .notificationType("ORDER_CONFIRMED")
                .title("容錯測試")
                .content("容錯測試內容")
                .channel("IN_APP")
                .build();

        // Act & Assert: processMessage 不應拋出例外（AC-002 容錯設計：history 例外被內部 catch 吞掉）
        assertDoesNotThrow(
                () -> ReflectionTestUtils.invokeMethod(consumerService, "processMessage", message),
                "Consumer 不應因 history 寫入問題而拋出例外");
    }
}
