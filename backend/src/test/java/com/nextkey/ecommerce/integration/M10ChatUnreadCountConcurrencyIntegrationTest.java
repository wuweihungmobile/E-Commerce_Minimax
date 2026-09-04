package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.api.dto.ChatDto;
import com.nextkey.ecommerce.core.chat.ChatService;
import com.nextkey.ecommerce.domain.model.chat.Conversation;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ConversationRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * 對話未讀計數的併發正確性整合測試（Sprint 125，DEF-056；真實 PostgreSQL）。
 *
 * <p>為什麼一定要真實 DB：修復前 {@code ChatService.sendMessage} 是「讀出
 * {@code Conversation} → 未讀計數 +1 → {@code save()}」的兩段式操作，任何把
 * {@code ConversationRepository} mock 掉的測試都只是單執行緒依序回放 stub，讀後寫窗口
 * 根本不存在（承 {@code M11PromoConcurrencyIntegrationTest} 同一理由）。本測試以多執行緒
 * 同時對同一個對話送出訊息，直接壓在同一列 {@code conversations} 上，由資料庫決定結果；
 * 修復後的 {@code recordNewMessage} 原子 UPDATE 應使未讀計數精準等於併發訊息數。
 *
 * <p>{@code ChatService.sendMessage} 本身已是 {@code @Transactional}，每條執行緒呼叫該
 * proxy 方法時各自開啟獨立交易（ThreadLocal 交易同步），不需要像
 * {@code M11PromoConcurrencyIntegrationTest} 額外包一層 {@code TransactionTemplate}
 * ——那是因為 {@code PromoService.tryConsumeUsageQuota} 本身不帶 {@code @Transactional}。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-M10-RACE: 對話未讀計數併發正確性（Sprint 125 / DEF-056）")
class M10ChatUnreadCountConcurrencyIntegrationTest {

    @Autowired private ChatService chatService;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private UserRepository userRepository;

    @MockBean private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    /** 併發執行緒數（=同時送出的訊息數）。 */
    private static final int THREADS = 20;
    /** System Tenant ID（V9），與 conversations.tenant_id 的 FK 對齊，不需另建 Tenant。 */
    private static final UUID SYSTEM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private UUID initiatorId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());

        long stamp = System.nanoTime();
        initiatorId = userRepository.save(User.builder()
                .email("chat-race-initiator-" + stamp + "@test.com")
                .build()).getId();
        UUID recipientId = userRepository.save(User.builder()
                .email("chat-race-recipient-" + stamp + "@test.com")
                .build()).getId();

        Conversation conversation = Conversation.builder()
                .tenantId(SYSTEM_TENANT_ID)
                .conversationType(Conversation.ConversationType.DIRECT)
                .initiatorId(initiatorId)
                .recipientId(recipientId)
                .initiatorUnreadCount(0)
                .recipientUnreadCount(0)
                .isActive(true)
                .build();
        conversationId = conversationRepository.save(conversation).getId();
    }

    @Test
    @DisplayName("N 條執行緒同時由 initiator 發訊息 → recipient 未讀計數精準等於 N（不遺漏併發訊息）")
    void concurrentMessagesFromInitiator_recipientUnreadCountExactlyMatchesThreadCount() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Void>> results = new ArrayList<>(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                final int idx = i;
                Callable<Void> attempt = () -> {
                    startGun.await();
                    ChatDto.SendMessageRequest request = ChatDto.SendMessageRequest.builder()
                            .conversationId(conversationId)
                            .content("race message " + idx)
                            .build();
                    chatService.sendMessage(initiatorId, request);
                    return null;
                };
                results.add(pool.submit(attempt));
            }
            startGun.countDown();
            for (Future<Void> f : results) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }

        Conversation reloaded = conversationRepository.findById(conversationId).orElseThrow();
        // 修復前：THREADS 條併發訊息的「讀 → +1 → save」互相覆蓋，最終計數遠小於 THREADS。
        assertThat(reloaded.getRecipientUnreadCount())
                .as("recipient 未讀計數必須精準等於併發訊息數，遺漏任何一筆都是未讀徽章偏低")
                .isEqualTo(THREADS);
        assertThat(reloaded.getInitiatorUnreadCount()).isZero();
    }
}
